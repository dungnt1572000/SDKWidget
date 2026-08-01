package com.dungz.widgetsdk.internal

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.dungz.widgetsdk.WidgetClickSource
import com.dungz.widgetsdk.WidgetType

/**
 * Builds the intents fired when the user taps a widget. Shared by the XML provider,
 * the Glance widget and the public helpers exposed to custom renderers.
 */
internal object ClickIntents {

    const val ACTION_WIDGET_CLICK = "com.dungz.widgetsdk.action.WIDGET_CLICK"
    const val EXTRA_CLICK_SOURCE = "com.dungz.widgetsdk.extra.CLICK_SOURCE"
    const val EXTRA_DEEP_LINK = "com.dungz.widgetsdk.extra.DEEP_LINK"
    const val EXTRA_WIDGET_ID = "com.dungz.widgetsdk.extra.WIDGET_ID"
    const val EXTRA_WIDGET_TYPE = "com.dungz.widgetsdk.extra.WIDGET_TYPE"

    /**
     * Intent that opens the configured click activity (or the launcher activity as
     * fallback) with click metadata attached. Null only if no launchable activity exists.
     */
    fun buildIntent(
        context: Context,
        widgetId: Int,
        source: WidgetClickSource,
        deepLink: String?,
        type: WidgetType,
    ): Intent? {
        val component = WidgetStorage.loadClickComponent(context)
        val intent = if (component != null) {
            Intent().setComponent(component)
        } else {
            context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?: return null
        }
        intent.action = ACTION_WIDGET_CLICK
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        intent.putExtra(EXTRA_CLICK_SOURCE, source.name)
        intent.putExtra(EXTRA_DEEP_LINK, deepLink)
        intent.putExtra(EXTRA_WIDGET_ID, widgetId)
        intent.putExtra(EXTRA_WIDGET_TYPE, type.name)
        return intent
    }

    fun pendingIntent(
        context: Context,
        widgetId: Int,
        source: WidgetClickSource,
        deepLink: String?,
        type: WidgetType,
    ): PendingIntent? {
        val intent = buildIntent(context, widgetId, source, deepLink, type) ?: return null
        // Unique request code per widget instance, tap target and widget type so the
        // PendingIntents don't overwrite each other.
        val requestCode = (widgetId * 10 + source.ordinal) * 2 + type.ordinal
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
