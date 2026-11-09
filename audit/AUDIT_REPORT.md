# AUDIT REPORT — AZ Tube 1.0

**Ngày audit:** 2026-11-09
**Phiên bản kiểm tra:** commit `d344b73` trên `main` (repo `diennguyendhv/AZ-Tube`), versionName 1.0.
**Thiết bị test:**
- Giả lập MuMu (Android 15, x86_64)
- Pixel 7a (Android 16), chỉ đọc số đo RAM, không thao tác
**Phạm vi:** kiến trúc, vòng đời, quản lý player, coroutine, lưu trữ dữ liệu, điều khoản dịch vụ YouTube.
**Báo cáo liên quan:**
- [`SECURITY_REPORT.md`](SECURITY_REPORT.md)
- [`LICENSE_REPORT.md`](LICENSE_REPORT.md)
- [`PERFORMANCE_REPORT.md`](PERFORMANCE_REPORT.md)
- [`RECOMMENDATIONS.md`](RECOMMENDATIONS.md)

**Thay đổi mã trong quá trình audit:** các phát hiện trong báo cáo dựa trên commit `d344b73`. Giữa chừng, theo yêu cầu trực tiếp của người dùng ("Sửa ngay"), có 3 thay đổi tính năng nhỏ được làm sau commit này:
1. Mặc định **bật** 3 công tắc: Lịch sử xem, Lịch sử tìm kiếm, Đề xuất (`Store.kt`).
2. Video đề xuất và video tự phát tiếp **chỉ lấy từ kênh đang phát** (`Repo.sameChannelVideos`, `StreamResolver.manifestFor`). Danh sách video của kênh được lưu tạm 30 phút, nên không tăng số request so với trước.

3. Thêm mục **"Không đề xuất kênh này"** trong menu video (`Blocked.kt`). Video của kênh bị chặn được ẩn ở mọi danh sách và không được tự phát. Bỏ chặn trong tab Bạn. Danh sách lưu ở khoá `blocked_channels` trong SharedPreferences.

Ba thay đổi này không ảnh hưởng tới các phát hiện ARCH, SEC, L, TOS. Bài đo 30 phút chạy nền được đo trên bản `d344b73`, trước khi cài bản mới.

---

## 1. Tổng quan dự án

| Hạng mục | Giá trị |
|---|---|
| Ngôn ngữ / UI | Kotlin, Jetpack Compose, Material 3 |
| Phát media | Media3 1.11.1 (ExoPlayer, DASH, MediaSessionService, MediaController) |
| Nguồn dữ liệu | NewPipeExtractor v0.27.0, đọc trực tiếp trang và API nội bộ của YouTube, không qua API chính thức |
| Lưu trữ | SharedPreferences `settings`, giá trị là chuỗi JSON. **Không dùng Room.** |
| Module | 1 module (`:app`), 1 package `com.az.tube`, 16 file, 3.323 dòng |
| Test tự động | **Không có** (không có thư mục `src/test` hay `src/androidTest`) |
| SDK | minSdk 26, targetSdk 36, compileSdk 37 |

### Sơ đồ phụ thuộc giữa các thành phần
```
MainActivity ──(MediaController)──► PlaybackService ──► ExoPlayer ──► ResolvingDataSource
     │                                    │                               │
     ▼                                    │                       StreamResolver/InfoCache (object)
AppViewModel ──► Repo (object) ──► NewPipeExtractor ──► DownloaderImpl/Net (OkHttp, object)
     │                                                                      ▲
     ▼                                                                      │
   Store (object, SharedPreferences)            YoutubeDataSource (googlevideo.com) ─┘
```

---

## 2. Đánh giá kiến trúc

### 2.1 Cấu trúc package
Toàn bộ code nằm trong một package phẳng `com.az.tube`. Phân tách theo file:
- **Dữ liệu:** `Repo`, `Store`, `Models`, `Media`, `DownloaderImpl`, `YoutubeDataSource`
- **Dịch vụ:** `PlaybackService`
- **UI:** `AzApp`, `Browse`, `Watch`, `Components`, `Theme`, `Icons`
- **Trạng thái:** `AppViewModel`

Với quy mô 3.300 dòng thì vẫn đọc được. Nhưng ranh giới giữa các lớp chỉ dựa vào quy ước tên file, không có gì bắt buộc.

