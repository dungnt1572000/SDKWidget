package com.dungz.widgetsdk

import android.content.Context
import android.widget.RemoteViews

/**
 * Lets the host app supply its own UI for the XML widget.
 *
 * Build a [RemoteViews] from any layout in the app's resources and wire the tap targets
 * with [RetentionWidgetSdk.createClickPendingIntent] so click callbacks keep working:
 *
 * ```
 * RetentionWidgetSdk.setXmlRenderer { context, widgetId, content ->
 *     RemoteViews(context.packageName, R.layout.my_widget).apply {
 *         setTextViewText(R.id.my_title, content.title)
 *         setOnClickPendingIntent(
 *             R.id.my_root,
 *             RetentionWidgetSdk.createClickPendingIntent(
 *                 context, widgetId, WidgetClickSource.BODY, content.deepLink,
 *             ),
 *         )
 *     }
 * }
 * ```
 *
 * Register it in `Application.onCreate()` so the widget renders correctly after the
 * launcher restarts the app process.
 */
fun interface WidgetXmlRenderer {
    fun create(context: Context, widgetId: Int, content: WidgetContent): RemoteViews
}
