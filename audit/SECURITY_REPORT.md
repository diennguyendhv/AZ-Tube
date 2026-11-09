# SECURITY REPORT — AZ Tube 1.0

**Ngày audit:** 2026-11-09
**Phiên bản kiểm tra:** commit `d344b73`. File `AZTube.apk` được build lúc 07:47 cùng ngày.

**Phương pháp:**
- Đọc toàn bộ mã nguồn (16 file Kotlin, 3.323 dòng).
- Đọc manifest đã merge bên trong APK bằng `aapt2 dump xmltree`.
- Kiểm tra chữ ký bằng `apksigner verify --print-certs`.
- Tìm mẫu rủi ro (log, `http://`, `!!`, `catch` rỗng, `GlobalScope`, `runBlocking`).
- Kiểm tra thư viện qua cây phụ thuộc Gradle.

**Không làm:**
- Phân tích động bằng proxy hoặc MITM.
- Fuzz intent.
- Quét CVE tự động.

**Không có thay đổi mã nguồn nào trong quá trình audit.**

---

## 1. Tóm tắt

| Mức độ | Số lượng |
|---|---|
| CRITICAL | 0 |
| HIGH | 1 (chỉ khi phân phối) |
| MEDIUM | 3 |
| LOW | 6 |
| Đạt / thông tin | 6 |

Với mục đích **thử nghiệm cá nhân**, không thấy lỗ hổng nào cho phép app khác hoặc kẻ tấn công trên mạng đọc dữ liệu, chạy mã, hay chiếm quyền app. Các điểm cần xử lý chủ yếu liên quan tới **phân phối** (khóa ký) và **quyền riêng tư** (dịch vụ phát nhạc mở cho mọi app, dữ liệu được sao lưu lên cloud).

---

## 2. Các vấn đề

### SEC-1 · HIGH (khi phân phối) / LOW (dùng cá nhân) — APK ký bằng khóa debug
- **Vị trí:** `app/build.gradle.kts`, dòng 20–23 (`signingConfig = signingConfigs.getByName("debug")`)
- **Bằng chứng:**
  - `Signer #1 certificate DN: C=US, O=Android, CN=Android Debug`
  - Chứng chỉ SHA-256: `ac61cdf5…ad5609`
  - Keystore `~/.android/debug.keystore` (mật khẩu mặc định `android`), hiệu lực đến 2056
  - Chỉ ký theo scheme v2
- **Mô tả:** khóa debug được Android SDK tự tạo, mật khẩu ai cũng biết, nằm không bảo vệ trong thư mục người dùng. Khóa không được sao lưu.
- **Ảnh hưởng:**
  1. Mất máy tính hoặc cài lại Android Studio thì khóa đổi. Mọi người đã cài sẽ phải gỡ app (mất toàn bộ kênh đăng ký và lịch sử) mới cài được bản mới.
  2. Ai lấy được file keystore có thể ký một APK giả, và APK đó sẽ cài đè lên bản thật trên máy người dùng.
  3. Play Protect có thể cảnh báo khi cài.
- **Khắc phục:**
  - Tạo keystore release riêng bằng `keytool -genkeypair`, dùng mật khẩu mạnh.
  - Không đưa keystore vào git. Sao lưu ra 2 nơi offline.
  - Khai báo `signingConfigs.release`, đọc mật khẩu từ `local.properties` hoặc biến môi trường.
  - Bật thêm ký v3 để sau này có thể xoay khóa (key rotation).

### SEC-2 · MEDIUM — `PlaybackService` exported, không giới hạn app nào được kết nối
- **Vị trí:**
  - `AndroidManifest.xml`: `<service android:name=".PlaybackService" android:exported="true">`
  - `PlaybackService.kt`, dòng 60 (`onGetSession` trả session cho mọi controller) và dòng 92–99 (`SessionCallback` không override `onConnect`)
