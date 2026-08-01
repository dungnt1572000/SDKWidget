# Retention Widget SDK — Hướng dẫn tích hợp (Implementation Guide)

> SDK widget màn hình Home tăng retention, gồm 2 loại widget: **XML** (RemoteViews) và **Compose** (Glance).
> Cả 2 dùng chung nội dung (`WidgetContent`) và chung callback. App có thể dùng UI mặc định của SDK hoặc tự truyền UI vào.

- **Repo:** https://github.com/dungnt1572000/SDKWidget
- **Dependency:** `com.github.dungnt1572000.SDKWidget:sdk-widget:1.0.0` (JitPack)
- **minSdk:** 29 · **compileSdk yêu cầu:** 37

---

## 1. Thêm dependency (JitPack)

**Bước 1 — thêm repo JitPack** vào `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")   // ← thêm dòng này
    }
}
```

**Bước 2 — thêm dependency** vào `app/build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.dungnt1572000.SDKWidget:sdk-widget:1.0.0")
}
```

Không cần khai báo gì trong `AndroidManifest.xml` — manifest của SDK (2 widget provider + pin receiver) tự merge vào app.

> **Lưu ý:** SDK kéo theo `androidx.glance:glance-appwidget` (scope `api`). App **không cần** bật Compose nếu chỉ dùng UI mặc định hoặc chỉ custom widget XML. Chỉ khi dùng `setComposeContent` (custom UI Glance) thì app mới cần bật Compose compiler.

---

## 2. Khởi tạo SDK — `Application.onCreate()`

Tạo class Application (nếu chưa có) và đăng ký trong manifest:

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // 1. Init — bắt buộc. clickActivity = activity mở ra khi user ấn widget
        //    (null → dùng launcher activity). defaultContent chỉ áp dụng lần đầu.
        RetentionWidgetSdk.init(
            context = this,
            clickActivity = MainActivity::class.java,
            defaultContent = WidgetContent(
                title = "Chào mừng trở lại! 👋",
                message = "Giữ streak của bạn nhé",
                ctaText = "Mở app",
                streakCount = 0,
            ),
        )

        // 2. Callback toàn cục — đăng ký ở đây để sống sót qua process death
        RetentionWidgetSdk.setCallback(object : RetentionWidgetCallback {
            override fun onPinAccepted(type: WidgetType) {
                // User đồng ý dialog pin (analytics: pin_accepted)
            }
            override fun onWidgetAdded(type: WidgetType) {
                // Widget đầu tiên (của loại type) đã lên Home
            }
            override fun onWidgetRemoved(type: WidgetType) {
                // Widget cuối cùng (của loại type) bị gỡ
            }
            override fun onWidgetClicked(event: WidgetClickEvent) {
                // User ấn widget (bắn cùng lúc với consumeClickEvent)
            }
        })
    }
}
```

```xml
<!-- AndroidManifest.xml -->
<application android:name=".MyApp" ...>
    <!-- singleTop để tap widget khi app đang mở đi vào onNewIntent thay vì tạo activity mới -->
    <activity android:name=".MainActivity" android:launchMode="singleTop" ... />
</application>
```

> ⚠️ **Bắt buộc init + setCallback + set renderer (nếu có) trong `Application.onCreate()`.** Launcher có thể render lại widget bất cứ lúc nào (kể cả khi app vừa cold-start), và callback/renderer là in-memory — đăng ký muộn sẽ mất event hoặc render sai UI.

---

## 3. Thêm widget lên màn hình Home (pin flow)

Android **không có runtime permission** cho widget — dialog xác nhận pin của hệ thống chính là bước xin phép user. SDK bọc sẵn toàn bộ flow:

```kotlin
when (RetentionWidgetSdk.requestPinWidget(context, WidgetType.XML)) {  // hoặc WidgetType.COMPOSE
    PinWidgetResult.REQUESTED     -> { /* Dialog đã hiện — chờ onPinAccepted */ }
    PinWidgetResult.ALREADY_ADDED -> { /* Đã có widget trên Home (SDK giới hạn 1 widget) */ }
    PinWidgetResult.UNSUPPORTED   -> {
        // Launcher không hỗ trợ pin — hướng dẫn user thêm thủ công:
        // nhấn giữ màn hình Home → Widgets → tìm app
    }
}

