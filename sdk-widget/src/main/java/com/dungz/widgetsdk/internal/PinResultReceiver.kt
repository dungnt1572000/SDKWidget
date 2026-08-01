package com.dungz.widgetsdk.internal

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dungz.widgetsdk.RetentionWidgetSdk
import com.dungz.widgetsdk.WidgetType

/**
 * Target of the success PendingIntent passed to
 * `AppWidgetManager.requestPinAppWidget`. The system fires it only when the user
 * accepts the pin dialog (Android provides no cancel signal).
 */
internal class PinResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val typeName = intent.getStringExtra(ClickIntents.EXTRA_WIDGET_TYPE)
        val type = WidgetType.entries.firstOrNull { it.name == typeName } ?: WidgetType.XML
        RetentionWidgetSdk.dispatchPinAccepted(type)
    }

    internal companion object {
        const val ACTION_PIN_ACCEPTED = "com.dungz.widgetsdk.action.PIN_ACCEPTED"
    }
}