- **Mô tả:** Media3 cần exported để thông báo, màn hình khóa, tai nghe và Android Auto điều khiển được. Nhưng hiện mọi app trên máy đều kết nối được bằng `MediaController`.
- **Ảnh hưởng:**
  - Một app bất kỳ có thể **đọc video đang phát** (tiêu đề, kênh, link) và theo dõi thói quen xem.
  - App đó cũng có thể phát, dừng, tua, xóa hàng chờ.
  - App đó có thể thêm `mediaId` tùy ý. App sẽ gọi `StreamInfo.getInfo(url)` với URL đó. NewPipeExtractor chỉ chấp nhận URL YouTube, nên khả năng dùng để gửi request tới máy chủ khác là thấp.
  - Không lộ dữ liệu lưu trữ.
- **Khắc phục:**
  - Override `MediaSession.Callback.onConnect` để chỉ chấp nhận: chính app, controller của System UI và thông báo (`session.isMediaNotificationController`, `controller.isTrusted`), và gói Android Auto nếu muốn hỗ trợ.
  - Với các controller khác: từ chối, hoặc dùng `AcceptedResultBuilder` chỉ cho lệnh phát/dừng, không cho đọc hàng chờ hay thêm media.

### SEC-3 · MEDIUM — Phụ thuộc lấy từ JitPack theo tag/commit, không xác minh checksum
- **Vị trí:**
  - `settings.gradle.kts`: `maven("https://jitpack.io")`
  - `app/build.gradle.kts`: `NewPipeExtractor:v0.27.0`
  - Kéo theo `nanojson:e9d656dd…`
- **Mô tả:** JitPack build artifact từ GitHub khi có yêu cầu. Dự án chưa bật Gradle dependency verification (`gradle/verification-metadata.xml`). Nếu tag bị đẩy lại hoặc JitPack bị xâm nhập, máy build có thể nhận mã khác mà không hề biết.
- **Ảnh hưởng:** rủi ro chuỗi cung ứng. Mã độc chạy với đầy đủ quyền của app (Internet, chạy nền).
- **Khắc phục:**
  - Chạy `./gradlew --write-verification-metadata sha256 help` và commit file sinh ra.
  - Giới hạn JitPack chỉ cho nhóm `com.github.TeamNewPipe` bằng `content { includeGroup(...) }`.
  - Xem changelog trước khi nâng phiên bản.

### SEC-4 · MEDIUM — Dữ liệu cá nhân được sao lưu tự động (`allowBackup` mặc định = true)
- **Vị trí:** `AndroidManifest.xml`, thẻ `<application>`: không khai báo `allowBackup`, `fullBackupContent` hay `dataExtractionRules`. APK đã build cũng không có các thuộc tính này.
- **Mô tả:** SharedPreferences `settings` chứa lịch sử xem (tối đa 200 video), lịch sử tìm kiếm (20 từ khóa) và kênh đăng ký. Tất cả được đưa vào Google cloud backup và tính năng chuyển dữ liệu sang máy mới.
- **Ảnh hưởng:** lịch sử nằm trên cloud của Google, ngoài kiểm soát của người dùng, kể cả khi họ nghĩ đã xóa trong app (bản sao lưu cũ vẫn còn).
- **Khắc phục:**
  - Thêm `android:dataExtractionRules` (Android 12+) và `android:fullBackupContent` (Android 11 trở xuống).
  - Chỉ cho sao lưu kênh đăng ký và cài đặt. Loại trừ lịch sử. Muốn vậy cần tách lịch sử sang file SharedPreferences hoặc DataStore riêng.
  - Hoặc đặt `allowBackup="false"` nếu không cần sao lưu.

### SEC-5 · LOW — Thông báo lỗi kỹ thuật hiển thị nguyên văn cho người dùng
- **Vị trí:**
  - `PlaybackService.kt`, dòng 119 và 124: `toast("… ${error.cause?.message …}")`
  - `AppViewModel.kt`, dòng 63–64 (`Paged.error = e.message`)
  - `Watch.kt`, dòng 107 (`PlayerState.error`)