// Check trước nếu muốn ẩn/hiện nút
val supported = RetentionWidgetSdk.isPinWidgetSupported(context)
```

**Các API trạng thái:**

```kotlin
RetentionWidgetSdk.hasWidget(context)                              // có widget nào trên Home không
RetentionWidgetSdk.getActiveWidgetCount(context)                   // tổng cả 2 loại
RetentionWidgetSdk.getActiveWidgetCount(context, WidgetType.XML)   // đếm riêng từng loại
```

> ⚠️ **User bấm Cancel dialog pin → không có callback** (giới hạn của Android — chỉ có success callback). Nếu cần biết, poll `getActiveWidgetCount()` khi app quay lại foreground (`onResume`).

---

## 4. Cập nhật nội dung widget

Gọi ở bất kỳ đâu — cả 2 loại widget ngoài Home cập nhật ngay lập tức. Nội dung được persist (SharedPreferences nội bộ SDK) nên widget vẫn render đúng sau khi app bị kill hoặc máy reboot:

```kotlin
RetentionWidgetSdk.updateWidget(
    context,
    WidgetContent(
        title = "Streak 7 ngày! 🔥",
        message = "Quay lại hôm nay để giữ streak",
        ctaText = "Học ngay",
        streakCount = 7,
        deepLink = "myapp://streak",   // trả về trong WidgetClickEvent khi user tap
    ),
)

