# AZ Tube

Ứng dụng Android xem video YouTube không quảng cáo, phát nền khi tắt màn hình. Viết cho mục đích cá nhân: nghe nhạc khi chạy bộ.

- **Tải file cài:** [aztube-releases](https://github.com/diennguyendhv/aztube-releases/releases)
- **Trang chủ:** https://diennguyendhv.github.io/aztube-releases/

> AZ Tube là dự án cá nhân, độc lập, **không liên kết với YouTube hay Google**. App lấy dữ liệu qua thư viện mã nguồn mở NewPipeExtractor, không dùng API chính thức của YouTube. Việc sử dụng có thể không phù hợp với Điều khoản dịch vụ của YouTube. Bạn tự cân nhắc khi dùng. Xem [`audit/`](audit/AUDIT_REPORT.md).

## Tính năng
- Tìm kiếm, trang chủ theo chủ đề, trang kênh, playlist.
- Phát nền khi tắt màn hình, điều khiển trên thông báo và màn hình khoá, nút tai nghe.
- Cửa sổ nổi (PiP), chế độ chỉ nghe âm thanh, chọn chất lượng tối đa.
- Đăng ký kênh không cần tài khoản (lưu trên máy).
- Đề xuất chỉ từ kênh đang phát. "Không đề xuất kênh này" để ẩn hẳn một kênh.
- Công tắc bật/tắt lịch sử xem, lịch sử tìm kiếm, đề xuất (tab Bạn).
- Mất mạng thì giữ bài và vị trí, có mạng lại thì phát tiếp.

## Build
Cần Android Studio (JDK đi kèm) và Android SDK 37.

```bash
./gradlew assembleRelease
```

File APK nằm ở `app/build/outputs/apk/release/`. Muốn ký bằng khóa riêng, tạo file `keystore.properties` ở thư mục gốc (đã nằm trong `.gitignore`):

```properties
storeFile=/đường/dẫn/tới/khoa.keystore
storePassword=...
keyAlias=...
keyPassword=...
```

Không có file này thì bản release được ký bằng debug key của máy đang build.

## Thư viện chính
- [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) (GPL-3.0)
- [AndroidX Media3 / ExoPlayer](https://github.com/androidx/media) (Apache-2.0)
- Jetpack Compose, Material 3, Coil (Apache-2.0)

Danh sách đầy đủ và giấy phép: [`audit/LICENSE_REPORT.md`](audit/LICENSE_REPORT.md).

## Giấy phép
Mã nguồn phát hành theo **GNU General Public License v3.0**. Xem [`LICENSE`](LICENSE).
