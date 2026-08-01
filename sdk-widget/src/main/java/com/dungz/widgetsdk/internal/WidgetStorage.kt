package com.dungz.widgetsdk.internal

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import com.dungz.widgetsdk.WidgetContent
import androidx.core.content.edit

/**
 * Persists widget content and configuration so the widget can re-render after process
 * death (the launcher calls the provider without the app being started by the user).
 */
internal object WidgetStorage {

    private const val PREFS = "retention_widget_sdk"
    private const val KEY_TITLE = "title"
    private const val KEY_MESSAGE = "message"
    private const val KEY_CTA = "cta"
    private const val KEY_STREAK = "streak"
    private const val KEY_DEEP_LINK = "deep_link"
    private const val KEY_CLICK_COMPONENT = "click_component"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun saveContent(context: Context, content: WidgetContent) {
        prefs(context).edit()
            .putString(KEY_TITLE, content.title)
            .putString(KEY_MESSAGE, content.message)
            .putString(KEY_CTA, content.ctaText)
            .putInt(KEY_STREAK, content.streakCount)
            .putString(KEY_DEEP_LINK, content.deepLink)
            .apply()
    }

    fun hasContent(context: Context): Boolean = prefs(context).contains(KEY_TITLE)

    fun loadContent(context: Context): WidgetContent {
        val p = prefs(context)
        val defaults = WidgetContent()
        return WidgetContent(
            title = p.getString(KEY_TITLE, defaults.title) ?: defaults.title,
            message = p.getString(KEY_MESSAGE, defaults.message) ?: defaults.message,
            ctaText = p.getString(KEY_CTA, defaults.ctaText) ?: defaults.ctaText,
            streakCount = p.getInt(KEY_STREAK, defaults.streakCount),
            deepLink = p.getString(KEY_DEEP_LINK, null),
        )
    }

    fun saveClickComponent(context: Context, component: ComponentName?) {
        prefs(context).edit {
            putString(KEY_CLICK_COMPONENT, component?.flattenToString())
        }
    }

    fun loadClickComponent(context: Context): ComponentName? =
        prefs(context).getString(KEY_CLICK_COMPONENT, null)
            ?.let(ComponentName::unflattenFromString)
}
