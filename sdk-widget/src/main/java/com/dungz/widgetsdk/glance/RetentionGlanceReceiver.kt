package com.dungz.widgetsdk.glance

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.dungz.widgetsdk.RetentionWidgetSdk
import com.dungz.widgetsdk.WidgetType

/** Manifest-registered receiver backing the Compose (Glance) widget. */
class RetentionGlanceReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = RetentionGlanceWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        RetentionWidgetSdk.dispatchWidgetAdded(WidgetType.COMPOSE)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        RetentionWidgetSdk.dispatchWidgetRemoved(WidgetType.COMPOSE)
    }
}