### 2.2 MVVM và Repository
- **Có:** một `AppViewModel` (AndroidViewModel) giữ trạng thái UI bằng Compose state. UI gọi hàm của ViewModel. Dữ liệu mạng đi qua `Repo`.
- **Lệch khỏi chuẩn:**
  - `Repo`, `Store`, `InfoCache`, `StreamResolver`, `Net`, `Media` đều là `object` (singleton toàn cục). Không có interface, không inject được, nên không thể viết unit test khi không có mạng.
  - `AppViewModel` gánh quá nhiều việc: điều hướng (back stack), trang chủ, tìm kiếm, gợi ý, kênh đăng ký, lịch sử, chi tiết video, cài đặt. Tổng cộng 341 dòng.
  - UI gọi thẳng `Store` để ghi cài đặt, bỏ qua ViewModel (`Browse.kt`: `Store.audioOnly = it`, `Store.autoplay = it`; `Watch.kt`). Vì vậy có hai nguồn sự thật cho cùng một giá trị.
  - Điều hướng tự viết bằng `mutableStateListOf<Route>`, không lưu qua `SavedStateHandle`. Khi hệ điều hành thu hồi tiến trình rồi mở lại, người dùng luôn quay về Trang chủ.

### 2.3 Vòng đời Activity và Service
| Điểm kiểm tra | Kết quả |
|---|---|
| `MediaController` tạo ở `onStart`, giải phóng ở `onStop` (`MainActivity.kt`, dòng 65–82) | Đúng. Không giữ kết nối khi app ẩn. |
| Tắt track video khi `onStop` để tiết kiệm băng thông | Đúng (`MainActivity.kt`, dòng 77) |
| `PlayerView` gỡ player khi Compose huỷ view (`onRelease = { it.player = null }`, `Watch.kt`, dòng 222) | Đúng |
| `PlayerState` gỡ listener qua `DisposableEffect` (`AzApp.kt`, dòng 60) | Đúng |
| `PlaybackService.onDestroy` giải phóng player và session, xoá callback tĩnh (`PlaybackService.kt`, dòng 70–78) | Đúng |
| Vuốt app khỏi danh sách gần đây (`onTaskRemoved`) | Đang phát thì tiếp tục, đang dừng thì `stopSelf`. Đúng ý đồ. |
| Khôi phục phát nhạc (`onPlaybackResumption`) | **Chưa có** (xem ARCH-3) |
| Picture-in-Picture | Dùng `onUserLeaveHint`. Chưa dùng `setAutoEnterEnabled` (Android 12+), nên vuốt về Home bằng cử chỉ có thể không vào PiP. |

### 2.4 Quản lý ExoPlayer và MediaSession
- Chỉ có một `ExoPlayer`, nằm trong service. UI chỉ giữ `MediaController`. Đây là mô hình đúng của Media3, nên không có tình trạng hai player chạy song song.
- `AudioAttributes(USAGE_MEDIA)` kèm `handleAudioFocus = true`, `setHandleAudioBecomingNoisy(true)`, `WAKE_MODE_NETWORK`. Đúng cho phát nền.
- Link luồng được lấy khi bắt đầu phát, qua `ResolvingDataSource`. Bước này chạy trên thread loader của ExoPlayer, không chặn UI.
- Xử lý lỗi tự viết trong `onPlayerError`. Xem ARCH-1 và ARCH-2.

### 2.5 Memory leak
Phân tích tĩnh **không thấy leak Activity hay Context**:
- Không object toàn cục nào giữ Activity.
- `StreamResolver.onResolved` giữ lambda tham chiếu tới service, nhưng được đặt `null` trong `onDestroy`.

Có một số **bộ nhớ tăng dần không giới hạn** (không phải leak theo nghĩa giữ Activity), xem ARCH-5. Số đo RAM thực tế nằm trong `PERFORMANCE_REPORT.md`.

### 2.6 Coroutine và xử lý lỗi
- Dùng `viewModelScope` và `withContext(Dispatchers.IO)`. Không có `GlobalScope` hay `runBlocking`.
- `CancellationException` được ném lại đúng cách trong `Paged` và `loadDetails`.
- Xem thêm ARCH-4, ARCH-6 và ARCH-7.

---

