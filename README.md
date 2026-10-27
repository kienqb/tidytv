# TidyTV

App Android để **dọn ứng dụng rác trên Android TV / Google TV qua Wi-Fi** rồi cài một launcher sạch có khoá trẻ em, làm cho người không rành kỹ thuật. Cài lên điện thoại Android hoặc cài thẳng lên TV đều được. Giao diện đi từng bước một, có 5 bước, nút to, tiếng Việt.

> Hướng dẫn sử dụng chi tiết cho người dùng: **https://kienqb.is-a.dev/huong-dan-tidytv/**
> Tải bản cài đặt: mục **[Releases](../../releases)** của repo này.

## Tính năng

1. **Kết nối** tới TV qua ADB trên Wi-Fi (hỗ trợ cả ghép nối bằng mã 6 số của Google TV đời mới).
2. **Nhận diện TV** (hãng, model, phiên bản Android) và tự gợi ý danh sách app rác theo mẫu máy — **158 danh sách mẫu** cho Sony, TCL, Xiaomi, Philips, Hisense, Amazon Fire TV…
3. **Sao lưu** trạng thái hiện tại trước khi dọn (lưu trong app theo từng TV + một bản ghi trên TV).
4. **Dọn rác**: chỉ *tắt* (disable) ứng dụng, không gỡ hẳn; app hệ thống quan trọng và launcher đang dùng được bảo vệ sẵn. Mỗi lần tắt/bật đều đọc lại trạng thái thật trên TV để kiểm chứng, không báo khống.
5. **Cài launcher** đóng gói kèm (TV Launcher Mod): màn hình chính gọn, có **khoá trẻ em bằng mã TOTP + passcode** và **điều khiển từ xa qua Telegram** (mỗi người tự tạo bot riêng của mình).
6. **Khôi phục**: bật lại đúng những app chính TidyTV đã tắt và trả launcher gốc về làm màn hình chính.

## Cách hoạt động

- Kết nối ADB bằng thư viện [libadb-android](https://github.com/MuntashirAkon/libadb-android) chạy ngay trong app, không cần máy tính, không cần root.
- Lệnh dọn dẹp duy nhất là `pm disable` — tắt app chứ không xoá, nên khôi phục được hoàn toàn.
- Trên Google TV, muốn launcher mới làm màn hình chính phải tắt launcher gốc (`com.google.android.apps.tv.launcherx` và `com.google.android.tungsten.setupwraith`); nếu giành Home không thành công, app tự bật lại launcher gốc.

## Yêu cầu

- TV chạy **Android TV / Google TV, Android 8.0 trở lên** (Samsung và LG dùng hệ điều hành riêng nên không dùng được).
- Điện thoại chạy app: Android 8.0+; hoặc cài thẳng app lên TV.
- TV và điện thoại cùng một mạng Wi-Fi.

## Build từ mã nguồn

Cần JDK 17 và Android SDK 34:

```bash
./gradlew :app:assembleDebug     # bản debug, ký bằng khoá debug mặc định
./gradlew :app:testDebugUnitTest # chạy unit test
```

Bản release phải ký bằng khoá của chính bạn: tạo một keystore, rồi tạo file `key.properties` (gồm `storeFile`, `storePassword`, `keyAlias`, `keyPassword`) và trỏ biến môi trường `TIDYTV_KEY_PROPERTIES` tới file đó (hoặc đặt tại `~/.signing-keys/tv-launcher/key.properties`). Thiếu khoá thì build release sẽ dừng và báo lỗi, không âm thầm xuất APK chưa ký.

File `app/src/main/assets/tvhome.apk` là launcher đóng gói kèm (bản share đã build sẵn); thay file này bằng launcher khác cùng package `com.gathin.tvhome` nếu bạn muốn đóng gói bản của mình.

## Nguồn gốc & ghi công

TidyTV được làm tiếp từ hai dự án dưới đây, ghi rõ ra đây để ai lấy mã nguồn về dùng cũng truy được về gốc:

- **[CleanYourTV](https://github.com/Gathin23/cleanyourtv)** của **Gathin23** — dự án gốc về dọn TV qua Wi-Fi. Danh sách ứng dụng rác theo từng mẫu TV trong `app/src/main/assets/models/` được lấy từ bộ ví dụ (examples) của CleanYourTV. Launcher đóng gói kèm trong TidyTV (`tvhome.apk`, gói `com.gathin.tvhome`) cũng là bản chỉnh sửa từ launcher của CleanYourTV.
- **[CleanTV by VGS](https://github.com/vgscity/vgs-cleantv)** của **VGS** — bản phát triển tiếp từ CleanYourTV, là nguồn cảm hứng trực tiếp cho cách làm của TidyTV: kết nối ADB qua Wi-Fi cho người không rành kỹ thuật, sao lưu và khôi phục một chạm.

Thư viện bên thứ ba:

- [libadb-android](https://github.com/MuntashirAkon/libadb-android) của Muntashir Akon — Apache License 2.0
- [Conscrypt](https://github.com/google/conscrypt) của Google — Apache License 2.0
- [Bouncy Castle](https://www.bouncycastle.org/) — Bouncy Castle Licence

## Giấy phép

Mã nguồn của TidyTV phát hành theo giấy phép **MIT** (xem file [LICENSE](LICENSE)). Các thành phần bên thứ ba — gồm danh sách mẫu TV và launcher đóng gói kèm có nguồn gốc từ CleanYourTV — giữ nguyên giấy phép và quyền tác giả của dự án gốc.

## Tác giả

Kiên Nguyễn — Quảng Bình, Việt Nam. Trang dự án: https://kienqb.is-a.dev
