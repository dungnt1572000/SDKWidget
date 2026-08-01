package com.dungz.widgetsdk

/**
 * Global listener for widget lifecycle and interaction events.
 *
 * Register it in `Application.onCreate()` via [RetentionWidgetSdk.setCallback] so it is
 * re-attached after process death. All methods have empty defaults — override only what
 * you need.
 */
interface RetentionWidgetCallback {

    /** The user accepted the system pin dialog shown by [RetentionWidgetSdk.requestPinWidget]. */
    fun onPinAccepted(type: WidgetType) {}

    /** The first widget instance of [type] was added to the home screen. */
    fun onWidgetAdded(type: WidgetType) {}

    /** The last widget instance of [type] was removed from the home screen. */
    fun onWidgetRemoved(type: WidgetType) {}

    /**
     * The user tapped a widget. Fired when the opened activity hands its intent to
     * [RetentionWidgetSdk.consumeClickEvent].
     */
    fun onWidgetClicked(event: WidgetClickEvent) {}
}
