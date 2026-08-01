package com.dungz.widgetsdk

import android.app.Activity
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.glance.action.Action
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.updateAll
import com.dungz.widgetsdk.glance.RetentionGlanceReceiver
import com.dungz.widgetsdk.glance.RetentionGlanceWidget
import com.dungz.widgetsdk.internal.ClickIntents
import com.dungz.widgetsdk.internal.PinResultReceiver
import com.dungz.widgetsdk.internal.WidgetStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Entry point of the retention widget SDK.
 *
 * Ships two home screen widgets sharing the same content and callbacks:
 * - [WidgetType.XML] — RemoteViews rendered from an XML layout
 * - [WidgetType.COMPOSE] — Glance widget written in Compose style
 *
 * Both render an SDK default UI out of the box; the host app can inject its own UI with
 * [setXmlRenderer] and/or [setComposeContent].
 *
 * Typical integration:
 * ```
 * // Application.onCreate()
 * RetentionWidgetSdk.init(this, clickActivity = MainActivity::class.java)
 * RetentionWidgetSdk.setCallback(object : RetentionWidgetCallback { ... })
 * // Optional — custom UI from the app:
 * RetentionWidgetSdk.setXmlRenderer { ctx, id, content -> RemoteViews(...) }
 * RetentionWidgetSdk.setComposeContent { content -> MyGlanceUi(content) }
 *
 * // Anywhere in the app
 * RetentionWidgetSdk.updateWidget(context, WidgetContent(title = "...", streakCount = 7))
 * RetentionWidgetSdk.requestPinWidget(context, WidgetType.COMPOSE)
 *
 * // In the activity opened by a widget tap (onCreate + onNewIntent)
 * RetentionWidgetSdk.consumeClickEvent(intent)?.let { event -> ... }
 * ```
 */
object RetentionWidgetSdk {

    private var callback: RetentionWidgetCallback? = null
    internal var xmlRenderer: WidgetXmlRenderer? = null
        private set
    internal var composeContent: (@Composable (WidgetContent) -> Unit)? = null
        private set

    private val sdkScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Initializes the SDK. Call once from `Application.onCreate()`.
     *
     * @param clickActivity the activity opened when the user taps a widget. Pass null
     *   to fall back to the app's launcher activity.
     * @param defaultContent initial content used before [updateWidget] is ever called.
     *   Only applied on first launch so it never overwrites content set later.
     */
    @JvmStatic
    @JvmOverloads
    fun init(
        context: Context,
        clickActivity: Class<out Activity>? = null,
        defaultContent: WidgetContent? = null,
    ) {
        val appContext = context.applicationContext
        WidgetStorage.saveClickComponent(
            appContext,
            clickActivity?.let { ComponentName(appContext, it) },
        )
        if (defaultContent != null && !WidgetStorage.hasContent(appContext)) {
            WidgetStorage.saveContent(appContext, defaultContent)
        }
        refreshAllWidgets(appContext)
    }

    /**
     * Registers a global listener for pin/add/remove/click events. Register it in
     * `Application.onCreate()` so it survives process restarts. Pass null to clear.
     */
    @JvmStatic
    fun setCallback(callback: RetentionWidgetCallback?) {
        this.callback = callback
    }

    /**
     * Injects the host app's own UI for the XML widget. Pass null to restore the SDK
     * default. Register in `Application.onCreate()` (the launcher may re-render the
     * widget after process death); call [updateWidget] afterwards to re-render
     * immediately.
     */
    @JvmStatic
    fun setXmlRenderer(renderer: WidgetXmlRenderer?) {
        xmlRenderer = renderer
    }

    /**
     * Injects the host app's own Glance UI for the Compose widget. The lambda receives
     * the current [WidgetContent] and must emit Glance composables
     * (`androidx.glance.*`). Wire tap targets with [createClickAction] so click
     * callbacks keep working. Pass null to restore the SDK default. Register in
     * `Application.onCreate()`; call [updateWidget] afterwards to re-render immediately.
     */
    fun setComposeContent(content: (@Composable (WidgetContent) -> Unit)?) {
        composeContent = content
    }

    /** Persists [content] and re-renders every widget instance of both types. */
    @JvmStatic
    fun updateWidget(context: Context, content: WidgetContent) {
        val appContext = context.applicationContext
        WidgetStorage.saveContent(appContext, content)
        refreshAllWidgets(appContext)
    }

    /** The content currently rendered by the widgets. */
    @JvmStatic
    fun getWidgetContent(context: Context): WidgetContent =
        WidgetStorage.loadContent(context.applicationContext)

    /**
     * Whether the current launcher supports programmatic widget pinning
     * (the "permission dialog" flow of [requestPinWidget]).
     */
    @JvmStatic
    fun isPinWidgetSupported(context: Context): Boolean =
        AppWidgetManager.getInstance(context.applicationContext).isRequestPinAppWidgetSupported

