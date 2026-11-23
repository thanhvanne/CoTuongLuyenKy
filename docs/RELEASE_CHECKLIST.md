# Checklist Hoàn thiện & Phát hành — Cờ Tướng – Luyện Kỳ v1.0.0

Tài liệu kiểm tra trước phát hành theo Mục 25 và Mục 27 của `DAC_TA_DU_AN_CO_TUONG.md`.

---

## 1. Quyền riêng tư & Offline-first (§4, §25.5)
- [x] `AndroidManifest.xml` **không** khai báo quyền `INTERNET`, không khai báo quyền đọc/ghi bộ nhớ ngoài cũ.
- [x] Không tích hợp SDK quảng cáo, không theo dõi hành vi, không yêu cầu tài khoản.
- [x] Sao lưu/khôi phục dữ liệu thực hiện qua Storage Access Framework (`CreateDocument` / `OpenDocument`) với định dạng JSON có `schemaVersion = 1` và kiểm tra tính hợp lệ trước khi nhập.

## 2. Luật Cờ Tướng & Kiểm thử tự động (§5, §24)
- [x] Kiểm tra hợp lệ đầy đủ 7 loại quân (Tướng, Sĩ, Tượng, Xe, Pháo, Mã cản chân, Chốt qua sông).
- [x] Cấm lộ mặt Tướng (`kingsFacing`), chiếu bí (`CHECKMATE`), hết nước hợp lệ (`STALEMATE`), 60 nước không ăn quân (`SIXTY_MOVE_RULE`).
- [x] Bất biến 3 lần phân tách: **Trường chiếu bất biến** (`PERPETUAL_CHECK_LOSS`), **Trường tróc bất biến đơn phương** (`PERPETUAL_CHASE_LOSS` — Phase 4), và **Bất biến hòa** (`THREEFOLD_REPETITION_DRAW`).
- [x] Mọi thế cờ mẫu trong `SeedPuzzleCatalog` (15 bài: 4 chiến thuật, 6 khai cuộc, 5 tàn cuộc) đều qua kiểm tra FEN & chuỗi UCI hợp lệ.

## 3. Hiệu năng, Pin & Nhiệt độ (§6.6, §25.4)
- [x] `DeviceThermalPowerMonitor` tự động nhận diện `PowerManager.currentThermalStatus` (Android 10+), chế độ Tiết kiệm pin và mức pin ≤ 15% để hạ về `ECO` (Tiết kiệm pin).
- [x] Tùy chỉnh nâng cao trong Cài đặt cho phép chọn số luồng CPU (giới hạn theo `maxSafeThreads`) và bộ nhớ Hash.
- [x] Mọi tác vụ phân tích và engine đều hủy đúng khi rời màn hình, xoay màn hình hoặc đổi nước đi (`requestId` + `positionHash`).

## 4. Khả năng tiếp cận & Trải nghiệm (§19.7, §25.4)
- [x] `rememberEffectiveReduceMotion` kết hợp cài đặt trong app và `Settings.Global.ANIMATOR_DURATION_SCALE` của hệ thống Android.
- [x] Mô tả ngữ nghĩa TalkBack tiếng Việt trên `XiangqiBoard`, `EvaluationBar`, và `AdvantageChart`.
- [x] Chuyển màn hình `NavHost` dùng `fadeIn`/`fadeOut` ngắn (180ms/140ms) và tự động tắt khi bật Giảm chuyển động.

## 5. Xây dựng & Kiểm thử bản phát hành trên Android Studio
Chạy trên máy có Android SDK / JDK 17+:
```bash
./gradlew clean testDebugUnitTest assembleDebug
./gradlew assembleRelease
```
Kiểm tra lại trên thiết bị thật (Android 10+ / API 29+):
- [ ] Khởi động lạnh, tạo ván mới, chơi hoàn chỉnh 1 ván Luyện tập & 1 ván Thử sức.
- [ ] Gợi ý 3 tầng, phân tích sau ván, lưu bài tập từ sai lầm, giải bài tập & lặp lại ngắt quãng.
- [ ] Trình dựng thế cờ → Đấu với máy / Phân tích / Lưu bài tập.
- [ ] Xuất & nhập bản sao lưu JSON (chế độ Hợp nhất và Thay thế).
- [ ] Kiểm tra nâng cấp cơ sở dữ liệu Room (v1 → v2 → v3) giữ nguyên lịch sử ván đấu.