## 3. Danh sách vấn đề (kiến trúc, độ ổn định, dữ liệu)

> Các vấn đề bảo mật nằm trong `SECURITY_REPORT.md` (SEC-x), giấy phép trong `LICENSE_REPORT.md` (L-x), hiệu năng trong `PERFORMANCE_REPORT.md` (PERF-x).

### ARCH-1 · HIGH — Mất mạng: lỗi được xử lý bằng cách bỏ qua bài, thay vì chờ có mạng lại
- **Vị trí:** `PlaybackService.kt`, dòng 107–126 (`onPlayerError`)
- **Mô tả:** khi ExoPlayer báo lỗi (đã hết số lần thử lại của chính nó), app gọi `prepare()` lại ngay một lần. Nếu vẫn lỗi thì `seekToNextMediaItem()`. Logic không phân biệt lỗi mạng (`ERROR_CODE_IO_NETWORK_CONNECTION_FAILED`, `…TIMEOUT`) với lỗi nội dung (video bị chặn, không có luồng).
- **Ảnh hưởng:**
  - Người chạy bộ vào vùng mất sóng lâu hơn bộ đệm (khoảng 50 giây) thì app thử lại ngay trong lúc vẫn chưa có mạng, sau đó bỏ qua bài.
  - Bài tiếp theo cũng lỗi, nên app có thể trượt qua cả hàng chờ rồi dừng hẳn.
  - Có mạng lại thì app không tự phát tiếp.
  - Kết quả đo thực tế: xem mục 6 và `PERFORMANCE_REPORT.md`.
- **Khắc phục:**
  - Với nhóm lỗi mạng: không bỏ bài. Giữ vị trí hiện tại, đăng ký `ConnectivityManager.NetworkCallback` và gọi `prepare()` khi có mạng lại (có thể dùng backoff).
  - Chỉ bỏ bài với lỗi nội dung (HTTP 403/410, không có luồng phù hợp).
  - Có thể tăng `LoadErrorHandlingPolicy` (số lần thử và thời gian giữa các lần).

### ARCH-2 · MEDIUM — Bộ đếm thử lại (`retries`) không bao giờ được xoá
- **Vị trí:** `PlaybackService.kt`, dòng 28, 109–117
- **Mô tả:** `retries[id]` chỉ tăng, không reset khi phát thành công hay khi chuyển bài.
- **Ảnh hưởng:** link luồng YouTube hết hạn sau vài giờ. Nếu một video đã dùng hết lượt thử lại (ví dụ lúc mất mạng ở ARCH-1), thì lần sau link hết hạn (tạm dừng lâu rồi phát tiếp) app sẽ không lấy link mới nữa mà bỏ qua hoặc báo lỗi ngay.
- **Khắc phục:** xoá `retries[id]` khi player vào `STATE_READY` hoặc khi chuyển sang bài khác. Giới hạn kích thước map.

### ARCH-3 · MEDIUM — Không khôi phục hàng chờ sau khi tiến trình bị huỷ
- **Vị trí:** `PlaybackService.kt`, dòng 92–99 (`SessionCallback` không có `onPlaybackResumption`). Hàng chờ chỉ nằm trong bộ nhớ.
- **Mô tả:**
  - Khi app tạm dừng lâu, Media3 bỏ trạng thái foreground. Hệ điều hành có thể huỷ tiến trình bất cứ lúc nào.
  - Hàng chờ và vị trí đang phát không được lưu, nên mất hết.
  - Nút "phát" trên tai nghe Bluetooth hay widget hệ thống sau đó không làm gì, vì Media3 cần `onPlaybackResumption` để khôi phục.
- **Ảnh hưởng:** người dùng phải mở app và tìm lại bài. Kết quả đo: mục 6.
- **Khắc phục:** lưu hàng chờ (mediaId, tiêu đề, ảnh), vị trí và chỉ số bài vào storage khi chuyển bài hoặc tạm dừng. Hiện thực `onPlaybackResumption` để trả lại danh sách đó.

