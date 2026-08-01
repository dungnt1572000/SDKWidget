package com.dungz.widgetsdk

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.dungz.widgetsdk.internal.ClickIntents
import com.dungz.widgetsdk.internal.WidgetStorage

/**
 * RemoteViews-based (XML) home screen widget shipped by the SDK.
 *
 * The host app never touches this class directly — it is registered through the SDK's
 * manifest (merged automatically) and driven through [RetentionWidgetSdk]. If the host
 * app registered a [WidgetXmlRenderer], its RemoteViews are used instead of the SDK
 * default layout.
 */
class RetentionWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val content = WidgetStorage.loadContent(context)
        appWidgetIds.forEach { id ->
            appWidgetManager.updateAppWidget(id, buildViews(context, content, id))
        }
    }

    override fun onEnabled(context: Context) {
        RetentionWidgetSdk.dispatchWidgetAdded(WidgetType.XML)
    }

    override fun onDisabled(context: Context) {
        RetentionWidgetSdk.dispatchWidgetRemoved(WidgetType.XML)
    }

    internal companion object {

        private const val TAG = "RetentionWidgetSdk"

        fun buildViews(context: Context, content: WidgetContent, widgetId: Int): RemoteViews {
            RetentionWidgetSdk.xmlRenderer?.let { renderer ->
                try {
                    return renderer.create(context, widgetId, content)
                } catch (e: RuntimeException) {
                    Log.e(TAG, "Custom XML renderer failed, falling back to default UI", e)
                }
            }
            return buildDefaultViews(context, content, widgetId)
        }

        private fun buildDefaultViews(
            context: Context,
            content: WidgetContent,
            widgetId: Int,
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.rw_widget_retention)
            views.setTextViewText(R.id.rw_title, content.title)
            views.setTextViewText(R.id.rw_message, content.message)
            views.setTextViewText(R.id.rw_cta, content.ctaText)
            if (content.streakCount > 0) {
                views.setViewVisibility(R.id.rw_streak, View.VISIBLE)
                views.setTextViewText(
                    R.id.rw_streak,
                    context.getString(R.string.rw_streak_format, content.streakCount),
                )
            } else {
                views.setViewVisibility(R.id.rw_streak, View.GONE)
            }

            views.setOnClickPendingIntent(
                R.id.rw_root,
                ClickIntents.pendingIntent(
                    context, widgetId, WidgetClickSource.BODY, content.deepLink, WidgetType.XML,
                ),
            )
            views.setOnClickPendingIntent(
                R.id.rw_cta,
                ClickIntents.pendingIntent(
                    context, widgetId, WidgetClickSource.CTA, content.deepLink, WidgetType.XML,
                ),
            )
            return views
        }
    }
}
