package com.dungz.sdkwidget

import android.app.Application
import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.core.content.edit
import com.dungz.widgetsdk.RetentionWidgetCallback
import com.dungz.widgetsdk.RetentionWidgetSdk
import com.dungz.widgetsdk.WidgetClickEvent
import com.dungz.widgetsdk.WidgetContent
import com.dungz.widgetsdk.WidgetType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** In-memory event log so the demo UI can show what the SDK callbacks report. */
object WidgetEventLog {
    val events = mutableStateListOf<String>()

    fun add(message: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        events.add(0, "[$time] $message")
    }
}

class DemoApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // 1. Init the SDK: which activity opens on widget tap + first-launch content.
        RetentionWidgetSdk.init(
            context = this,
            clickActivity = MainActivity::class.java,
            defaultContent = WidgetContent(
                title = "Quay lại nào! 👋",
                message = "Chuỗi ngày của bạn đang chờ.",
                ctaText = "Mở app",
                streakCount = 1,
                deepLink = "demo://home",
            ),
        )

        // 2. Re-apply the persisted custom-UI choice. Must happen in Application.onCreate:
        //    the launcher can re-render widgets after process death, and the renderers
        //    live only in memory.
        applyCustomUi(this, isCustomUiEnabled(this))

        // 3. Register the global callback in Application.onCreate so it survives
        //    process death (widget taps can cold-start the app).
        RetentionWidgetSdk.setCallback(object : RetentionWidgetCallback {
            override fun onPinAccepted(type: WidgetType) {
                WidgetEventLog.add("Người dùng đồng ý thêm widget $type (pin accepted)")
            }

            override fun onWidgetAdded(type: WidgetType) {
                WidgetEventLog.add("Widget $type đã được thêm vào Home screen")
            }

            override fun onWidgetRemoved(type: WidgetType) {
                WidgetEventLog.add("Widget $type đã bị gỡ khỏi Home screen")
            }

            override fun onWidgetClicked(event: WidgetClickEvent) {
                WidgetEventLog.add(
                    "Widget ${event.widgetType} được ấn: source=${event.source}, " +
                        "deepLink=${event.deepLink}"
                )
            }
        })
    }

    companion object {
        private const val PREFS = "demo_settings"
        private const val KEY_CUSTOM_UI = "custom_ui"

        fun isCustomUiEnabled(context: Context): Boolean =
            context.getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_CUSTOM_UI, false)

        /** Switches both widget types between the SDK default UI and the app's own UI. */
        fun setCustomUiEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREFS, MODE_PRIVATE).edit {
                putBoolean(KEY_CUSTOM_UI, enabled)
            }
            applyCustomUi(context, enabled)
            // Re-render widgets already on the home screen with the new UI.
            RetentionWidgetSdk.updateWidget(context, RetentionWidgetSdk.getWidgetContent(context))
        }

        private fun applyCustomUi(context: Context, enabled: Boolean) {
            if (enabled) {
                RetentionWidgetSdk.setXmlRenderer(CustomWidgetUi.xmlRenderer)
                RetentionWidgetSdk.setComposeContent(CustomWidgetUi.composeContent)
            } else {
                RetentionWidgetSdk.setXmlRenderer(null)
                RetentionWidgetSdk.setComposeContent(null)
            }
        }
    }
}