### ARCH-4 · MEDIUM — Bảng tin kênh đăng ký gửi rất nhiều request mỗi lần làm mới
- **Vị trí:** `Repo.kt`, dòng 174–184 (`subscriptionFeed`)
- **Mô tả:**
  - Mỗi kênh tốn 2 request (`ChannelInfo` và tab Videos), tối đa 30 kênh, 4 request song song.
  - Tức là tới 60 request mỗi lần mở hoặc kéo làm mới Trang chủ (khi tắt đề xuất) hay tab Kênh đăng ký.
  - Kênh nào lỗi thì bị bỏ qua lặng lẽ (`runCatching … getOrDefault(emptyList())`).
- **Ảnh hưởng:**
  - Dễ bị YouTube giới hạn (HTTP 429, `ReCaptchaException`). Khi đó mọi chức năng đều lỗi một thời gian.
  - Tốn pin và dữ liệu.
  - Người dùng không biết danh sách đang thiếu kênh nào.
  - Đây cũng là hành vi "truy cập tự động" (xem mục 4).
- **Khắc phục:**
  - Lưu kết quả bảng tin tạm thời (ví dụ 30 phút) và chỉ tải lại khi người dùng kéo làm mới.
  - Hiển thị số kênh tải lỗi.
  - Giảm song song xuống 2.
  - Về lâu dài: dùng nguồn được cấp phép (xem `RECOMMENDATIONS.md`).

### ARCH-5 · LOW — Bộ nhớ đệm tăng không giới hạn
- **Vị trí:**
  - `Media.kt`, dòng 85, 95 (`StreamResolver.related`, ConcurrentHashMap không bao giờ xoá)
  - `PlaybackService.kt`, dòng 27 (`played` HashSet)
  - `AppViewModel.kt`, dòng 132 (`homeCache`) và 228 (`pages`): giữ mọi trang tìm kiếm, kênh, playlist đã mở kèm toàn bộ kết quả đã cuộn, suốt vòng đời ViewModel
  - `Media.kt`, dòng 70: `InfoCache` xoá 20 mục tuỳ ý khi vượt 60 mục, không theo thứ tự cũ nhất
- **Ảnh hưởng:** RAM tăng dần trong phiên dài (nhiều giờ, hàng trăm lượt tìm kiếm). Mỗi mục nhỏ (chuỗi), nên ảnh hưởng thấp. Số đo: `PERFORMANCE_REPORT.md`.
- **Khắc phục:** dùng `LruCache` hoặc giới hạn kích thước cho `related`, `pages`, `played`. Xoá `pages[route]` khi route bị pop khỏi back stack.

### ARCH-6 · LOW — Lỗi bị nuốt, người dùng không biết
- **Vị trí:**
  - `AppViewModel.kt`, dòng 84–85: `Paged.loadMore` lỗi thì đặt `more = null`. Danh sách âm thầm dừng tải thêm.
  - `Repo.kt`, dòng 80: gợi ý tìm kiếm lỗi trả về rỗng.
  - `Store.kt`, dòng 60–64: JSON lỗi trả về rỗng. Xem DATA-2, mức MEDIUM.
- **Khắc phục:** hiện hàng "Không tải được thêm · Thử lại" ở cuối danh sách. Ghi lỗi trong build debug.

### ARCH-7 · LOW — Gọi mạng có thể chạy trên main thread
- **Vị trí:** `AppViewModel.kt`, dòng 209: `InfoCache.get(url, …)` nằm trong `viewModelScope.launch`, tức chạy trên Main.
- **Mô tả:** hiện an toàn vì `Repo.details(url)` ở dòng trước đã nạp cache. Nhưng nếu thứ tự hoặc thời hạn cache thay đổi, sẽ gặp `NetworkOnMainThreadException` và app crash.
- **Khắc phục:** lấy ảnh thumbnail trong `Repo.details` (trả về luôn trong `Details`), hoặc bọc bằng `withContext(Dispatchers.IO)`.

### ARCH-8 · LOW — `future.get()` không bắt ngoại lệ
- **Vị trí:** `MainActivity.kt`, dòng 70–72
- **Ảnh hưởng:** nếu kết nối tới `PlaybackService` thất bại (service crash khi khởi tạo), `get()` ném `ExecutionException` và app crash ngay khi mở.
- **Khắc phục:** bọc `try/catch`, hiển thị thông báo và thử kết nối lại.

