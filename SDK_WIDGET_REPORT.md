# Báo cáo: Retention Widget SDK (`sdk-widget`)

> Ngày: 2026-08-01 · Trạng thái: **Hoàn thành, build pass** (`./gradlew :app:assembleDebug` ✅)
> Cập nhật v2: thêm widget Compose (Glance) + cơ chế truyền UI từ app vào SDK.

## 1. Đã làm gì

- Tạo module Android library mới **`:sdk-widget`** — SDK widget màn hình Home nhằm tăng retention, gồm **2 loại widget**:
  - **XML** (`WidgetType.XML`) — RemoteViews từ layout XML.
  - **Compose** (`WidgetType.COMPOSE`) — Glance, viết bằng cú pháp Composable.
- **Truyền UI từ app vào SDK**: app đăng ký `setXmlRenderer` (RemoteViews từ layout của app) và/hoặc `setComposeContent` (Glance composable của app). Không đăng ký → dùng UI mặc định của SDK.
- Sửa module **`:app`** thành app demo đầy đủ (Compose UI): thêm từng loại widget, toggle UI mặc định ↔ UI tùy chỉnh, cập nhật nội dung, xem log sự kiện.
- Chưa test runtime trên máy thật/emulator (không có thiết bị kết nối lúc build) — cần chạy thử theo mục 7.

## 2. Quyết định thiết kế quan trọng

| Vấn đề | Quyết định | Lý do |
|---|---|---|
| 2 loại XML & Compose | 2 provider riêng: `RetentionWidgetProvider` (RemoteViews) và `RetentionGlanceReceiver` (Glance) — chung content, chung callback | Glance là cách chính thức của Google để viết widget bằng Compose. Cả 2 hiện trong widget picker, có thể cùng nằm trên Home. |
| Truyền UI từ app | Hook in-memory: `WidgetXmlRenderer` (fun interface trả RemoteViews) + `@Composable (WidgetContent) -> Unit` (Glance). Đăng ký trong `Application.onCreate` | Renderer là code nên không persist được — `Application.onCreate` luôn chạy trước khi launcher render lại widget (kể cả cold start), nên hook luôn kịp đăng ký. SDK fallback về UI mặc định nếu renderer null hoặc throw. |
| "Xin permission" | Dùng flow `AppWidgetManager.requestPinAppWidget` | Android **không có runtime permission** cho widget. Dialog pin của hệ thống chính là bước xin phép người dùng. SDK bọc sẵn: check hỗ trợ + hiện dialog + callback khi user đồng ý (kèm loại widget). |
| Callback khi ấn widget | Ấn widget → mở Activity đã cấu hình (kèm extras) → app gọi `consumeClickEvent(intent)` → SDK trả `WidgetClickEvent` + bắn vào callback toàn cục | Mở Activity trực tiếp từ PendingIntent là đường duy nhất chạy ổn định trên Android 10+ (hạn chế background activity start). UI tùy chỉnh gắn click qua helper `createClickPendingIntent` (XML) / `createClickAction` (Glance) nên callback vẫn hoạt động. |
| Dữ liệu widget sau khi app bị kill | Lưu `SharedPreferences` nội bộ SDK | Launcher gọi provider bất cứ lúc nào, kể cả khi process app chết — widget vẫn render đúng nội dung cuối. |
| Chỉ 1 widget duy nhất | `requestPinWidget` check `hasWidget()` (tính chung cả 2 loại) → trả `PinWidgetResult.ALREADY_ADDED`, không hiện dialog | SDK enforce tại điểm vào duy nhất mà app kiểm soát. Lưu ý: user vẫn có thể kéo thêm widget thủ công từ widget picker của launcher — Android không có API chặn việc này. |

## 3. Cấu trúc module `sdk-widget`