    /**
     * Asks the system to show the pin-widget confirmation dialog on the home screen for
     * the given widget [type].
     *
     * Android has no runtime permission for widgets — this dialog *is* the user consent
     * step. If the user accepts, [RetentionWidgetCallback.onPinAccepted] and
     * [RetentionWidgetCallback.onWidgetAdded] fire; if the user dismisses the dialog the
     * system provides no signal (poll [getActiveWidgetCount] if you need to know).
     *
     * The SDK allows a single widget on the home screen: if an instance of either type
     * is already added, no dialog is shown and [PinWidgetResult.ALREADY_ADDED] is
     * returned. (Adding manually from the launcher's widget picker bypasses this check —
     * Android offers no way to block that.)
     */
    @JvmStatic
    @JvmOverloads
    fun requestPinWidget(context: Context, type: WidgetType = WidgetType.XML): PinWidgetResult {
        val appContext = context.applicationContext
        val manager = AppWidgetManager.getInstance(appContext)
        if (!manager.isRequestPinAppWidgetSupported) return PinWidgetResult.UNSUPPORTED
        if (hasWidget(appContext)) return PinWidgetResult.ALREADY_ADDED

        val successIntent = Intent(appContext, PinResultReceiver::class.java)
            .setAction(PinResultReceiver.ACTION_PIN_ACCEPTED)
            .putExtra(ClickIntents.EXTRA_WIDGET_TYPE, type.name)
        val successCallback = PendingIntent.getBroadcast(
            appContext,
            type.ordinal,
            successIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val shown = manager.requestPinAppWidget(
            providerComponent(appContext, type), null, successCallback,
        )
        return if (shown) PinWidgetResult.REQUESTED else PinWidgetResult.UNSUPPORTED
    }

    /**
     * Number of widget instances currently on the home screen — of [type], or of both
     * types when [type] is null.
     */
    @JvmStatic
    @JvmOverloads
    fun getActiveWidgetCount(context: Context, type: WidgetType? = null): Int {
        val appContext = context.applicationContext
        return when (type) {
            null -> WidgetType.entries.sumOf { widgetIds(appContext, it).size }
            else -> widgetIds(appContext, type).size
        }
    }

    /** Whether at least one widget instance (of any type) is on the home screen. */
    @JvmStatic
    fun hasWidget(context: Context): Boolean = getActiveWidgetCount(context) > 0

    /**
     * PendingIntent for tap targets inside a custom [WidgetXmlRenderer]. Opens the
     * configured click activity and makes the tap observable through
     * [consumeClickEvent] and [RetentionWidgetCallback.onWidgetClicked].
     */
    @JvmStatic
    fun createClickPendingIntent(
        context: Context,
        widgetId: Int,
        source: WidgetClickSource,
        deepLink: String?,
    ): PendingIntent? =
        ClickIntents.pendingIntent(context, widgetId, source, deepLink, WidgetType.XML)

    /**
     * Glance [Action] for tap targets inside custom [setComposeContent] UI — the
     * Compose-widget counterpart of [createClickPendingIntent].
     */
    fun createClickAction(
        context: Context,
        source: WidgetClickSource,
        deepLink: String?,
    ): Action? =
        ClickIntents.buildIntent(
            context, AppWidgetManager.INVALID_APPWIDGET_ID, source, deepLink, WidgetType.COMPOSE,
        )?.let { actionStartActivity(it) }

    /**
     * Extracts a [WidgetClickEvent] from an activity intent, or returns null if the
     * intent did not originate from a widget tap.
     *
     * Call it from both `onCreate` and `onNewIntent` of the activity configured in
     * [init]. The event is consumed: calling again with the same intent returns null,
     * so configuration changes don't replay the click. Also forwards the event to the
     * registered [RetentionWidgetCallback].
     */
    @JvmStatic
    fun consumeClickEvent(intent: Intent?): WidgetClickEvent? {
        if (intent == null) return null
        if (intent.action != ClickIntents.ACTION_WIDGET_CLICK) return null
        if (!intent.hasExtra(ClickIntents.EXTRA_CLICK_SOURCE)) return null

        val sourceName = intent.getStringExtra(ClickIntents.EXTRA_CLICK_SOURCE)
        val source = WidgetClickSource.entries.firstOrNull { it.name == sourceName }
            ?: return null
        val typeName = intent.getStringExtra(ClickIntents.EXTRA_WIDGET_TYPE)
        val event = WidgetClickEvent(
            source = source,
            widgetType = WidgetType.entries.firstOrNull { it.name == typeName } ?: WidgetType.XML,
            deepLink = intent.getStringExtra(ClickIntents.EXTRA_DEEP_LINK),
            widgetId = intent.getIntExtra(
                ClickIntents.EXTRA_WIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID,
            ),
        )
        // Mark consumed so a recreated activity doesn't report the same tap twice.
        intent.removeExtra(ClickIntents.EXTRA_CLICK_SOURCE)

        callback?.onWidgetClicked(event)
        return event
    }

    // ---- internal dispatch, called by the SDK's receivers ----

    internal fun dispatchPinAccepted(type: WidgetType) {
        callback?.onPinAccepted(type)
    }

    internal fun dispatchWidgetAdded(type: WidgetType) {
        callback?.onWidgetAdded(type)
    }

    internal fun dispatchWidgetRemoved(type: WidgetType) {
        callback?.onWidgetRemoved(type)
    }

    // ---- helpers ----

    private fun providerComponent(context: Context, type: WidgetType): ComponentName =
        when (type) {
            WidgetType.XML -> ComponentName(context, RetentionWidgetProvider::class.java)
            WidgetType.COMPOSE -> ComponentName(context, RetentionGlanceReceiver::class.java)
        }

    private fun widgetIds(context: Context, type: WidgetType): IntArray =
        AppWidgetManager.getInstance(context).getAppWidgetIds(providerComponent(context, type))

    private fun refreshAllWidgets(context: Context) {
        // XML widgets: synchronous RemoteViews update.
        val manager = AppWidgetManager.getInstance(context)
        val xmlIds = widgetIds(context, WidgetType.XML)
        if (xmlIds.isNotEmpty()) {
            val content = WidgetStorage.loadContent(context)
            xmlIds.forEach { id ->
                manager.updateAppWidget(
                    id,
                    RetentionWidgetProvider.buildViews(context, content, id),
                )
            }
        }
        // Compose widgets: Glance recomposition is suspend, run it off the caller thread.
        if (widgetIds(context, WidgetType.COMPOSE).isNotEmpty()) {
            sdkScope.launch { RetentionGlanceWidget().updateAll(context) }
        }
    }
}
