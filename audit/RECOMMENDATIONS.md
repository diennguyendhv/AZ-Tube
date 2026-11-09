# RECOMMENDATIONS — AZ Tube 1.0

**Ngày:** 2026-11-09 · **Áp dụng cho:** commit `d344b73`

Tài liệu này gom các đề xuất từ:
- [`AUDIT_REPORT.md`](AUDIT_REPORT.md): ARCH, DATA, TOS
- [`SECURITY_REPORT.md`](SECURITY_REPORT.md): SEC
- [`LICENSE_REPORT.md`](LICENSE_REPORT.md): L
- [`PERFORMANCE_REPORT.md`](PERFORMANCE_REPORT.md): PERF

Đề xuất được sắp theo **thứ tự nên làm**. Theo yêu cầu audit, tài liệu **không** đề xuất cách né giới hạn của YouTube, không thay đổi cơ chế xác thực, và không tối ưu thêm việc bỏ qua quảng cáo.

---

## 1. Bảng tổng hợp mức độ

| Mức | Mã vấn đề |
|---|---|
| **CRITICAL** | TOS-1, TOS-2 (điều khoản dịch vụ YouTube) |
| **HIGH** | TOS-3, TOS-4, ARCH-1 (= PERF-2), SEC-1 (khi phân phối), L-1, L-2 |
| **MEDIUM** | PERF-1, ARCH-2, ARCH-3, ARCH-4, ARCH-9, DATA-2, SEC-2, SEC-3, SEC-4, L-3, L-4, TOS-5 |
| **LOW** | PERF-3, PERF-4, ARCH-5…8, DATA-1, DATA-3, SEC-5…10, L-5, L-6, TOS-6 |

Chi tiết các mã PERF nằm trong [`PERFORMANCE_REPORT.md`](PERFORMANCE_REPORT.md).

---

## 2. Điều khoản dịch vụ: phương án dùng nội dung và trình phát được cấp phép

Các vấn đề TOS-1 và TOS-2 nằm ở **cách app lấy và phát video**. Không thể sửa bằng một thay đổi nhỏ. Có 4 hướng hợp lệ, có thể kết hợp:

| Phương án | Mô tả | Còn quảng cáo? | Phát nền? | Công sức | Ghi chú |
|---|---|---|---|---|---|
| **A. YouTube IFrame Player + YouTube Data API v3** | Phát bằng trình phát nhúng chính thức trong WebView (ví dụ thư viện mã nguồn mở `android-youtube-player`). Tìm kiếm, kênh, playlist qua Data API v3 với API key của bạn. | **Có** (do YouTube phân phát) | **Không**. Developer Policies III.I.9 cấm trình phát nền. | Trung bình. Thay `Media.kt`, `YoutubeDataSource.kt`, `Repo.kt`. Giữ được UI Compose. | Phải tuân thủ API Terms: hiển thị thương hiệu, có chính sách quyền riêng tư, quota 10.000 đơn vị/ngày (tìm kiếm tốn 100 đơn vị/lần), không cache dữ liệu quá thời hạn. Mất mục tiêu "không quảng cáo, phát nền". |
| **B. YouTube Premium với app chính thức** | Dùng app YouTube hoặc YouTube Music chính thức với gói Premium. | Không | Có | Không cần code | Cách hợp lệ duy nhất để có đủ "không quảng cáo + phát nền" cho nội dung YouTube. |
| **C. Nguồn nhạc có giấy phép mở cho chạy bộ** | Đổi nguồn sang nhạc có API và giấy phép rõ ràng, ví dụ Jamendo API (nhạc Creative Commons), Free Music Archive, podcast qua RSS công khai. | Không | Có (được phép theo điều khoản của nguồn) | Trung bình. Giữ toàn bộ phần phát nền Media3 hiện có, chỉ thay lớp dữ liệu. | Cần đọc điều khoản API của từng nguồn (đăng ký key, ghi công tác giả, giới hạn thương mại). |
| **D. Trình phát file của chính bạn** | Phát nhạc hoặc video bạn sở hữu trên máy, hoặc từ máy chủ riêng của bạn (diennguyenaz.com). | Không | Có | Thấp. Media3 đã hỗ trợ sẵn file cục bộ và HTTP. | Hoàn toàn trong quyền kiểm soát của bạn. |

