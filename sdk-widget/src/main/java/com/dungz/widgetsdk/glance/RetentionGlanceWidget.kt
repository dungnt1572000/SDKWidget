package com.dungz.widgetsdk.glance

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.action.clickable
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.dungz.widgetsdk.R
import com.dungz.widgetsdk.RetentionWidgetSdk
import com.dungz.widgetsdk.WidgetClickSource
import com.dungz.widgetsdk.WidgetContent
import com.dungz.widgetsdk.WidgetType
import com.dungz.widgetsdk.internal.ClickIntents
import com.dungz.widgetsdk.internal.WidgetStorage

/**
 * Compose (Glance) flavor of the retention widget.
 *
 * Renders the content registered through [RetentionWidgetSdk.updateWidget]. If the host
 * app registered custom UI via [RetentionWidgetSdk.setComposeContent], that content is
 * composed instead of the SDK default.
 */
class RetentionGlanceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val content = WidgetStorage.loadContent(context)
            val custom = RetentionWidgetSdk.composeContent
            if (custom != null) {
                custom(content)
            } else {
                DefaultGlanceContent(context, content)
            }
        }
    }
}

private val Accent = Color(0xFFFF5B2C)

@Composable
private fun DefaultGlanceContent(context: Context, content: WidgetContent) {
    // Glance can't recover the int widget id cheaply; click events from the Compose
    // widget carry INVALID_APPWIDGET_ID. Source/deepLink/type are what consumers use.
    val bodyIntent = ClickIntents.buildIntent(
        context, AppWidgetManager.INVALID_APPWIDGET_ID,
        WidgetClickSource.BODY, content.deepLink, WidgetType.COMPOSE,
    )
    val ctaIntent = ClickIntents.buildIntent(
        context, AppWidgetManager.INVALID_APPWIDGET_ID,
        WidgetClickSource.CTA, content.deepLink, WidgetType.COMPOSE,
    )

    var root = GlanceModifier
        .fillMaxSize()
        .background(ColorProvider(Accent))
        .cornerRadius(16.dp)
        .padding(16.dp)
    if (bodyIntent != null) root = root.clickable(actionStartActivity(bodyIntent))

    Column(modifier = root) {
        if (content.streakCount > 0) {
            Text(
                text = context.getString(R.string.rw_streak_format, content.streakCount),
                style = TextStyle(
                    color = ColorProvider(Color.White),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Spacer(GlanceModifier.height(4.dp))
        }
        Text(
            text = content.title,
            maxLines = 1,
            style = TextStyle(
                color = ColorProvider(Color.White),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        Spacer(GlanceModifier.height(2.dp))
        Text(
            text = content.message,
            maxLines = 2,
            style = TextStyle(
                color = ColorProvider(Color(0xE6FFFFFF)),
                fontSize = 13.sp,
            ),
        )
        Spacer(GlanceModifier.defaultWeight())

        var cta = GlanceModifier
            .fillMaxWidth()
            .background(ColorProvider(Color.White))
            .cornerRadius(20.dp)
            .padding(vertical = 8.dp)
        if (ctaIntent != null) cta = cta.clickable(actionStartActivity(ctaIntent))
        Text(
            text = content.ctaText,
            modifier = cta,
            style = TextStyle(
                color = ColorProvider(Accent),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            ),
        )
    }
}