- **Mô tả:** chuỗi lỗi của OkHttp hoặc ExoPlayer có thể chứa tên máy chủ `*.googlevideo.com`, mã lỗi HTTP, hoặc một phần URL.
- **Ảnh hưởng:** lộ thông tin kỹ thuật trên màn hình (ví dụ khi quay hoặc chụp màn hình). Không có log hệ thống nên không lộ qua logcat.
- **Khắc phục:** đổi lỗi sang thông báo tiếng Việt cố định theo nhóm (mất mạng, video bị chặn, lỗi máy chủ). Chi tiết kỹ thuật chỉ ghi khi build debug.

### SEC-6 · LOW — Dữ liệu lưu trữ không mã hóa
- **Vị trí:** `Store.kt` (`getSharedPreferences("settings", MODE_PRIVATE)`)
- **Mô tả:** lịch sử và kênh đăng ký lưu dạng JSON thô trong `/data/data/com.az.tube/shared_prefs/settings.xml`. Thư mục này chỉ app đọc được trên máy chưa root. Bản release không `debuggable`, nên `run-as` bị từ chối (đã kiểm tra trên MuMu).
- **Ảnh hưởng:** chỉ lộ khi máy đã root hoặc qua bản sao lưu (xem SEC-4).
- **Khắc phục:** chưa cần thiết cho thử nghiệm cá nhân. Nếu muốn, dùng DataStore kèm mã hóa Tink. (`EncryptedSharedPreferences` đã ngừng phát triển.)

### SEC-7 · LOW — Không thu gọn và làm rối mã (`isMinifyEnabled = false`)
- **Vị trí:** `app/build.gradle.kts`, dòng 19
- **Ảnh hưởng:** APK lớn hơn (13,9 MB) và dễ dịch ngược. Vì dự án phải công khai mã nguồn theo GPL, đây không phải vấn đề bảo mật đáng kể. Ảnh hưởng chủ yếu là kích thước APK.
- **Khắc phục:** bật R8 cho bản release, viết `proguard-rules.pro` giữ các lớp NewPipeExtractor, Rhino và nanojson (thư viện dùng reflection), rồi test lại toàn bộ.

### SEC-8 · LOW — Chạy JavaScript tải từ youtube.com bằng Rhino
- **Vị trí:** gián tiếp qua NewPipeExtractor (`org.mozilla:rhino:1.8.1`), được gọi khi lấy luồng phát trong `Media.kt`, dòng 91 (`InfoCache.get` → `StreamInfo.getInfo`)
- **Mô tả:** để giải mã chữ ký URL, NewPipeExtractor tải file JavaScript player của YouTube và chạy một phần trong Rhino, bên trong tiến trình app.
- **Ảnh hưởng:** mã từ máy chủ bên ngoài chạy trong app. Rhino được NewPipe dùng ở phạm vi hạn chế và kết nối là HTTPS tới youtube.com, nên rủi ro thấp. Tuy vậy nó vẫn là bề mặt tấn công nếu nguồn JavaScript bị thay đổi.
- **Khắc phục:** giữ NewPipeExtractor ở bản mới (bản vá bảo mật Rhino đi theo). Không tự sửa cơ chế này (xem mục điều khoản dịch vụ trong `AUDIT_REPORT.md`).

### SEC-9 · LOW — Hỏi quyền thông báo mỗi lần mở app
- **Vị trí:** `MainActivity.kt`, dòng 38–42
- **Ảnh hưởng:** chỉ ảnh hưởng trải nghiệm. Android tự ngừng hiện hộp thoại sau 2 lần bị từ chối. Không phải lỗ hổng.
- **Khắc phục:** lưu trạng thái đã hỏi, hoặc chỉ hỏi khi người dùng phát video lần đầu.