### ARCH-9 · MEDIUM — Không có test tự động, ranh giới lớp yếu
- **Vị trí:** toàn dự án
- **Ảnh hưởng:**
  - Mỗi lần sửa (như việc vừa xoá rồi thêm lại tính năng lịch sử và đề xuất) chỉ kiểm được bằng tay trên giả lập.
  - Khả năng hỏng ngầm cao khi YouTube đổi định dạng và NewPipeExtractor phải nâng phiên bản.
- **Khắc phục:**
  - Tách interface cho `Repo` và `Store`.
  - Viết unit test cho logic thuần: `Paged`, `mix`, `formatDuration`, `compactNumber`, `timeAgo`, `Store` JSON, logic 3 công tắc quyền riêng tư.
  - Thêm một test instrumented cho `PlaybackService` (phát, mất mạng, khôi phục).

---

## 4. Điều khoản dịch vụ YouTube

**Văn bản đối chiếu** (đọc ngày 2026-11-09):
- **Điều khoản dịch vụ YouTube** (`youtube.com/t/terms`), bản tiếng Việt, "có hiệu lực từ 5 tháng 1, 2022", mục "Quyền và hạn chế".
- **YouTube API Services – Developer Policies** (`developers.google.com/youtube/terms/developer-policies`). Chính sách này áp dụng cho ứng dụng dùng YouTube API Services. AZ Tube **không** dùng API chính thức, nên chỉ đưa vào để so sánh với yêu cầu của Google đối với ứng dụng bên thứ ba **được cấp phép**.

> Mục này chỉ **đánh dấu rủi ro** dựa trên câu chữ của điều khoản. Đây **không phải** kết luận pháp lý. Theo yêu cầu audit, báo cáo **không** đề xuất cách né giới hạn, không thay đổi cơ chế xác thực, và không tối ưu thêm việc bỏ qua quảng cáo.

| # | Mức rủi ro | Thành phần | Cách app đang làm | Điều khoản liên quan |
|---|---|---|---|---|
| TOS-1 | **CRITICAL** | `Media.kt` (`StreamResolver.manifestFor`, dòng 90–122); `YoutubeDataSource.kt` | Lấy thẳng link file âm thanh và hình ảnh từ googlevideo.com, tự dựng DASH và phát bằng ExoPlayer. Không dùng trình phát YouTube, nên **không có quảng cáo**. | ToS, Quyền và hạn chế, mục 1: không được truy cập, sao chép, tải xuống… Nội dung "trừ khi được cho phép rõ ràng". Mục 2: không được tìm cách tránh né, vô hiệu hoá hoặc can thiệp vào bất kỳ phần nào của Dịch vụ. Developer Policies III.I.5: không được chặn quảng cáo do YouTube phân phát. III.I.6: không được chặn bất kỳ phần nào của trình phát YouTube. |
| TOS-2 | **CRITICAL** | `YoutubeDataSource.kt`, dòng 58–84; `DownloaderImpl.kt`, dòng 21–29 | Gửi request giống ứng dụng chính thức (POST kèm tham số `range` và `rn`). Giả User-Agent (Firefox desktop, visionOS) để không bị giới hạn tốc độ. | ToS, mục 2: tìm cách tránh né hoặc can thiệp vào tính năng hạn chế sử dụng Nội dung. **Theo yêu cầu audit: không tối ưu thêm thành phần này.** |
| TOS-3 | **HIGH** | `Repo.kt` (search, kiosk, channel, playlist, `subscriptionFeed`, `home`); `DownloaderImpl.kt` | Đọc trang và API nội bộ của YouTube bằng chương trình. Riêng `home()` gửi 3–7 lượt tìm kiếm cho một lần mở trang. `subscriptionFeed` gửi tới 60 request. | ToS, mục 3: không được truy cập Dịch vụ bằng bất kỳ phương thức tự động nào (trừ công cụ tìm kiếm công khai theo robots.txt, hoặc có cho phép bằng văn bản). Developer Policies III.I.14: chỉ được dùng YouTube API Services để lấy dữ liệu API. |
| TOS-4 | **HIGH** | `PlaybackService.kt` (phát nền); `AzApp.kt`, dòng 129 và `MainActivity.kt`, dòng 77 (tắt track hình, chế độ "Chỉ nghe") | Phát khi màn hình tắt hoặc app ẩn. Tách riêng phần âm thanh của video. | Developer Policies III.I.9: không tạo tính năng phát nội dung từ trình phát nền. III.I.7: không tách hay cô lập thành phần âm thanh/hình ảnh. Áp dụng cho ứng dụng dùng API. Với người dùng cuối, YouTube cung cấp phát nền qua gói trả phí. |
| TOS-5 | **MEDIUM** | `Store.kt` (lịch sử xem lưu tiêu đề, link, ảnh) | Chỉ lưu **metadata**, không lưu file video hay âm thanh. Không có tính năng tải xuống. | Developer Policies III.E.1: không lưu bản sao nội dung nghe nhìn. App **không** làm việc này. Rủi ro thấp hơn các mục trên. |
| TOS-6 | **LOW** | `Components.kt` (logo), `strings.xml` | Logo và màu đã đổi sang mẫu riêng. Vẫn dùng chữ "YouTube" để mô tả nguồn link. | Hướng dẫn thương hiệu của YouTube. Dùng tên để mô tả nguồn thường chấp nhận được, nhưng cần ghi "không liên kết với YouTube". |

