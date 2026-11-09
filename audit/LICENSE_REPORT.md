# LICENSE REPORT — AZ Tube 1.0

**Ngày audit:** 2026-11-09
**Phiên bản kiểm tra:** commit `d344b73` (versionName 1.0, versionCode 1)
**Nguồn dữ liệu:**
- Cây phụ thuộc `releaseRuntimeClasspath`, xuất bằng `gradlew :app:dependencies`. Cây có 309 artifact khác nhau, trong đó 105 module AndroidX.
- Trường `<license>` trong file POM của từng artifact, đọc từ Gradle cache trên máy.

> **Lưu ý quan trọng:** báo cáo này chỉ liệt kê sự kiện kỹ thuật và nghĩa vụ ghi trong văn bản giấy phép. Đây **không phải ý kiến pháp lý**. Mục nào ghi "chưa xác minh" nghĩa là chưa có đủ căn cứ để kết luận. Trước khi phân phối APK ra ngoài, nên hỏi người có chuyên môn pháp lý.

---

## 1. Thư viện khai báo trực tiếp (`app/build.gradle.kts`)

| Thư viện | Phiên bản | Giấy phép (theo POM) | Ghi chú |
|---|---|---|---|
| `com.github.TeamNewPipe:NewPipeExtractor` | v0.27.0 | **GNU GPL v3.0 or later** | Lấy qua JitPack. Là lõi của app. |
| `com.squareup.okhttp3:okhttp` | 5.5.1 | Apache-2.0 | |
| `androidx.media3:media3-exoplayer` | 1.11.1 | Apache-2.0 | |
| `androidx.media3:media3-exoplayer-dash` | 1.11.1 | Apache-2.0 | |
| `androidx.media3:media3-session` | 1.11.1 | Apache-2.0 | |
| `androidx.media3:media3-ui` | 1.11.1 | Apache-2.0 | |
| `androidx.core:core-ktx` | 1.19.1 | Apache-2.0 | |
| `androidx.activity:activity-compose` | 1.13.0 | Apache-2.0 | |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | 2.11.0 | Apache-2.0 | |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.11.0 | Apache-2.0 | |
| `androidx.compose:compose-bom` | 2026.09.00 (ui 1.12.1, material3 1.4.0) | Apache-2.0 | |
| `io.coil-kt.coil3:coil-compose` | 3.6.3 | Apache-2.0 | |
| `io.coil-kt.coil3:coil-network-okhttp` | 3.6.3 | Apache-2.0 | |
| `com.android.tools:desugar_jdk_libs_nio` | 2.1.5 | GPL-2.0 kèm **Classpath Exception** (mã OpenJDK) | Cấu hình `coreLibraryDesugaring`. Mã này **được đóng gói vào APK**. Không đọc được giấy phép từ POM; thông tin lấy theo dự án upstream, **cần xác minh**. |
| Kotlin stdlib | 2.4.21 | Apache-2.0 | |

## 2. Thư viện kéo theo đáng chú ý (transitive)

| Thư viện | Phiên bản | Giấy phép | Đi kèm thư viện | Ghi chú |
|---|---|---|---|---|
| `com.github.TeamNewPipe:nanojson` | commit `e9d656dd…` | **Chưa xác minh** | NewPipeExtractor | POM và JAR đều không ghi giấy phép. POM trỏ về `mmastrac/nanojson`. Cần kiểm tra file LICENSE trên repo fork của TeamNewPipe. |
| `org.mozilla:rhino`, `rhino-engine` | 1.8.1 | MPL-2.0 | NewPipeExtractor | Copyleft theo từng file. Dùng để chạy JavaScript player của YouTube. |
| `org.jsoup:jsoup` | 1.23.2 | MIT | NewPipeExtractor | |
| `com.google.protobuf:protobuf-javalite` | 4.36.2 | Chưa đọc được từ POM. Upstream dùng BSD-3-Clause, **cần xác minh** | NewPipeExtractor | |
| `com.google.guava:guava` | 33.3.1-android | Chưa đọc được từ POM (nằm ở parent POM). Upstream dùng Apache-2.0 | Media3 | |
| `com.google.guava:failureaccess` | 1.0.2 | Như trên | Guava | |
| `com.google.code.findbugs:jsr305` | 3.0.2 | Apache-2.0 (theo POM) | | Chỉ chứa annotation |
| `com.squareup.okio:okio` | 3.18.1 | Apache-2.0 | OkHttp | |
| `com.google.accompanist:accompanist-drawablepainter` | 0.37.3 | Apache-2.0 | Coil | |
| `org.jspecify:jspecify` | 1.0.0 | Apache-2.0 | | |
| `org.jetbrains:annotations` | 13.0 / 23.0.0 | Apache-2.0 (upstream) | Kotlin | |
| 105 module AndroidX | nhiều phiên bản | Apache-2.0 | | 78 POM không ghi trực tiếp giấy phép vì khai báo ở parent POM. Toàn bộ AndroidX phát hành theo Apache-2.0. |

