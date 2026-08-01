package com.dungz.widgetsdk

/** Which part of the widget the user tapped. */
enum class WidgetClickSource {
    /** The widget body (anywhere outside the CTA button). */
    BODY,

    /** The call-to-action button. */
    CTA,
}

/**
 * Describes a tap on the home screen widget.
 *
 * @property source which part of the widget was tapped
 * @property widgetType whether the XML or the Compose (Glance) widget was tapped
 * @property deepLink the deep link that was attached to the widget content at render
 *   time, or null if none was set
 * @property widgetId the system id of the tapped widget instance. Taps on the Compose
 *   widget report [android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID].
 */
data class WidgetClickEvent(
    val source: WidgetClickSource,
    val widgetType: WidgetType,
    val deepLink: String?,
    val widgetId: Int,
)