**Nhận định:** TOS-1, TOS-2 và TOS-3 là **bản chất** của cách app hoạt động hiện tại. Không thể sửa cho tuân thủ bằng một bản vá nhỏ. Tuân thủ điều khoản đòi hỏi **thay đổi nguồn nội dung hoặc cách phát**. Các phương án được cấp phép nằm trong `RECOMMENDATIONS.md`, mục 2.

---

## 5. Dữ liệu

### 5.1 Hệ thống lưu trữ hiện tại
- **Không dùng Room hay SQLite.** Toàn bộ nằm trong một file `shared_prefs/settings.xml` (`Store.kt`).

| Khoá | Kiểu | Giới hạn | Ghi chú |
|---|---|---|---|
| `subscriptions` | Mảng JSON `ChannelItem` | Không giới hạn | Dữ liệu quan trọng nhất của người dùng |
| `watch_history` | Mảng JSON `VideoItem` | 200 | Chỉ ghi khi công tắc "Lịch sử xem" bật |
| `history` | Chuỗi, ngăn cách bằng `\n` | 20 | Lịch sử tìm kiếm. Chỉ ghi khi công tắc bật |
| `audio_only`, `max_quality`, `autoplay`, `*_on` | boolean/int | | Cài đặt |

- **Yêu thích:** chưa có tính năng này, nên không có dữ liệu để kiểm tra.
- Mọi lần ghi dùng `sp.edit { }`, tức `apply()`: ghi vào bộ nhớ ngay, ghi xuống đĩa bất đồng bộ.

### 5.2 Vấn đề dữ liệu

#### DATA-1 · LOW — Lịch sử tìm kiếm lưu bằng ký tự xuống dòng
- **Vị trí:** `Store.kt`, dòng 49–51
- **Mô tả:** từ khoá chứa `\n` (từ văn bản chia sẻ qua `ACTION_SEND`) sẽ bị tách thành nhiều mục khi đọc lại.
- **Khắc phục:** lưu dạng mảng JSON như hai danh sách còn lại, hoặc thay `\n` bằng dấu cách trước khi lưu.

#### DATA-2 · MEDIUM — JSON hỏng làm mất toàn bộ kênh đăng ký mà không báo
- **Vị trí:** `Store.kt`, dòng 60–64 (`readArray` lỗi thì trả về rỗng); `AppViewModel.kt`, `toggleSubscribe` ghi đè toàn bộ danh sách
- **Mô tả:** nếu chuỗi `subscriptions` bị hỏng (ghi dở, lỗi phiên bản), app đọc ra danh sách rỗng. Lần đăng ký kênh tiếp theo sẽ **ghi đè** danh sách rỗng cộng một kênh, và dữ liệu cũ mất vĩnh viễn.
- **Khắc phục:**
  - Đọc lỗi thì giữ nguyên chuỗi gốc (đổi tên sang khoá `subscriptions_corrupt_<time>`) trước khi ghi đè.
  - Đọc từng phần tử, bỏ phần tử lỗi thay vì bỏ cả mảng.
  - Về lâu dài: chuyển sang Room hoặc DataStore có schema version.