Danh sách đầy đủ 309 artifact có thể xuất lại bằng lệnh:
```bash
./gradlew :app:dependencies --configuration releaseRuntimeClasspath
```

## 3. Nhóm giấy phép và nghĩa vụ khi phân phối APK

| Giấy phép | Thư viện | Nghĩa vụ chính khi phân phối bản nhị phân |
|---|---|---|
| **GPL-3.0-or-later** | NewPipeExtractor | Xem mục 4. Đây là nghĩa vụ nặng nhất và quyết định cách phân phối toàn bộ app. |
| GPL-2.0 + Classpath Exception | desugar_jdk_libs | Ngoại lệ Classpath cho phép liên kết mà không áp GPL-2.0 lên phần còn lại của app. Vẫn phải giữ thông báo bản quyền. |
| MPL-2.0 | Rhino | Copyleft theo từng file. Dùng nguyên bản thì phải giữ thông báo và cho người nhận biết cách lấy mã nguồn các file MPL (có thể trỏ về repo upstream). |
| MIT | jsoup, (có thể) nanojson | Kèm thông báo bản quyền và nội dung giấy phép. |
| BSD-3-Clause | protobuf-javalite (cần xác minh) | Kèm thông báo bản quyền và điều khoản. Không dùng tên tác giả để quảng bá. |
| Apache-2.0 | AndroidX, Media3, OkHttp, Okio, Coil, Kotlin, Guava… | Kèm nội dung giấy phép Apache-2.0 và các file `NOTICE` (nếu thư viện có). Ghi rõ nếu đã sửa mã thư viện (hiện không sửa). |

## 4. Nghĩa vụ GPL-3.0 đối với NewPipeExtractor

Phần này tóm tắt từ văn bản GPL-3.0. Áp dụng cụ thể vào dự án cần ý kiến pháp lý.

1. **Phạm vi:** AZ Tube gọi trực tiếp các lớp của NewPipeExtractor và đóng gói chung trong một APK. Theo cách FSF giải thích, đây là một "work based on" thư viện GPL. Khi **phân phối** APK, toàn bộ app thường phải phát hành theo GPL-3.0 (hoặc giấy phép tương thích). Apache-2.0 tương thích một chiều với GPL-3.0, nên các thư viện Apache-2.0 không cản trở việc này.
2. **Mã nguồn tương ứng (Corresponding Source, §6):** người nhận APK phải có cách lấy **toàn bộ** mã nguồn và script build (Gradle, cấu hình). Có thể đặt link tải mã nguồn ngay cạnh link tải APK, hoặc kèm lời đề nghị cung cấp mã nguồn bằng văn bản, có hiệu lực ít nhất 3 năm.
3. **Văn bản giấy phép và thông báo (§4–5):** kèm toàn văn GPL-3.0, giữ nguyên thông báo bản quyền của NewPipe, và ghi rõ app là sản phẩm phái sinh.
4. **Không thêm hạn chế (§10):** không được cấm người nhận sửa hoặc phân phối lại. Không được dùng điều khoản riêng mâu thuẫn với GPL.
5. **Installation Information (§6, "User Products"):** GPL-3.0 yêu cầu cung cấp thông tin cần thiết để cài bản đã sửa lên thiết bị. Android cho phép cài APK tự ký, nên yêu cầu này thường được đáp ứng sẵn. **Chưa kết luận**; cần xem xét thêm nếu phân phối kèm thiết bị.
6. **Dùng cá nhân:** GPL không ràng buộc việc tự build và tự dùng mà không phân phối cho ai. Nghĩa vụ chỉ phát sinh khi "convey", tức là đưa APK cho người khác, kể cả bạn bè.

