# PERFORMANCE REPORT — AZ Tube 1.0

**Ngày đo:** 2026-11-09
**Bản đo:** commit `d344b73` (bản release, `isMinifyEnabled=false`).
**Thiết bị:**
- Giả lập MuMu (Android 15, x86_64, 720x1600). Đo chính.
- Pixel 7a (Android 16). Chỉ đọc số RAM một lần, không thao tác.

**Cách đo:**
- Script đọc mỗi phút qua adb:
  - `dumpsys meminfo com.az.tube`: PSS, Java heap, native heap
  - `/proc/<pid>/stat`: tổng CPU ticks, 100 ticks = 1 giây CPU
  - `dumpsys media_session`: trạng thái và vị trí phát
- `dumpsys batterystats` sau khi reset trước bài đo.

**Giới hạn của số đo:**
- Giả lập không có mô hình pin thật. Số mAh trong batterystats **không phản ánh pin điện thoại thật**. Chỉ dùng được thời lượng (giải mã video, wakelock).
- CPU của giả lập x86 khác CPU ARM của điện thoại. Chỉ nên so sánh tương đối.
- Mỗi bài chỉ chạy 1 lần. Không đủ để kết luận thống kê.

---

## 1. Tóm tắt

| Hạng mục | Kết quả | Đánh giá |
|---|---|---|
| RAM khi nghỉ (Pixel) | PSS 81 MB | Bình thường cho app Compose + ExoPlayer |
| RAM chạy nền 30 phút (MuMu) | PSS 115–131 MB, đầu 119,8 MB, cuối 119,6 MB | **Không thấy rò rỉ** |
| Phát nền 30 phút, tắt màn hình | Phát liên tục, không dừng, không lỗi | **Đạt** |
| CPU chạy nền | ~2% một nhân trong 8 phút đầu, rồi **~12%** từ phút thứ 9 | **Có vấn đề**, xem PERF-1 |
| Mất mạng, có mạng lại | Phát hết bộ đệm (~55 giây), rồi bỏ qua bài và **dừng ở trạng thái lỗi**. Không tự phục hồi khi có mạng lại. | **Không đạt**, xác nhận ARCH-1 |
| Cuộc gọi đến | Chưa đo | Xem mục 5 |
| Hệ điều hành thu hồi app chạy nền | Chưa đo | Xem mục 5 |
| Khôi phục dữ liệu sau khi đóng đột ngột | Chưa đo trên máy. Có phân tích mã | Xem mục 5 |

---

## 2. Bài đo 30 phút chạy nền

**Điều kiện:**
- Video "Best Running Music 2026 | 130-160 BPM", dài 1:06:34.
- App ở nền, màn hình tắt, âm lượng media bằng 0.
- Không thao tác gì trong 30 phút.

| Phút | PSS (KB) | Java heap (KB) | Native heap (KB) | CPU ticks (cộng dồn) | Trạng thái | Vị trí (ms) |
|---|---|---|---|---|---|---|
| 0 | 119.849 | 14.540 | 12.608 | 341 | PLAYING | 15.931 |
| 4 | 116.220 | 13.236 | 11.992 | 802 | PLAYING | 258.605 |
| 8 | 115.567 | 13.016 | 11.856 | 1.308 | PLAYING | 498.258 |
| 9 | **131.279** | **21.292** | **15.380** | 1.828 | PLAYING | 558.267 |
| 10 | 122.882 | 19.016 | 13.456 | 2.479 | PLAYING | 618.264 |
| 15 | 123.635 | 18.916 | 13.880 | 5.788 | PLAYING | 918.277 |
| 20 | 121.631 | 18.820 | 14.784 | 9.322 | PLAYING | 1.218.264 |
| 25 | 122.470 | 18.816 | 15.536 | 13.136 | PLAYING | 1.518.268 |
| 30 | 119.615 | 18.744 | 16.240 | 17.019 | PLAYING | 1.823.049 |

Số liệu đủ 31 mẫu (phút 0–30): `perf30.csv` trong thư mục scratchpad của phiên audit.

**Nhận xét:**
- **Phát liên tục:** vị trí tăng 1.807 giây trong khoảng 1.800 giây đo. Không có lần dừng hay đệm nào.
- **RAM ổn định:** cuối bài thấp hơn đầu bài. Không có xu hướng tăng của PSS.
- **Native heap tăng chậm:** từ 13,5 MB (phút 10) lên 16,2 MB (phút 30), khoảng +0,13 MB/phút. Xem PERF-3.
- **CPU tăng ~6 lần từ phút thứ 9:**
  - Phút 0–8: ~121 ticks/phút, tức ~2% một nhân.
  - Phút 9–30: ~723 ticks/phút, tức ~12% một nhân.
  - Cùng lúc, Java heap tăng từ 13 lên 19–21 MB.
  - Batterystats ghi nhận **giải mã video 21 phút 4 giây**, khớp với khoảng phút 9 đến phút 30, trong khi màn hình tắt. Xem PERF-1.

### Bài đo phụ (bị loại)
Lần chạy đầu bị nhiễu vì giả lập đang được dùng (video phát trên màn hình). Số đo chỉ để tham khảo: PSS 91–114 MB, ~800 ticks/phút (~13%) khi phát video có hình trên màn hình. Mức CPU này tương đương với giai đoạn sau phút 9 của bài đo chính. Điều đó củng cố giả thuyết PERF-1 là video đã được giải mã lại dù màn hình tắt.

---

## 3. Bài đo mất mạng

**Điều kiện:**
- Phát "Never Gonna Give You Up" ở nền, màn hình tắt.
- Tắt Wi-Fi và dữ liệu di động 120 giây (`svc wifi disable`, `svc data disable`), rồi bật lại.
- Lấy mẫu mỗi 10 giây.

