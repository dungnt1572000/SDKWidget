package com.dungz.widgetsdk

/** Outcome of [RetentionWidgetSdk.requestPinWidget]. */
enum class PinWidgetResult {
    /**
     * The system pin dialog was shown. Whether the user accepts is reported later via
     * [RetentionWidgetCallback.onPinAccepted].
     */
    REQUESTED,

    /**
     * A widget instance (of either type) is already on the home screen. The SDK allows
     * only one widget at a time.
     */
    ALREADY_ADDED,

    /**
     * The launcher doesn't support programmatic pinning — guide the user to add the
     * widget manually from the widget picker.
     */
    UNSUPPORTED,
}
