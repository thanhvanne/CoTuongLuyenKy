# ĐẶC TẢ BỘ LUẬT CỜ TƯỚNG & QUY ƯỚC DỮ LIỆU (PHASE 1)

Tài liệu này chốt các quyết định kỹ thuật theo Mục 5 và Mục 30 của `DAC_TA_DU_AN_CO_TUONG.md`.

## 1. Bộ luật mặc định: `WXF_BASIC_V1`

Dựa trên quy chuẩn Liên đoàn Cờ Tướng Thế Giới (WXF) / Liên đoàn Cờ Tướng Việt Nam:

1. **Bàn cờ**: 9 cột (file `0..8`, tương ứng `a..i`) × 10 hàng (rank `0..9`).
   - Hàng `0..4`: phần sân Đỏ (Cung Đỏ: file `3..5`, rank `0..2`).
   - Giữa hàng 4 và hàng 5: sông `SỞ HÀ – HÁN GIỚI`.
   - Hàng `5..9`: phần sân Đen (Cung Đen: file `3..5`, rank `7..9`).
   - Đỏ đi trước mặc định.
2. **Luật di chuyển**:
   - Đầy đủ luật Tướng, Sĩ, Tượng (không qua sông, cản mắt), Xe, Pháo (ăn quân cần đúng 1 ngòi), Mã (cản chân), Tốt (chưa qua sông chỉ tiến, qua sông được tiến hoặc ngang).
   - **Cấm hai Tướng trực diện** (lộ mặt Tướng): không cho phép nước đi khiến hai Tướng đứng cùng cột mà không có quân nào chắn giữa.
   - **Cấm tự chiếu**: sau nước đi, Tướng bên đi không được nằm trong tầm chiếu.
3. **Kết thúc ván**:
   - **Chiếu bí (`CHECKMATE`)**: bên tới lượt đang bị chiếu và không có nước đi hợp lệ → **thua**.
   - **Hết nước đi hợp lệ / Khốn bí (`STALEMATE`)**: bên tới lượt không bị chiếu nhưng không còn nước đi hợp lệ → **thua** (đặc trưng luật Cờ Tướng, khác Cờ Vua).
   - **Luật 60 nước không ăn quân (`DRAW_SIXTY_MOVE_RULE`)**: 120 nửa lượt liên tiếp (`halfMoveClock >= 120`) không có quân bị ăn → **hòa**.
   - **Không đủ lực chiếu bí (`DRAW_INSUFFICIENT_MATERIAL`)**: cả hai bên không còn Xe, Pháo, Mã hoặc Tốt → **hòa**.
   - **Xin thua (`RESIGNATION`)**, **Hết giờ (`TIMEOUT`)**.
4. **Xử lý lặp thế & chiếu kéo dài (phạm vi Phase 1)**:
   - Mỗi thế cờ được định danh bằng khóa Zobrist 64-bit (`positionHash`, bao gồm vị trí tất cả quân và bên đi).
   - Khi một `positionHash` lặp lại lần thứ 3 trong ván:
     - Nếu bên vừa tạo ra thế lặp đã **chiếu liên tục** tất cả các nước của họ trong chu kỳ lặp → xử thua do **Chiếu kéo dài (`PERPETUAL_CHECK_LOSS`)**.
     - Ngược lại trong Phase 1 → xử **Hòa do lặp thế (`DRAW_REPETITION`)**.
   - **Giới hạn ghi nhận rõ (theo Mục 26 Phase 1)**: Các quy tắc phân xử **đuổi quân kéo dài (trường tróc / perpetual chase)** tinh vi theo WXF/luật Trung Quốc (phân biệt quân có gốc/không gốc, Tốt chưa qua sông, v.v.) **chưa tuyên bố hỗ trợ đầy đủ ở Phase 1** và sẽ được hoàn thiện ở Phase 4.

## 2. Quy ước FEN (`FenCodec`)

Chuẩn UCI/Pikafish:
- Thế khởi đầu:
  `rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1`
- Hàng duyệt từ `rank = 9` (đáy Đen) xuống `rank = 0` (đáy Đỏ).
- Quân Đỏ viết HOA: `K` (Tướng), `A` (Sĩ), `B` (Tượng; chấp nhận `E` khi đọc), `R` (Xe), `C` (Pháo), `N` (Mã; chấp nhận `H` khi đọc), `P` (Tốt).
- Quân Đen viết thường: `k`, `a`, `b`/`e`, `r`, `c`, `n`/`h`, `p`.
- Bên đi: `w` (hoặc `r` khi đọc) = Đỏ, `b` = Đen.
- Hai trường tiếp theo là `- -`, tiếp đến `halfMoveClock` (số nửa lượt không ăn quân) và `fullMoveNumber` (bắt đầu từ 1).

## 3. Quy ước ký phổ (`XiangqiNotation`)

- Lưu trữ nội bộ **luôn** dùng tọa độ UCI không nhập nhằng (`fromSquare` + `toSquare`, ví dụ `h2e2`).
- Đánh số cột hiển thị (1..9 từ phải sang trái theo góc nhìn bên đi):
  - Đỏ: file `8 -> 1`, ..., file `0 -> 9`.
  - Đen: file `0 -> 1`, ..., file `8 -> 9`.
- Hướng di chuyển:
  - Ngang: `bình` (`-`) + cột đích.
  - Tiến: `tấn` (`.`) + số bước (quân đi thẳng) hoặc cột đích (Sĩ, Tượng, Mã).
  - Lùi: `thoái` (`/`) + số bước (quân đi thẳng) hoặc cột đích (Sĩ, Tượng, Mã).
- Phân biệt 2 quân cùng loại trên cùng một cột: `Tiền` (quân tiến xa hơn về phía đối thủ) / `Hậu` (quân phía sau); với 3+ Tốt cùng cột: đánh số từ trước ra sau (`1..5`).