#### DATA-3 · LOW — Ghi lại toàn bộ danh sách mỗi lần thay đổi, đọc JSON trên main thread
- **Vị trí:**
  - `Store.kt`: `watchHistory` (200 mục) được serialize lại mỗi khi chuyển video
  - `AppViewModel.kt`, dòng 259: đọc và parse khi khởi tạo ViewModel trên Main
- **Ảnh hưởng:** nhỏ ở quy mô hiện tại, chỉ vài chục KB. Nhưng không mở rộng được nếu thêm "Yêu thích" hay tăng giới hạn.
- **Khắc phục:** khi dữ liệu lớn dần, chuyển sang Room (bảng `subscription`, `watch_history`, `search_history`), có index và migration.

### 5.3 Khả năng khôi phục sau khi app bị đóng đột ngột
Kết quả test thực tế: xem mục 6.

---

## 6. Kết quả kiểm tra thực tế (MuMu, Android 15)

Chi tiết số đo trong [`PERFORMANCE_REPORT.md`](PERFORMANCE_REPORT.md).

| Bài kiểm tra | Kết quả |
|---|---|
| Phát nền 30 phút, tắt màn hình | **Đạt.** Phát liên tục, RAM ổn định 115–131 MB, không thấy rò rỉ. |
| CPU khi phát nền | **Có vấn đề** (PERF-1, MEDIUM). Từ phút 9, video được giải mã lại dù màn hình tắt. CPU tăng từ ~2% lên ~12% một nhân. |
| Mất mạng 2 phút, có mạng lại | **Không đạt** (ARCH-1, HIGH, đã xác nhận). Hết bộ đệm thì bỏ qua bài, rồi dừng ở trạng thái lỗi. Không tự phục hồi khi có mạng. |
| Cuộc gọi đến | Chưa đo. Mã có xử lý audio focus (`PlaybackService.kt:34-40`). |
| Hệ điều hành thu hồi app | Chưa đo. Phân tích mã: hàng chờ bị mất, nút phát trên tai nghe không phát tiếp (ARCH-3). |
| Đóng đột ngột, dữ liệu | Chưa đo. Phân tích mã: rủi ro mất thay đổi cuối thấp; JSON hỏng xoá danh sách (DATA-2). |
| Đề xuất cùng kênh (sau thay đổi) | **Đạt.** Danh sách liên quan và 2 lần tự chuyển bài đều cùng kênh. |

---

## 7. Kết luận
### 7.1. Thử nghiệm cá nhân: **chấp nhận được, kèm điều kiện**
- Không phát hiện lỗ hổng bảo mật nghiêm trọng (SECURITY_REPORT: không có CRITICAL).
- App không xin quyền nhạy cảm, không gửi dữ liệu người dùng ra ngoài ngoài các request tới YouTube.
- Phát nền ổn định trong 30 phút, không rò rỉ RAM.
- Điều kiện:
  - Chấp nhận rằng khi mất mạng lúc chạy bộ, app sẽ dừng và phải mở lên bấm phát lại (ARCH-1).
  - Chấp nhận tốn pin hơn cần thiết khi phát nền (PERF-1).
  - Nên sửa ARCH-1 và PERF-1 trước khi dùng thường xuyên.

### 7.2. Phân phối cho người khác (kể cả người quen qua diennguyenaz.com): **chưa sẵn sàng**
- Có 2 vấn đề CRITICAL về điều khoản dịch vụ YouTube (TOS-1, TOS-2). Đây là rủi ro do **cách lấy và phát video**, không sửa được bằng một thay đổi nhỏ.
- Chưa đáp ứng nghĩa vụ giấy phép GPL-3.0 khi phân phối (L-1, L-2): mã nguồn đang private, chưa có file LICENSE.
- APK ký bằng debug key (SEC-1).
- Báo cáo này **không phải ý kiến pháp lý**. Nếu muốn phân phối, nên chọn một phương án hợp lệ ở `RECOMMENDATIONS.md` mục 2 và hỏi ý kiến người có chuyên môn pháp lý.

### 7.3. Việc nên làm tiếp theo
1. Sửa ARCH-1 (mất mạng) và PERF-1 (video giải mã khi tắt màn hình).
2. Đo bổ sung trên Pixel: cuộc gọi, thu hồi app, đóng đột ngột.
3. Quyết định hướng đi về điều khoản dịch vụ trước khi đưa APK cho bất kỳ ai.
