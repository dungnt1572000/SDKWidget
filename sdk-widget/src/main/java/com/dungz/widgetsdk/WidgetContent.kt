package com.dungz.widgetsdk

/**
 * Content rendered inside the home screen widget.
 *
 * All fields are optional; sensible defaults are used so the widget never renders empty.
 * [deepLink] is delivered back to the app inside [WidgetClickEvent] when the user taps
 * the widget, letting you route to a specific screen.
 */
data class WidgetContent(
    val title: String = "We miss you! 👋",
    val message: String = "Come back and keep your streak alive.",
    val ctaText: String = "Open app",
    val streakCount: Int = 0,
    val deepLink: String? = null,
)