```
sdk-widget/
├── build.gradle.kts                  # com.android.library + compose plugin, minSdk 29, compileSdk 37
├── consumer-rules.pro                # keep rules tự áp cho app tích hợp
└── src/main/
    ├── AndroidManifest.xml           # khai báo 2 widget provider + pin receiver (tự merge vào app)
    ├── java/com/dungz/widgetsdk/
    │   ├── RetentionWidgetSdk.kt     # ★ Facade duy nhất app cần đụng tới
    │   ├── RetentionWidgetProvider.kt# Widget XML (RemoteViews), ưu tiên renderer của app
    │   ├── WidgetType.kt             # enum XML | COMPOSE
    │   ├── WidgetXmlRenderer.kt      # fun interface: app truyền RemoteViews UI vào
    │   ├── WidgetContent.kt          # data: title, message, ctaText, streakCount, deepLink
    │   ├── WidgetClickEvent.kt       # data: source (BODY/CTA), widgetType, deepLink, widgetId
    │   ├── RetentionWidgetCallback.kt# interface callback (default rỗng, kèm WidgetType)
    │   ├── glance/
    │   │   ├── RetentionGlanceWidget.kt   # Widget Compose (Glance), ưu tiên content của app
    │   │   └── RetentionGlanceReceiver.kt # receiver cho widget Glance
    │   └── internal/
    │       ├── WidgetStorage.kt      # SharedPreferences persistence
    │       ├── ClickIntents.kt       # build intent/PendingIntent click, dùng chung 2 loại
    │       └── PinResultReceiver.kt  # nhận kết quả pin dialog
    └── res/
        ├── layout/rw_widget_retention.xml   # layout mặc định widget XML
        ├── drawable/rw_*.xml                # gradient cam, nút trắng bo góc, badge streak
        ├── xml/rw_widget_info.xml           # info widget XML (3x2 cell, resizable)
        ├── xml/rw_widget_info_compose.xml   # info widget Compose (Glance)
        └── values/strings.xml               # prefix rw_ tránh đụng resource app
```

## 4. Public API

```kotlin
// Application.onCreate()
RetentionWidgetSdk.init(context, clickActivity = MainActivity::class.java, defaultContent = WidgetContent(...))
RetentionWidgetSdk.setCallback(object : RetentionWidgetCallback {
    override fun onPinAccepted(type: WidgetType) {}       // user đồng ý dialog pin
    override fun onWidgetAdded(type: WidgetType) {}       // widget đầu tiên (mỗi loại) lên Home
    override fun onWidgetRemoved(type: WidgetType) {}     // widget cuối cùng (mỗi loại) bị gỡ
    override fun onWidgetClicked(e: WidgetClickEvent) {}  // user ấn widget
})

// (Tuỳ chọn) Truyền UI từ app vào — không gọi thì dùng UI mặc định của SDK
RetentionWidgetSdk.setXmlRenderer { context, widgetId, content ->     // UI cho widget XML
    RemoteViews(context.packageName, R.layout.my_widget).apply {
        setTextViewText(R.id.my_title, content.title)
        setOnClickPendingIntent(
            R.id.my_root,
            RetentionWidgetSdk.createClickPendingIntent(context, widgetId, WidgetClickSource.BODY, content.deepLink),
        )
    }
}
RetentionWidgetSdk.setComposeContent { content ->                     // UI cho widget Compose (Glance)
    Column(GlanceModifier.clickable(
        RetentionWidgetSdk.createClickAction(LocalContext.current, WidgetClickSource.BODY, content.deepLink)!!
    )) { Text(content.title) }
}

// Bất kỳ đâu
RetentionWidgetSdk.updateWidget(context, WidgetContent(title, message, ctaText, streakCount, deepLink))
// Chính sách 1-widget-duy-nhất: trả PinWidgetResult.REQUESTED | ALREADY_ADDED | UNSUPPORTED
RetentionWidgetSdk.requestPinWidget(context, WidgetType.XML)      // hoặc WidgetType.COMPOSE
RetentionWidgetSdk.isPinWidgetSupported(context)
RetentionWidgetSdk.getActiveWidgetCount(context)                  // tổng; truyền type để đếm riêng
RetentionWidgetSdk.hasWidget(context)
RetentionWidgetSdk.getWidgetContent(context)

// Trong Activity được mở bởi widget (onCreate + onNewIntent)
RetentionWidgetSdk.consumeClickEvent(intent)?.let { event ->
    // event.source = BODY | CTA, event.widgetType = XML | COMPOSE, event.deepLink → điều hướng
}
```

Hầu hết API có `@JvmStatic` → gọi từ Java (app XML thuần) tự nhiên. Riêng `setComposeContent` là Kotlin-only (lambda `@Composable`).

## 5. Thay đổi ở module `app` (demo)