### SEC-10 · LOW — Nhận văn bản tùy ý qua `ACTION_SEND`
- **Vị trí:**
  - `AndroidManifest.xml` (intent-filter `SEND text/plain`)
  - `MainActivity.kt`, dòng 55–63
  - `AppViewModel.kt` (`submitSearch`, `openLink`)
- **Mô tả:**
  - Văn bản chia sẻ không chứa URL sẽ thành từ khóa tìm kiếm. Nếu lịch sử tìm kiếm đang bật, từ khóa đó được lưu lại.
  - Văn bản nhiều dòng sẽ làm hỏng định dạng lưu lịch sử, vì lịch sử ngăn cách bằng `\n` (`Store.kt`, dòng 51–53).
  - URL được kiểm tra qua `getLinkTypeByUrl` và lỗi được bắt.
- **Ảnh hưởng:** không thực thi được gì. Tệ nhất là app khác ghi rác vào lịch sử tìm kiếm.
- **Khắc phục:** chỉ nhận văn bản chứa URL YouTube, hoặc hỏi người dùng trước khi tìm kiếm. Thay `\n` trong từ khóa trước khi lưu, hoặc lưu lịch sử dạng mảng JSON.

---

## 3. Các mục đạt yêu cầu

| # | Hạng mục | Kết quả |
|---|---|---|
| OK-1 | **HTTP/HTTPS** | `targetSdk 36` nên cleartext bị chặn mặc định. Mã nguồn không có `http://`, không có `usesCleartextTraffic` hay `networkSecurityConfig` cho phép HTTP. URL ảnh dạng `//…` được chuẩn hóa thành `https:` (`Models.kt`, `pickImage`). Các host gồm youtube.com, googlevideo.com, ytimg.com, đều HTTPS. |
| OK-2 | **Log** | Không có `Log.*`, `println` hay `printStackTrace`. Không có SDK analytics hoặc crash reporting. Không gửi dữ liệu cho bên thứ ba ngoài các máy chủ YouTube và Google cần để lấy nội dung. |
| OK-3 | **Quyền** | Chỉ xin `INTERNET`, `WAKE_LOCK`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `POST_NOTIFICATIONS`. Không có quyền vị trí, danh bạ, micro, camera hay bộ nhớ. Quyền thêm vào khi merge là `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (mức signature). |
| OK-4 | **Component exported** | 5 component exported: `MainActivity` (cần cho launcher và link), `PlaybackService` (xem SEC-2), `BluetoothValidationActivity` (yêu cầu `BLUETOOTH_PRIVILEGED`), `ProfileInstallReceiver` (yêu cầu `DUMP`). `InitializationProvider` không exported. |
| OK-5 | **Debuggable** | APK release không `debuggable`. `run-as com.az.tube` bị từ chối: "package not debuggable". |
| OK-6 | **Tài khoản** | App không đăng nhập, không lưu cookie, token hay mật khẩu. Không có thông tin xác thực nào để lộ. |

---

## 4. Thư viện bên thứ ba (góc độ bảo mật)
- Tất cả đều là bản phát hành gần đây, theo cây phụ thuộc ngày 2026-11-09. Chi tiết xem `LICENSE_REPORT.md`.
- **Chưa** chạy quét CVE tự động (OWASP Dependency-Check, OSV-Scanner). Nên chạy trước mỗi lần phát hành.
- Các thư viện xử lý dữ liệu không tin cậy từ mạng: NewPipeExtractor (HTML/JSON), jsoup (HTML), nanojson (JSON), Rhino (JavaScript), ExoPlayer (MPD/MP4). Đây là nơi cần cập nhật bản vá đầu tiên.

## 5. Kết luận bảo mật
- **Thử nghiệm cá nhân:** chấp nhận được. Không có vấn đề CRITICAL. Rủi ro thực tế lớn nhất là SEC-2 (app khác trên máy theo dõi được video đang phát) và SEC-4 (lịch sử lên cloud backup).
- **Phân phối cho người khác:** cần xử lý SEC-1, SEC-2, SEC-3 và SEC-4 trước.
