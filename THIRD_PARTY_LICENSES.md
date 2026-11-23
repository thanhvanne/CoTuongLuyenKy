# Giấy phép thành phần nguồn mở (Third-Party Licenses)

**Ứng dụng:** Cờ Tướng – Luyện Kỳ (`com.thanhvan.cotuongluyenky`)  
**Mô hình hoạt động:** 100% Offline, không quảng cáo, không theo dõi người dùng.

---

## 1. Engine Cờ Tướng (tích hợp native tùy chọn)

- **Pikafish** (Xiangqi NNUE Engine)
  - Kho mã nguồn: https://github.com/official-pikafish/Pikafish
  - Giấy phép: **GNU General Public License v3.0 (GPL-3.0)**
  - Ghi chú tuân thủ: Khi phân phối bản phát hành có đóng gói binary Pikafish trong `jniLibs/`, cần đính kèm đầy đủ thông báo bản quyền GPL-3.0 và hướng dẫn truy cập mã nguồn tương ứng theo quy định GPL-3.0 (xem `docs/ENGINE_INTEGRATION.md`).

---

## 2. Thư viện AndroidX & Kotlin (Apache License 2.0)

- **Jetpack Compose** (`androidx.compose.*`) — Apache-2.0
- **AndroidX Room** (`androidx.room.*`) — Apache-2.0
- **AndroidX DataStore** (`androidx.datastore.*`) — Apache-2.0
- **AndroidX Navigation Compose** (`androidx.navigation.*`) — Apache-2.0
- **AndroidX Lifecycle & Core KTX** (`androidx.lifecycle.*`, `androidx.core.*`) — Apache-2.0
- **Kotlin Standard Library & Kotlinx Coroutines** — Apache-2.0

---

## 3. Tài nguyên nội bộ

- **Âm thanh (`res/raw/sfx_*.wav`)**: Được tổng hợp số hoàn toàn nội bộ bằng mã nguồn trong dự án, không sao chép từ kho âm thanh bên thứ ba.
- **Đồ họa bàn cờ & quân cờ**: Dựng bằng Vector Canvas trực tiếp trong Jetpack Compose (`XiangqiBoard.kt`), hỗ trợ chữ Hán truyền thống và chữ Việt rõ nét.