## 5. Hiện trạng tuân thủ

| # | Mức độ | Vị trí | Vấn đề | Ảnh hưởng | Cách khắc phục hợp lệ |
|---|---|---|---|---|---|
| L-1 | **HIGH** | Repo `diennguyendhv/AZ-Tube` (private); kế hoạch phát hành trên diennguyenaz.com | Repo đang **riêng tư**, trong khi dự định đưa APK cho người quen. Người nhận APK không có cách lấy mã nguồn. | Nếu phân phối như hiện tại thì không đáp ứng nghĩa vụ cung cấp mã nguồn của GPL-3.0 (§6). | Trước khi đưa APK cho bất kỳ ai: công khai repo (hoặc một bản mirror công khai đúng commit của APK), đặt link mã nguồn cạnh link tải APK, và gắn tag phiên bản trùng `versionName`. |
| L-2 | **HIGH** | Gốc repo | Không có file `LICENSE`. Dự án chưa tuyên bố giấy phép. | Người nhận không biết quyền của mình. Mã riêng của dự án đang ở trạng thái "all rights reserved", mâu thuẫn với nghĩa vụ GPL khi phân phối. | Thêm `LICENSE` với toàn văn GPL-3.0 (hoặc GPL-3.0-or-later). Thêm header bản quyền hoặc mục "Giấy phép" trong README. |
| L-3 | **MEDIUM** | `app/` (UI) | App không có màn hình "Giấy phép mã nguồn mở". APK cũng không kèm `NOTICE` hay license của thư viện. | Thiếu thông báo bản quyền bắt buộc của Apache-2.0, MIT, MPL-2.0, BSD và GPL khi phân phối bản nhị phân. | Thêm màn hình "Giấy phép" trong tab Bạn, liệt kê thư viện, phiên bản và văn bản giấy phép. Có thể dùng plugin như `com.google.android.gms:oss-licenses-plugin` hoặc AboutLibraries (cần tự kiểm tra giấy phép của chính plugin). |
| L-4 | **MEDIUM** | `nanojson`, `protobuf-javalite`, `desugar_jdk_libs_nio` | Không đọc được giấy phép từ artifact. | Chưa đủ căn cứ để liệt kê đúng nghĩa vụ. | Kiểm tra file LICENSE trên repo upstream của từng thư viện và ghi lại vào màn hình giấy phép. |
| L-5 | **LOW** | `audit/LICENSE_REPORT.md` | Danh sách thư viện được tạo thủ công tại một thời điểm, sẽ lệch khi nâng phiên bản. | Thông tin giấy phép cũ. | Tự động tạo danh sách khi build (plugin license report) và kiểm tra trong CI. |
| L-6 | **LOW** | Tên app "AZ Tube", chữ "YouTube" trong UI và intent-filter | Không thuộc giấy phép phần mềm, nhưng liên quan nhãn hiệu. Logo đã đổi sang mẫu riêng (đường chạy màu cam). | Rủi ro nhãn hiệu thấp hơn trước. **Chưa kết luận**. | Không dùng logo hoặc màu nhận diện của YouTube. Ghi rõ "không liên kết với YouTube/Google" trong app và trên trang tải. |

## 6. Kết luận (trong phạm vi giấy phép)
- **Dùng cá nhân** (tự build, tự cài, không đưa ai): không thấy nghĩa vụ giấy phép nào bị vi phạm.
- **Phân phối cho người khác**, kể cả người quen qua diennguyenaz.com: **chưa sẵn sàng**. Ít nhất phải xử lý xong L-1, L-2, L-3 trước khi phát hành.
- Giấy phép phần mềm là vấn đề **riêng**, tách biệt với điều khoản dịch vụ của YouTube (xem `AUDIT_REPORT.md`, mục 4). Tuân thủ GPL **không** đồng nghĩa với việc được phép truy cập nội dung YouTube theo cách app đang làm.
