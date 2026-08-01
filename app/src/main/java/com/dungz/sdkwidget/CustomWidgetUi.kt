package com.dungz.sdkwidget

import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
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
import androidx.glance.LocalContext
import com.dungz.widgetsdk.RetentionWidgetSdk
import com.dungz.widgetsdk.WidgetClickSource
import com.dungz.widgetsdk.WidgetContent
import com.dungz.widgetsdk.WidgetXmlRenderer

/**
 * UI của widget do APP sở hữu, truyền vào SDK qua setXmlRenderer/setComposeContent.
 * SDK chỉ cung cấp dữ liệu ([WidgetContent]) và helper để gắn click callback.
 */
object CustomWidgetUi {

    /** UI tùy chỉnh cho widget XML: layout tím của app thay cho layout cam của SDK. */
    val xmlRenderer = WidgetXmlRenderer { context, widgetId, content ->
        RemoteViews(context.packageName, R.layout.widget_custom_xml).apply {
            setTextViewText(R.id.cw_title, "✨ ${content.title}")
            setTextViewText(R.id.cw_message, content.message)
            setTextViewText(R.id.cw_button, content.ctaText)
            setOnClickPendingIntent(
                R.id.cw_root,
                RetentionWidgetSdk.createClickPendingIntent(
                    context, widgetId, WidgetClickSource.BODY, content.deepLink,
                ),
            )
            setOnClickPendingIntent(
                R.id.cw_button,
                RetentionWidgetSdk.createClickPendingIntent(
                    context, widgetId, WidgetClickSource.CTA, content.deepLink,
                ),
            )
        }
    }

    /** UI tùy chỉnh cho widget Compose: Glance composable xanh lá của app. */
    val composeContent: @Composable (WidgetContent) -> Unit = { content ->
        val context = LocalContext.current
        val teal = Color(0xFF00796B)

        var root = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(teal))
            .cornerRadius(16.dp)
            .padding(16.dp)
        RetentionWidgetSdk.createClickAction(context, WidgetClickSource.BODY, content.deepLink)
            ?.let { root = root.clickable(it) }

        Column(modifier = root) {
            Text(
                text = "🌱 ${content.title}",
                maxLines = 1,
                style = TextStyle(
                    color = ColorProvider(Color.White),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Spacer(GlanceModifier.height(4.dp))
            Text(
                text = content.message,
                maxLines = 2,
                style = TextStyle(
                    color = ColorProvider(Color(0xCCFFFFFF)),
                    fontSize = 13.sp,
                ),
            )
            Spacer(GlanceModifier.defaultWeight())

            var cta = GlanceModifier
                .fillMaxWidth()
                .background(ColorProvider(Color.White))
                .cornerRadius(20.dp)
                .padding(vertical = 8.dp)
            RetentionWidgetSdk.createClickAction(context, WidgetClickSource.CTA, content.deepLink)
                ?.let { cta = cta.clickable(it) }
            Text(
                text = content.ctaText,
                modifier = cta,
                style = TextStyle(
                    color = ColorProvider(teal),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                ),
            )
        }
    }
}
