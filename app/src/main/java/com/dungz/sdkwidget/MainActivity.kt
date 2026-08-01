package com.dungz.sdkwidget

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dungz.sdkwidget.ui.theme.SDKWidgetTheme
import com.dungz.widgetsdk.PinWidgetResult
import com.dungz.widgetsdk.RetentionWidgetSdk
import com.dungz.widgetsdk.WidgetContent
import com.dungz.widgetsdk.WidgetType

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Widget tap that cold-started the app lands here.
        handleWidgetClick(intent)

        setContent {
            SDKWidgetTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    DemoScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }

    // Widget tap while the app is already open (singleTop) lands here.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleWidgetClick(intent)
    }

    private fun handleWidgetClick(intent: Intent?) {
        val event = RetentionWidgetSdk.consumeClickEvent(intent) ?: return
        // The global callback already logged it; here is where a real app would
        // navigate based on event.deepLink.
        Toast.makeText(
            this,
            "Mở từ widget ${event.widgetType}: ${event.source}",
            Toast.LENGTH_SHORT,
        ).show()
    }
}

@Composable
fun DemoScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("Quay lại nào! 👋") }
    var message by remember { mutableStateOf("Chuỗi ngày của bạn đang chờ.") }
    var streak by remember { mutableIntStateOf(1) }
    var customUi by remember { mutableStateOf(DemoApp.isCustomUiEnabled(context)) }
    // Computed each recomposition: the event log below is state, so every SDK callback
    // (add/remove/pin) recomposes this screen and the counts refresh with it.
    val xmlCount = RetentionWidgetSdk.getActiveWidgetCount(context, WidgetType.XML)
    val composeCount = RetentionWidgetSdk.getActiveWidgetCount(context, WidgetType.COMPOSE)

    fun requestPin(type: WidgetType) {
        when (RetentionWidgetSdk.requestPinWidget(context, type)) {
            PinWidgetResult.REQUESTED -> Unit // dialog hệ thống đang hiện
            PinWidgetResult.ALREADY_ADDED -> Toast.makeText(
                context,
                "Chỉ được thêm 1 widget — hãy gỡ widget hiện tại trước",
                Toast.LENGTH_LONG,
            ).show()
            PinWidgetResult.UNSUPPORTED -> Toast.makeText(
                context,
                "Launcher không hỗ trợ pin widget — hãy thêm thủ công từ widget picker",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Retention Widget SDK Demo", style = MaterialTheme.typography.titleLarge)
        Text(
            "Widget đang hoạt động — XML: $xmlCount · Compose: $composeCount",
            style = MaterialTheme.typography.bodyMedium,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.padding(end = 8.dp)) {
                Text("UI tùy chỉnh từ app", style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (customUi) "Đang dùng UI của app (tím/xanh lá)"
                    else "Đang dùng UI mặc định của SDK (cam)",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(
                checked = customUi,
                onCheckedChange = { enabled ->
                    customUi = enabled
                    DemoApp.setCustomUiEnabled(context, enabled)
                },
            )
        }

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Tiêu đề widget") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = message,
            onValueChange = { message = it },
            label = { Text("Nội dung widget") },
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { if (streak > 0) streak-- }) { Text("-") }
            Text(
                "Streak: $streak ngày",
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
            OutlinedButton(onClick = { streak++ }) { Text("+") }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = { requestPin(WidgetType.XML) },
                modifier = Modifier.weight(1f),
            ) {
                Text("Thêm widget XML")
            }
            Button(
                onClick = { requestPin(WidgetType.COMPOSE) },
                modifier = Modifier.weight(1f),
            ) {
                Text("Thêm widget Compose")
            }
        }

        Button(
            onClick = {
                RetentionWidgetSdk.updateWidget(
                    context,
                    WidgetContent(
                        title = title,
                        message = message,
                        ctaText = "Mở app",
                        streakCount = streak,
                        deepLink = "demo://streak/$streak",
                    ),
                )
                Toast.makeText(context, "Đã cập nhật widget", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Cập nhật nội dung widget")
        }

        HorizontalDivider()

        Text("Sự kiện từ SDK", style = MaterialTheme.typography.titleMedium)
        Card(modifier = Modifier.fillMaxWidth()) {
            if (WidgetEventLog.events.isEmpty()) {
                Text(
                    "Chưa có sự kiện nào. Thêm widget rồi ấn thử vào nó.",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    WidgetEventLog.events.forEach { event ->
                        Text(event, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
