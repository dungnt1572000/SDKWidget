package com.dungz.widgetsdk

/** Which widget implementation a value refers to. Both can be on the home screen at once. */
enum class WidgetType {
    /** RemoteViews widget rendered from an XML layout ([RetentionWidgetProvider]). */
    XML,

    /** Glance widget rendered from Compose-style code ([com.dungz.widgetsdk.glance.RetentionGlanceReceiver]). */
    COMPOSE,
}