**Khuyến nghị:**
- Nếu mục tiêu chính là **nghe nhạc khi chạy bộ**, phương án **C + D** giữ được trọn trải nghiệm hiện tại (phát nền, không quảng cáo, không lịch sử) mà không phụ thuộc vào việc lấy nội dung YouTube.
- Nếu cần **nội dung YouTube**, chỉ A hoặc B là hợp lệ, và A phải chấp nhận có quảng cáo, không phát nền.
- Cho tới khi chọn hướng: **giữ app ở mức thử nghiệm cá nhân**, không phân phối, không tối ưu thêm `YoutubeDataSource.kt` hay `StreamResolver`.

---

## 3. Lộ trình đề xuất

### Giai đoạn 0: Ngay bây giờ, trước khi đưa APK cho bất kỳ ai
| # | Việc | Liên quan |
|---|---|---|
| 0.1 | **Chưa phân phối APK** (kể cả người quen qua diennguyenaz.com) cho tới khi quyết định hướng ở mục 2 và xử lý giấy phép. | TOS-1…4, L-1 |
| 0.2 | Không thêm tính năng làm tăng số request tới YouTube (ví dụ bảng tin tự làm mới, tải trước). | TOS-3, ARCH-4 |

### Giai đoạn 1: Ổn định cho thử nghiệm cá nhân (ước tính 1–2 ngày)
| # | Việc | Liên quan | Cách làm |
|---|---|---|---|
| 1.1 | ✅ Đã làm. Xử lý mất mạng đúng cách | ARCH-1, ARCH-2 | Phân loại lỗi theo `PlaybackException.errorCode`. Lỗi mạng thì giữ bài và vị trí, chờ `NetworkCallback.onAvailable` rồi `prepare()`. Reset `retries` khi `STATE_READY`. |
| 1.1b | ✅ Đã thêm lớp bảo vệ. Không giải mã video khi màn hình tắt | PERF-1 | Bật/tắt track video theo vòng đời Activity (`repeatOnLifecycle(STARTED)`), một nguồn duy nhất. Đo lại 30 phút. |
| 1.2 | Lưu và khôi phục hàng chờ | ARCH-3 | Lưu hàng chờ, chỉ số bài, vị trí khi chuyển bài hoặc tạm dừng. Hiện thực `MediaSession.Callback.onPlaybackResumption`. |
| 1.3 | Không mất kênh đăng ký khi JSON hỏng | DATA-2 | Sao lưu chuỗi gốc trước khi ghi đè. Parse từng phần tử. |
| 1.4 | Giới hạn app khác kết nối vào dịch vụ phát | SEC-2 | Override `onConnect`: chỉ chấp nhận chính app, controller của System UI và thông báo, và các controller tin cậy. |
| 1.5 | Loại lịch sử khỏi bản sao lưu cloud | SEC-4 | `dataExtractionRules` và `fullBackupContent`. Tách lịch sử sang file riêng. |
| 1.6 | Bắt lỗi kết nối controller | ARCH-8 | `try/catch` quanh `future.get()`. |
| 1.7 | Thông báo lỗi thân thiện | SEC-5, ARCH-6 | Đổi exception thành thông báo tiếng Việt. Thêm nút "Thử lại" ở cuối danh sách. |