| File | Thay đổi |
|---|---|
| `settings.gradle.kts` | `include(":sdk-widget")` |
| `build.gradle.kts` (root) | thêm `android-library` plugin `apply false` |
| `gradle/libs.versions.toml` | thêm alias `android-library`, `glance-appwidget 1.1.1`, `kotlinx-coroutines-android 1.10.1` |
| `app/build.gradle.kts` | `implementation(project(":sdk-widget"))`; compileSdk 36.1 → **37** (bắt buộc: core-ktx 1.19.0 yêu cầu 37 — lỗi có sẵn của template, không phải do SDK) |
| `app/AndroidManifest.xml` | `android:name=".DemoApp"`, MainActivity `launchMode="singleTop"` (để ấn widget khi app đang mở đi vào `onNewIntent`) |
| `DemoApp.kt` (mới) | init SDK + đăng ký callback + đăng ký lại UI tùy chỉnh (đọc từ prefs) trong `Application.onCreate` (sống sót qua process death) + `WidgetEventLog` cho UI |
| `CustomWidgetUi.kt` (mới) | **Demo truyền UI từ app vào**: `xmlRenderer` dùng `res/layout/widget_custom_xml.xml` (tím) + `composeContent` là Glance composable (xanh lá), gắn click qua helper của SDK |
| `res/layout/widget_custom_xml.xml` + `drawable/bg_custom_*` (mới) | Layout widget riêng của app, SDK không biết gì về nó |
| `MainActivity.kt` (viết lại) | Demo Compose: sửa title/message/streak, 2 nút **"Thêm widget XML/Compose"** (pin flow), **Switch bật/tắt UI tùy chỉnh** (persist qua prefs), nút cập nhật nội dung, đếm widget theo loại + log sự kiện realtime, xử lý `consumeClickEvent` ở cả `onCreate` và `onNewIntent` |

## 6. Giới hạn đã biết (trung thực)

1. **User bấm Cancel dialog pin → không có callback.** Đây là giới hạn của Android (chỉ có success callback). Workaround: poll `getActiveWidgetCount()` khi app quay lại foreground.
2. **`onWidgetAdded`/`onWidgetRemoved` chỉ nhận được khi process app còn sống** tại thời điểm đó (listener là in-memory). Vì đăng ký ở `Application.onCreate` nên thực tế luôn nhận được — launcher broadcast sẽ tự khởi động process app.
3. Một số OEM launcher không hỗ trợ pin widget → `requestPinWidget` trả `false`, app nên hướng dẫn user thêm thủ công (demo đã có toast).
4. Widget chỉ 1 cỡ 3x2 (resizable). Muốn nhiều size/layout khác nhau thì mở rộng widget-info xml + renderer.
5. Chưa có scheduled update (WorkManager) — nội dung chỉ đổi khi app gọi `updateWidget`. Với use-case retention (streak reset nửa đêm, daily reminder) đây là bước mở rộng hợp lý tiếp theo.
6. **Click event từ widget Compose có `widgetId = INVALID_APPWIDGET_ID`** (Glance không expose int id rẻ tiền) — `source`/`deepLink`/`widgetType` vẫn đầy đủ.
7. Renderer/composeContent là in-memory — **bắt buộc đăng ký trong `Application.onCreate`** (như demo), nếu đăng ký muộn thì lần render đầu sau cold-start sẽ dùng UI mặc định.
8. SDK giờ kéo theo Glance + Compose runtime. App XML thuần vẫn dùng bình thường (không cần compose compiler nếu không viết `setComposeContent`), nhưng APK nặng thêm; nếu cần bản siêu nhẹ thì tách flavor `sdk-widget-core` (XML only) sau.
9. Giới hạn 1-widget chỉ chặn được đường `requestPinWidget` của SDK. Thêm thủ công từ widget picker của launcher thì không chặn được (giới hạn của Android) — nếu cần, có thể detect >1 instance trong `onUpdate` và render thông báo "widget trùng" thay vì nội dung.

## 7. Cách test nhanh

```bash
./gradlew :app:installDebug
```
1. Mở app → ấn **"Thêm widget XML"** và **"Thêm widget Compose"** → dialog hệ thống hiện → đồng ý từng cái.
2. Log trong app hiện `pin accepted` + `Widget XML/COMPOSE đã được thêm`.
3. Về Home, ấn vào thân widget hoặc nút CTA → app mở, toast + log `source=BODY/CTA, widgetType=..., deepLink=...`.
4. Đổi title/streak trong app → ấn **"Cập nhật nội dung widget"** → cả 2 widget ngoài Home đổi ngay.
5. Bật switch **"UI tùy chỉnh từ app"** → widget XML chuyển sang layout tím của app, widget Compose chuyển sang UI xanh lá của app; tắt switch → về UI cam mặc định của SDK.
6. Kill app (swipe khỏi recents) → ấn widget → app cold-start vẫn nhận đủ event + widget vẫn giữ đúng UI tùy chỉnh (đăng ký lại ở Application).

## 8. Bước tiếp theo gợi ý

- WorkManager cập nhật widget định kỳ (streak decay, daily message) — giá trị retention lớn nhất.
- Thêm 2-3 kích cỡ widget (2x1 compact, 4x2 full).
- Bắn analytics event (Firebase) từ các callback để đo funnel: pin shown → accepted → clicked → opened.
- Publish AAR lên maven nội bộ Apero để các app khác dùng chung.
