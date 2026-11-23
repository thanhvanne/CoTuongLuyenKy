# Công thức Độ chính xác Tham khảo (`ref-acc-v1`)

Tài liệu hóa công thức độ chính xác theo Mục 16.3 & 26 (Phase 4) của `DAC_TA_DU_AN_CO_TUONG.md`.

## 1. Nguồn dữ liệu hợp lệ

- **Chỉ tính** trên các nước đi của người chơi đã được **phân tích đầy đủ cuối ván** (`isQuickLiveEval = false`).
- **Không tính** trên đánh giá nhanh trực tiếp trong ván.
- **Tách biệt hoàn toàn** giữa chế độ **Luyện tập (`PRACTICE`)** và **Thử sức (`CHALLENGE`)**.
- **Ngưỡng hiển thị tối thiểu**: chỉ hiển thị khi có ít nhất **10 nước** đã phân tích đầy đủ ở chế độ tương ứng.

## 2. Công thức

Với mỗi nước đi hợp lệ của người chơi:

1. Tính tổn thất điểm `cpLoss >= 0` theo góc nhìn bên đi từ `scoreBeforeRed` và `scoreAfterRed` (bị chặn trên ở `1200 cp` để tránh một sai lầm đơn lẻ kéo điểm về 0 tuyệt đối).
2. Nếu người chơi đi đúng `bestMove` của engine: `moveScore = 100.0`.
3. Ngược lại:
   ```text
   moveScore = 100.0 * exp(-0.0035 * min(cpLoss, 1200))
   ```
   Bảng tham chiếu nhanh:
   - `cpLoss = 0` → `100.0`
   - `cpLoss = 35` (ngưỡng `Tốt`) → `~88.5`
   - `cpLoss = 85` (ngưỡng `Chấp nhận được`) → `~74.3`
   - `cpLoss = 170` (ngưỡng `Chưa chính xác`) → `~55.2`
   - `cpLoss = 320` (ngưỡng `Sai lầm`) → `~32.6`
4. Điểm tham khảo của chế độ = trung bình cộng `moveScore` làm tròn về `[0..100]`.

## 3. Lưu ý minh bạch

- Đây là **chỉ số tham khảo nội bộ** gắn liền với phiên bản engine và độ sâu phân tích trên thiết bị.
- **Không dùng để so sánh trực tiếp** với điểm accuracy/CAPS của các nền tảng cờ khác.