// Đọc nội dung hiện tại
val current: WidgetContent = RetentionWidgetSdk.getWidgetContent(context)
```

Mọi field của `WidgetContent` đều có default — widget không bao giờ render rỗng.

---

## 5. Nhận sự kiện ấn widget

Khi user ấn widget, activity đã cấu hình ở `init` được mở. Gọi `consumeClickEvent` ở **cả `onCreate` và `onNewIntent`**:

```kotlin
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleWidgetClick(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleWidgetClick(intent)
    }

    private fun handleWidgetClick(intent: Intent?) {
        RetentionWidgetSdk.consumeClickEvent(intent)?.let { event ->
            // event.source     : BODY (thân widget) | CTA (nút call-to-action)
            // event.widgetType : XML | COMPOSE
            // event.deepLink   : deep link gắn trong WidgetContent lúc render (nullable)
            // event.widgetId   : id widget instance (widget COMPOSE trả INVALID_APPWIDGET_ID)
            event.deepLink?.let { navigateTo(it) }
        }
    }
}
```

- Event **chỉ consume 1 lần** — xoay màn hình / recreate activity không bị replay click.
- `consumeClickEvent` trả `null` nếu intent không phải từ widget → an toàn gọi với mọi intent.
- Event cũng được forward vào `RetentionWidgetCallback.onWidgetClicked` (tiện bắn analytics tập trung).

---

## 6. (Tuỳ chọn) Truyền UI riêng của app vào widget

Không đăng ký → widget dùng UI mặc định của SDK (gradient cam, badge streak, nút CTA). Muốn UI riêng, đăng ký trong `Application.onCreate()` — **luôn gắn click qua helper của SDK** để callback tiếp tục hoạt động.

### 6a. Custom UI cho widget XML (`WidgetXmlRenderer`)

```kotlin
RetentionWidgetSdk.setXmlRenderer { context, widgetId, content ->
    RemoteViews(context.packageName, R.layout.my_widget).apply {
        setTextViewText(R.id.my_title, content.title)
        setTextViewText(R.id.my_message, content.message)

        // Click thân widget
        setOnClickPendingIntent(
            R.id.my_root,
            RetentionWidgetSdk.createClickPendingIntent(
                context, widgetId, WidgetClickSource.BODY, content.deepLink,
            ),
        )
        // Click nút CTA
        setOnClickPendingIntent(
            R.id.my_cta,
            RetentionWidgetSdk.createClickPendingIntent(
                context, widgetId, WidgetClickSource.CTA, content.deepLink,
            ),
        )
    }
}
```

### 6b. Custom UI cho widget Compose (Glance)

Lambda nhận `WidgetContent` và phải emit **Glance composables** (`androidx.glance.*`, không phải Compose UI thường):

```kotlin
RetentionWidgetSdk.setComposeContent { content ->
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(Color(0xFF1B5E20))
            .clickable(
                RetentionWidgetSdk.createClickAction(
                    context, WidgetClickSource.BODY, content.deepLink,
                )!!
            ),
    ) {
        Text(content.title, style = TextStyle(color = ColorProvider(Color.White)))
        Text(content.message)
        Button(
            text = content.ctaText,
            onClick = RetentionWidgetSdk.createClickAction(
                context, WidgetClickSource.CTA, content.deepLink,
            )!!,
        )
    }
}
```

- Truyền `null` cho `setXmlRenderer` / `setComposeContent` → quay về UI mặc định của SDK.
- Sau khi đổi renderer lúc runtime, gọi `updateWidget` để re-render ngay.
- Renderer throw exception → SDK tự fallback về UI mặc định (widget không bao giờ vỡ).

---

## 7. Gọi từ Java

Hầu hết API có `@JvmStatic` — app Java thuần gọi bình thường:

```java
RetentionWidgetSdk.init(context, MainActivity.class, null);
RetentionWidgetSdk.updateWidget(context, new WidgetContent("Title", "Msg", "Open", 7, null));
RetentionWidgetSdk.requestPinWidget(context, WidgetType.XML);
WidgetClickEvent event = RetentionWidgetSdk.consumeClickEvent(getIntent());
```

Riêng `setComposeContent` là Kotlin-only (lambda `@Composable`).

---

## 8. Tham chiếu API nhanh

| API | Mô tả |
|---|---|
| `init(context, clickActivity?, defaultContent?)` | Khởi tạo — gọi 1 lần trong `Application.onCreate()` |
| `setCallback(callback?)` | Listener toàn cục pin/add/remove/click (`null` để clear) |
| `setXmlRenderer(renderer?)` | Custom UI widget XML (`null` → UI mặc định) |
| `setComposeContent(content?)` | Custom UI widget Glance (`null` → UI mặc định) |
| `updateWidget(context, content)` | Persist nội dung + re-render mọi widget ngay |
| `getWidgetContent(context)` | Nội dung đang render |
| `requestPinWidget(context, type)` | Hiện dialog pin → `REQUESTED / ALREADY_ADDED / UNSUPPORTED` |
| `isPinWidgetSupported(context)` | Launcher có hỗ trợ pin không |
| `getActiveWidgetCount(context, type?)` | Số widget trên Home (theo loại hoặc tổng) |
| `hasWidget(context)` | Có ít nhất 1 widget trên Home |
| `createClickPendingIntent(context, widgetId, source, deepLink)` | PendingIntent cho tap target trong custom XML UI |
| `createClickAction(context, source, deepLink)` | Glance `Action` cho tap target trong custom Compose UI |
| `consumeClickEvent(intent)` | Lấy `WidgetClickEvent` từ intent (consume 1 lần) |

**Model:**

- `WidgetContent(title, message, ctaText, streakCount, deepLink?)` — mọi field có default
- `WidgetClickEvent(source, widgetType, deepLink?, widgetId)`
- `WidgetClickSource` = `BODY | CTA` · `WidgetType` = `XML | COMPOSE`
- `PinWidgetResult` = `REQUESTED | ALREADY_ADDED | UNSUPPORTED`

---

## 9. Giới hạn cần biết

1. **Cancel dialog pin không có callback** — poll `getActiveWidgetCount()` ở `onResume` nếu cần đo funnel.
2. **SDK chỉ cho pin 1 widget** qua `requestPinWidget` (tính chung cả 2 loại). User vẫn có thể kéo thêm thủ công từ widget picker — Android không chặn được.
3. Một số OEM launcher không hỗ trợ pin → nhận `UNSUPPORTED`, hãy hướng dẫn user thêm thủ công.
4. Click từ widget **COMPOSE** có `widgetId = INVALID_APPWIDGET_ID` (giới hạn của Glance) — `source`/`deepLink`/`widgetType` vẫn đầy đủ.
5. Renderer/composeContent là in-memory — **phải đăng ký lại mỗi lần app khởi động** (trong `Application.onCreate()`), nếu không lần render đầu sau cold-start sẽ dùng UI mặc định.
6. Chưa có scheduled update — nội dung chỉ đổi khi app gọi `updateWidget`. Cần update định kỳ (streak reset nửa đêm...) thì app tự schedule bằng WorkManager và gọi `updateWidget` trong worker.

---

## 10. Checklist tích hợp

- [ ] Thêm `maven("https://jitpack.io")` vào `settings.gradle.kts`
- [ ] Thêm `implementation("com.github.dungnt1572000.SDKWidget:sdk-widget:1.0.0")`
- [ ] `compileSdk = 37`, `minSdk >= 29`
- [ ] Tạo `Application` class: `init` + `setCallback` (+ `setXmlRenderer`/`setComposeContent` nếu custom UI)
- [ ] Activity nhận click: `launchMode="singleTop"` + gọi `consumeClickEvent` ở `onCreate` **và** `onNewIntent`
- [ ] Test: pin widget → tap BODY/CTA → kill app → tap lại (cold start vẫn nhận event)