### Giai đoạn 2: Chất lượng mã (ước tính 2–4 ngày)
| # | Việc | Liên quan |
|---|---|---|
| 2.1 | Tách interface `VideoRepository`, `SettingsStore`. Inject qua constructor (Hilt hoặc thủ công). | ARCH-9 |
| 2.2 | Tách `AppViewModel` thành `HomeViewModel`, `SearchViewModel`, `WatchViewModel`, `LibraryViewModel`. Điều hướng dùng Navigation Compose với `SavedStateHandle`. | §2.2 |
| 2.3 | UI không ghi trực tiếp vào `Store`. Mọi thay đổi cài đặt đi qua ViewModel. | §2.2 |
| 2.4 | Unit test cho `Paged`, `mix`, các hàm định dạng, `Store`, logic công tắc. Instrumented test cho `PlaybackService`. | ARCH-9 |
| 2.5 | Chuyển lưu trữ sang Room (hoặc DataStore) có schema version, khi thêm tính năng "Yêu thích". | DATA-1, DATA-3 |
| 2.6 | Giới hạn bộ đệm (`LruCache`), xoá `pages` khi rời màn hình. | ARCH-5 |
| 2.7 | Bộ nhớ đệm bảng tin kênh đăng ký 30 phút, giảm song song. | ARCH-4 |
| 2.8 | `PictureInPictureParams.setAutoEnterEnabled(true)` cho Android 12+. | §2.3 |

### Giai đoạn 3: Chỉ khi quyết định phân phối (và đã chọn hướng hợp lệ ở mục 2)
| # | Việc | Liên quan |
|---|---|---|
| 3.1 | Tạo keystore release riêng, sao lưu offline, ký v2+v3. | SEC-1 |
| 3.2 | Bật Gradle dependency verification, giới hạn JitPack theo group. | SEC-3 |
| 3.3 | Thêm `LICENSE` (GPL-3.0 nếu còn dùng NewPipeExtractor), công khai mã nguồn đúng commit của APK, đặt link mã nguồn cạnh link tải. | L-1, L-2 |
| 3.4 | Màn hình "Giấy phép mã nguồn mở" trong app. Xác minh giấy phép nanojson, protobuf, desugar. | L-3, L-4 |
| 3.5 | Bật R8 kèm keep rules, chạy quét CVE (OSV-Scanner) trước mỗi bản phát hành. | SEC-7, §4 SECURITY |
| 3.6 | Ghi rõ "không liên kết với YouTube/Google" trong app và trên trang tải. Nếu phân phối rộng thì hỏi ý kiến pháp lý. | L-6, TOS-6 |

---

## 4. Các thay đổi tính năng người dùng yêu cầu trong lúc audit
(1), (2) và (3) **đã làm** trong lúc audit theo yêu cầu "Sửa ngay" của người dùng (sau commit `d344b73`). (4) vẫn đang chờ:
1. ✅ **Mặc định bật** cả 3 công tắc: Lịch sử xem, Lịch sử tìm kiếm, Đề xuất. Người dùng muốn tắt thì vào tab Bạn.
2. ✅ **Đề xuất chỉ từ kênh đang phát:** danh sách video bên dưới trình phát và video tự phát tiếp chỉ lấy video cùng kênh với video đang xem, tránh lẫn nội dung kênh khác.
3. ✅ **"Không đề xuất kênh này"** (yêu cầu thêm trong lúc audit): ẩn mọi video của kênh đã chặn, bỏ chặn trong tab Bạn.
4. ⏳ **Đề xuất nội dung tích cực, bổ ích** (yêu cầu trước đó, đang tạm dừng): lọc chủ đề trang chủ và loại nội dung tiêu cực.

**Lưu ý khi làm:**
- (1) và (2) **không làm tăng** số request tới YouTube. Thực ra (2) có thể giảm request nếu dùng danh sách video sẵn có của kênh.
- (4) nên làm bằng cách chọn lại chủ đề và từ khoá trang chủ, lọc theo tiêu đề. Không nên gửi thêm request để "đánh giá" video (xem TOS-3).