| Thời điểm | Trạng thái | Ghi chú |
|---|---|---|
| 0 s | PLAYING, vị trí 14 s, bộ đệm 70 s | Tắt mạng |
| 10–51 s | PLAYING | Phát tiếp từ bộ đệm |
| 61 s | BUFFERING, **chuyển sang bài kế tiếp** "Cry for Help" | Hết bộ đệm, bài đang phát bị bỏ qua |
| 71–122 s | **ERROR "Source error"** | Bật lại mạng ở 122 s |
| 132–172 s | **Vẫn ERROR** | Không tự phục hồi sau 50 giây có mạng |

Một lần mất mạng ngoài ý muốn trước đó, kéo dài ~6 phút, cho kết quả giống hệt: bỏ qua bài, rồi dừng ở ERROR.

**Kết luận:** xác nhận **ARCH-1 (HIGH)** trong `AUDIT_REPORT.md`. Khi mất mạng lúc chạy bộ, người dùng mất bài đang nghe và phải mở app bấm phát lại.

---

## 4. Danh sách vấn đề

### PERF-1 · MEDIUM — Video được giải mã lại khi màn hình tắt
- **Vị trí:**
  - `MainActivity.kt:75-77`: `onStop()` tắt track video.
  - `AzApp.kt:144`: `LaunchedEffect(player, vm.audioOnly) { setVideoEnabled(it, !vm.audioOnly) }` bật lại video mỗi khi `player` hoặc `audioOnly` thay đổi.
- **Mô tả:** sau khoảng 9 phút chạy nền, CPU tăng ~6 lần. Batterystats ghi nhận 21 phút giải mã video dù màn hình tắt.
- **Nguyên nhân:** **chưa xác định chắc chắn.** Giả thuyết: `LaunchedEffect` ở `AzApp.kt:144` chạy lại (ví dụ khi đối tượng `player` hoặc trạng thái thay đổi trong lúc app ở nền) và bật lại track video mà `onStop()` đã tắt.
- **Ảnh hưởng:**
  - Tốn pin và dữ liệu (tải cả luồng video) khi chạy bộ tắt màn hình.
  - Mục tiêu chính của app là nghe nền, nên ảnh hưởng trực tiếp.
- **Cách sửa:**
  - Tính trạng thái video từ một nguồn duy nhất: `videoEnabled = !audioOnly && activity đang STARTED`. Ví dụ dùng `LocalLifecycleOwner` + `repeatOnLifecycle(STARTED)` thay cho `LaunchedEffect` không theo vòng đời.
  - Kiểm chứng lại bằng bài đo 30 phút: CPU phải giữ ~2% suốt bài, batterystats không có thời lượng video.

### PERF-2 · HIGH — Mất mạng làm mất bài và dừng hẳn
Là ARCH-1, đã xác nhận bằng đo thực tế ở mục 3. Cách sửa: xem ARCH-1 và RECOMMENDATIONS 1.1.

### PERF-3 · LOW — Native heap tăng chậm
- **Vị trí:** chưa xác định. Có thể là bộ đệm của ExoPlayer, Coil hoặc `InfoCache` (ARCH-5).
- **Mô tả:** +2,8 MB trong 20 phút chạy nền. Số đo ngắn, chưa đủ để kết luận là rò rỉ.
- **Ảnh hưởng:** thấp trong phiên chạy bộ 1–2 giờ.
- **Cách sửa:** đo lại 2 giờ. Nếu tăng tuyến tính, dùng Android Studio Memory Profiler (bản debug) để tìm nguồn.

### PERF-4 · LOW — Chưa có bài đo trên điện thoại thật khi chạy nền
- **Mô tả:** số đo CPU và pin chỉ có trên giả lập.
- **Cách sửa:** lặp lại bài đo 30 phút trên Pixel 7a, đọc pin bằng `dumpsys batterystats` hoặc Battery Historian.

---

## 5. Các bài chưa đo được

Giả lập được dùng chung với người dùng. Khi hết khoảng thời gian được phép, các bài sau chưa chạy:

| Bài | Trạng thái | Phân tích mã (chưa kiểm chứng trên máy) |
|---|---|---|
| Cuộc gọi đến | Chưa đo | ExoPlayer dùng `setAudioAttributes(..., handleAudioFocus = true)` nên sẽ tạm dừng khi mất audio focus và phát tiếp khi có lại. Cần kiểm chứng. MuMu không giả lập được cuộc gọi GSM, nên cần đo trên điện thoại thật. |
| Hệ điều hành thu hồi app | Chưa đo | Không có `onPlaybackResumption` (ARCH-3). Sau khi app bị thu hồi, nút phát trên tai nghe hay màn hình khoá **sẽ không phát tiếp** bài cũ, và hàng chờ bị mất. |
| Đóng đột ngột rồi mở lại | Chưa đo | Lưu trữ dùng `SharedPreferences.apply()`, ghi xuống đĩa bất đồng bộ. Nếu process bị giết ngay sau khi bấm đăng ký, có thể mất thay đổi cuối cùng (rủi ro thấp). JSON hỏng sẽ xoá danh sách (DATA-2). |

**Cách đo đề xuất:**
- Cuộc gọi: gọi đến Pixel trong lúc phát nền.
- Thu hồi app: `adb shell am kill com.az.tube` khi đang tạm dừng ở nền, rồi bấm nút phát trên tai nghe.
- Đóng đột ngột: đăng ký một kênh, `adb shell am force-stop com.az.tube` ngay sau đó, mở lại và kiểm tra.
