# TÍCH HỢP ENGINE PIKAFISH & KIẾN TRÚC NATIVE BRIDGE

## 1. Kiến trúc phân tầng (Mục 6.1)

```text
UI (Compose)
  ↓
ViewModel (GameViewModel)
  ↓
EngineManager (quản lý hàng đợi, requestId, hủy tác vụ, cấp độ 1–10)
  ↓
XiangqiEngine interface
  ├── PikafishEngine (UCI over Native Process / JNI Bridge)
  └── BuiltInXiangqiEngine (Alpha-Beta + Quiescence + PST thuần Kotlin - fallback offline)
```

## 2. Chế độ hoạt động hiện tại (Phase 1)

- **Khi chưa đóng gói binary Pikafish + mạng `pikafish.nnue`**:
  - `EngineManager` tự động dùng `BuiltInXiangqiEngine` thuần Kotlin để ứng dụng chạy ngay, chơi mượt 10 cấp độ, không crash và hiển thị đúng tên engine đang dùng (`Luyện Kỳ Core` thay vì giả mạo `Pikafish`).
- **Khi tích hợp Pikafish thật cho thiết bị Android**:
  1. Tải mã nguồn Pikafish từ `https://github.com/official-pikafish/Pikafish` (Giấy phép **GPL-3.0**) và file mạng đánh giá `pikafish.nnue` tương ứng phiên bản.
  2. Biên dịch Pikafish bằng Android NDK cho các ABI mục tiêu (`arm64-v8a`, `armeabi-v7a`, `x86_64`) thành file thực thi đặt tên `libpikafish.so` trong `app/src/main/jniLibs/<abi>/libpikafish.so`.
     *(Lưu ý: trên Android 10+ / API 29+, thực thi binary từ `applicationInfo.nativeLibraryDir` đáp ứng quy định W^X khi đặt `jniLibs.useLegacyPackaging = true`.)*
  3. Đặt file mạng đánh giá vào `app/src/main/assets/engine/pikafish.nnue` (ứng dụng sẽ tự copy sang `filesDir/engine/pikafish.nnue` khi khởi tạo để truyền vào `setoption name EvalFile value <path>`).
  4. Tùy chọn: bật cầu nối JNI C++ tại `app/src/main/cpp/engine_bridge/` bằng cờ Gradle `-PenableNativeEngineBridge=true`.

## 3. Nghĩa vụ giấy phép (Mục 4.3)

- Pikafish phát hành theo giấy phép **GNU General Public License v3.0 (GPL-3.0)**.
- Trước khi phân phối APK/AAB có tích hợp Pikafish, cần tuân thủ đầy đủ nghĩa vụ GPL-3.0: ghi nhận tác giả, hiển thị toàn văn giấy phép trong mục *Thông tin & Giấy phép* của ứng dụng, và cung cấp mã nguồn tương ứng theo đúng phương thức tích hợp/phân phối thực tế.
