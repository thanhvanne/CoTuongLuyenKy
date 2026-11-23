Dưới đây là nội dung file **`DAC_TA_DU_AN_CO_TUONG.md`**. Mày có thể tạo file cùng tên trong thư mục gốc của dự án rồi copy toàn bộ nội dung này vào để lưu trữ và dùng làm tài liệu triển khai.

```md
# ĐẶC TẢ DỰ ÁN: ỨNG DỤNG LUYỆN CỜ TƯỚNG ANDROID

> Tên tạm: **Cờ Tướng – Luyện Kỳ**
>
> Phiên bản tài liệu: 1.0  
> Nền tảng: Android 10 trở lên  
> Công cụ phát triển: Android Studio  
> Ngôn ngữ chính: Kotlin  
> Giao diện: Jetpack Compose  
> Engine dự kiến: Pikafish  
> Mục tiêu: Chơi cờ tướng với máy và cải thiện trình độ thông qua phân tích, giải thích và luyện tập cá nhân hóa.

---

# 1. TỔNG QUAN DỰ ÁN

## 1.1. Mục tiêu sản phẩm

Xây dựng một ứng dụng cờ tướng trên điện thoại Android có khả năng:

1. Đánh với máy ở nhiều cấp độ.
2. Đánh giá nước đi trong lúc chơi.
3. Cho phép đi lại, xem gợi ý và thử biến.
4. Phân tích toàn bộ ván sau khi kết thúc.
5. Hiển thị lại bàn cờ theo từng nước.
6. Đưa ra nhận xét dựa trên thế cờ và kết quả phân tích.
7. Gợi ý nước tốt hơn và minh họa nhiều nước tiếp theo.
8. Biến những sai lầm của người chơi thành bài tập.
9. Theo dõi quá trình luyện tập và tiến bộ.
10. Hoạt động ổn định trên Android 10 trở lên.

Ứng dụng hướng tới việc **giúp người chơi lên trình**, không chỉ tạo ra một bàn cờ để đánh với máy.

## 1.2. Vòng lặp trải nghiệm chính

```text
Chơi với máy
    ↓
Nhận biết tình huống quan trọng
    ↓
Kết thúc ván
    ↓
Phân tích các bước ngoặt
    ↓
Tự giải lại thế cờ
    ↓
Xem lời giải và các biến
    ↓
Ôn tập sau một khoảng thời gian
    ↓
Chơi lại và cải thiện
```

## 1.3. Đối tượng sử dụng

- Người mới học cờ tướng.
- Người đã biết luật nhưng thường mắc lỗi chiến thuật.
- Người muốn luyện khai cuộc, trung cuộc và tàn cuộc.
- Người muốn phân tích lại các ván đã chơi.
- Người cần một ứng dụng có thể sử dụng offline.

## 1.4. Nguyên tắc thiết kế

1. **Đúng luật trước, đẹp sau.**
2. Engine chịu trách nhiệm tính toán, không tự suy diễn lời giải thích.
3. Không khẳng định một kết luận chiến thuật khi chưa có đủ căn cứ.
4. Mọi biến hiển thị phải được kiểm tra hợp lệ.
5. Giao diện ưu tiên bàn cờ và khả năng học.
6. Phản hồi trực tiếp không được gây giật hoặc khóa giao diện.
7. Người chơi có quyền tắt mọi trợ giúp trong khi đấu.
8. Bài tập phải tạo điều kiện cho người dùng tự suy nghĩ trước khi xem đáp án.
9. Chức năng có thể hoạt động offline phải ưu tiên hoạt động offline.
10. Không quảng cáo các chỉ số như Elo, độ chính xác hoặc cấp máy là chuẩn quốc tế nếu chưa có kiểm chứng.

---

# 2. PHẠM VI DỰ ÁN

## 2.1. Các chức năng chính

- Đấu với máy.
- Nhiều cấp độ máy.
- Chọn cầm đỏ, đen hoặc ngẫu nhiên.
- Chế độ luyện tập.
- Chế độ thử sức.
- Đồng hồ tùy chọn.
- Gợi ý nhiều tầng.
- Đi lại.
- Xoay bàn.
- Đánh giá nước đi trực tiếp.
- Kiểm tra quân nguy hiểm.
- Xem lịch sử nước đi.
- Lưu và tiếp tục ván dở.
- Phân tích cuối ván.
- Xem từng nước trên bàn cờ.
- Biểu đồ lợi thế.
- Nhận xét chiến thuật.
- Xem biến engine.
- Đánh thử từ thế bất kỳ.
- Lưu thế thành bài tập.
- Tạo bài tập từ sai lầm.
- Ôn tập ngắt quãng.
- Thống kê tiến bộ.
- Nhập, xuất thế cờ và ván đấu.
- Cài đặt giao diện, âm thanh, rung và mức phân tích.
- Hoạt động offline.
- Tùy chọn đồng bộ tài khoản ở giai đoạn phát triển sau.
- Tùy chọn AI ngôn ngữ để diễn đạt lời giải thích ở giai đoạn phát triển sau.

## 2.2. Không thuộc phạm vi ban đầu

- Đánh online giữa hai người.
- Chat giữa người dùng.
- Giải đấu online.
- Thanh toán hoặc mua vật phẩm.
- Xếp hạng quốc tế.
- Phát trực tiếp ván cờ.
- Nhận diện bàn cờ từ ảnh.

Các chức năng này có thể được xem xét sau khi lõi chơi và luyện tập đã ổn định.

---

# 3. CÔNG NGHỆ VÀ NỀN TẢNG

## 3.1. Công nghệ

| Hạng mục | Lựa chọn |
|---|---|
| IDE | Android Studio |
| Ngôn ngữ | Kotlin |
| Giao diện | Jetpack Compose |
| Kiến trúc | MVVM kết hợp phân lớp domain/data |
| Điều hướng | Navigation Compose |
| Bất đồng bộ | Kotlin Coroutines và Flow |
| Lưu dữ liệu | Room/SQLite |
| Cài đặt | DataStore |
| Tác vụ dài phù hợp | WorkManager khi đáp ứng ràng buộc Android |
| Âm thanh ngắn | SoundPool |
| Rung | Android haptic feedback / vibrator APIs |
| Engine | Pikafish, tích hợp native |
| Native bridge | Android NDK và JNI |
| Kiểm thử | JUnit, kiểm thử Compose và kiểm thử trên thiết bị |

## 3.2. Phiên bản Android

- `minSdk`: **29**, tương ứng Android 10.
- `targetSdk`: sử dụng phiên bản Android SDK ổn định mới nhất phù hợp tại thời điểm phát hành.
- Cần kiểm thử trên nhiều phiên bản Android, tối thiểu:
  - Android 10.
  - Một phiên bản Android trung gian.
  - Phiên bản Android mới đang phổ biến.

## 3.3. Định hướng thiết bị

- Ưu tiên điện thoại màn hình dọc.
- Hỗ trợ màn hình lớn bằng bố cục thích ứng.
- Hoạt động trong vùng an toàn của màn hình, tránh tai thỏ và thanh hệ thống.
- Không giả định mọi điện thoại có cùng tỉ lệ màn hình.
- Không yêu cầu kết nối mạng cho chức năng chơi, lưu và phân tích offline.

## 3.4. Lưu ý về dependency

Không ghi cố định phiên bản thư viện trong tài liệu này nếu chưa được kiểm chứng tại thời điểm triển khai.

Khi khởi tạo dự án:

1. Chọn phiên bản ổn định tương thích của Kotlin, Compose, Gradle và Android Gradle Plugin.
2. Quản lý phiên bản bằng Version Catalog.
3. Khóa hoặc ghi rõ phiên bản dependency trong dự án.
4. Chạy lại toàn bộ kiểm thử trước khi cập nhật thư viện.

---

# 4. NGUỒN ENGINE, LUẬT VÀ DỮ LIỆU

## 4.1. Engine đề xuất: Pikafish

Nguồn:

https://github.com/official-pikafish/Pikafish

Sử dụng engine cho:

- Đánh với máy.
- Gợi ý nước đi.
- Đánh giá thế cờ.
- Phân tích ván.
- Tìm biến đề xuất.
- Tìm phương án chiếu bí khi engine phát hiện được.
- Phân tích nhiều phương án nếu phiên bản tích hợp hỗ trợ.

## 4.2. Engine thay thế hoặc đối chiếu

Fairy-Stockfish:

https://github.com/fairy-stockfish/Fairy-Stockfish

Có thể sử dụng để:

- Thử nghiệm kỹ thuật.
- Đối chiếu một số kết quả.
- Làm phương án thay thế nếu phù hợp giấy phép và yêu cầu sản phẩm.

Không mặc định hai engine:

- Dùng chung thang điểm.
- Đánh giá thế giống nhau.
- Xử lý mọi trường hợp lặp thế giống nhau.
- Có cùng tính năng và tùy chọn cấu hình.

## 4.3. Giấy phép

Trước khi phân phối ứng dụng:

1. Kiểm tra giấy phép chính xác của:
   - Engine.
   - Các thay đổi trên engine.
   - Mạng đánh giá hoặc tài nguyên đi kèm.
   - Thư viện luật.
   - Bộ dữ liệu ván đấu.
   - Font chữ, hình ảnh và âm thanh.

2. Xác định nghĩa vụ khi phân phối:
   - Thông báo bản quyền.
   - Cung cấp giấy phép.
   - Nghĩa vụ cung cấp mã nguồn tương ứng, nếu có.
   - Các điều kiện đối với ứng dụng tích hợp.

3. Hiển thị trong ứng dụng:
   - Tên thành phần.
   - Phiên bản.
   - Tác giả hoặc dự án nguồn.
   - Nội dung giấy phép liên quan.
   - Đường dẫn mã nguồn khi cần thiết.

**Lưu ý:** Không giả định việc gọi engine qua JNI hoặc một giao diện trung gian tự động loại bỏ nghĩa vụ của giấy phép. Cần đánh giá giấy phép theo cách tích hợp và phân phối thực tế.

## 4.4. Nguồn ván đấu và bài tập

Ưu tiên:

1. Ván do người dùng tự chơi.
2. Bài tập được tạo từ sai lầm trong ván của người dùng.
3. Các bài tập tự biên soạn và kiểm chứng.
4. Bộ dữ liệu công khai có giấy phép cho phép sử dụng.
5. Ván do người dùng tự nhập.

Không tự ý sao chép:

- Bộ bài tập của ứng dụng khác.
- Bình luận có bản quyền.
- Hình ảnh, âm thanh hoặc dữ liệu trả phí.
- Cơ sở dữ liệu ván đấu chưa rõ quyền sử dụng.

## 4.5. Nguồn khai cuộc

Tạo cơ sở dữ liệu khai cuộc nhỏ trước, sau đó mở rộng.

Ví dụ nhóm khai cuộc:

- Trung Pháo.
- Bình Phong Mã.
- Tiên Nhân Chỉ Lộ.
- Phi Tượng Cục.
- Các hệ thống khác được kiểm chứng.

Mỗi mục cần có:

- Tên.
- Nguồn.
- Chuỗi nước hoặc điều kiện nhận diện.
- Các tên gọi thay thế nếu có.
- Ghi chú về mức độ chính xác của nhận diện.

Không gán tên khai cuộc chỉ dựa vào một nước đi nếu không đủ điều kiện nhận diện.

---

# 5. LUẬT CỜ VÀ BIỂU DIỄN DỮ LIỆU

## 5.1. Mô hình bàn cờ

- Bàn cờ có **9 cột** và **10 hàng**.
- Quân cờ đứng tại giao điểm.
- Có sông ở giữa bàn.
- Mỗi bên có cung Tướng.
- Phải hỗ trợ xoay bàn mà không thay đổi dữ liệu thế cờ.

## 5.2. Hai bên

- Đỏ.
- Đen.

Mặc định:

- Đỏ đi trước.
- Người chơi có thể chọn cầm đỏ, đen hoặc ngẫu nhiên.

## 5.3. Các loại quân

- Tướng.
- Sĩ.
- Tượng.
- Xe.
- Pháo.
- Mã.
- Tốt.

Hỗ trợ giao diện quân:

1. Chữ Hán truyền thống.
2. Tên tiếng Việt hoặc ký hiệu dễ đọc.

Hai bộ quân phải biểu diễn cùng một dữ liệu logic.

## 5.4. Luật di chuyển cơ bản

Lớp luật phải kiểm tra đầy đủ:

- Quân đi đúng quy tắc.
- Không đi ra ngoài bàn.
- Không ăn quân cùng bên.
- Mã không nhảy khi bị cản chân.
- Tượng không đi qua sông và không đi khi bị cản mắt.
- Pháo ăn quân phải có đúng một quân làm ngòi trên đường.
- Sĩ và Tướng di chuyển đúng giới hạn cung.
- Hai Tướng không được trực diện trái luật.
- Không được để Tướng của mình bị chiếu sau khi đi.
- Xử lý chính xác các tình huống chiếu và hết nước hợp lệ.

## 5.5. Luật lặp thế, chiếu và đuổi

Đây là phần dễ sai và có thể khác nhau giữa các bộ luật.

Yêu cầu:

1. Chọn và ghi rõ **một bộ luật mặc định cụ thể** trước khi triển khai luật nâng cao.
2. Tài liệu hóa:
   - Quy định về lặp thế.
   - Chiếu kéo dài.
   - Đuổi quân kéo dài.
   - Các trường hợp hòa hoặc xử thua.
   - Quy tắc kết thúc khác nếu bộ luật áp dụng có quy định.
3. Tạo bộ test riêng cho từng trường hợp.
4. Kiểm tra sự tương thích giữa:
   - Lớp luật của ứng dụng.
   - Engine.
   - Kết quả được hiển thị cho người dùng.

Nếu engine và lớp luật xử lý khác nhau:

- Không tự ý kết luận theo một bên mà không có chính sách rõ ràng.
- Ghi log để kiểm thử.
- Ưu tiên quyết định của lớp luật đã triển khai theo bộ luật ứng dụng công bố.
- Không hiển thị nhận xét chắc chắn khi kết quả còn mâu thuẫn.

## 5.6. Trạng thái kết thúc ván

Tối thiểu cần hỗ trợ:

- Một bên bị chiếu bí.
- Một bên không còn nước đi hợp lệ, xử lý theo bộ luật được chọn.
- Người chơi xin thua.
- Hết giờ khi bật đồng hồ.
- Hòa theo quy tắc đã triển khai.
- Kết quả do quy tắc lặp/chiếu/đuổi, nếu áp dụng.

Mỗi kết quả phải lưu:

- Bên thắng hoặc hòa.
- Lý do kết thúc.
- Thời điểm kết thúc.

## 5.7. FEN cờ tướng

Ứng dụng hỗ trợ nhập và xuất FEN theo một quy ước cờ tướng được tài liệu hóa.

Khi nhập:

- Kiểm tra định dạng.
- Kiểm tra số hàng, số cột.
- Kiểm tra ký hiệu quân.
- Kiểm tra bên đi.
- Kiểm tra các điều kiện hợp lệ có thể xác minh.
- Báo lỗi dễ hiểu nếu thế không hợp lệ.

### Lưu ý quan trọng

FEN là biểu diễn một thế cờ tại thời điểm nhất định, không nhất thiết chứa đủ lịch sử để xác định các quy tắc lặp.

Khi nhập thế cờ chỉ bằng FEN:

> “Thế cờ được nhập không kèm toàn bộ lịch sử. Một số quy tắc liên quan đến lặp thế có thể không xác định đầy đủ.”

## 5.8. Ký phổ

Ứng dụng cần:

- Lưu dữ liệu nước đi ở dạng tọa độ nội bộ không nhập nhằng.
- Hiển thị ký phổ cờ tướng theo quy ước đã chọn.
- Có thể hiển thị:
  - Dạng rút gọn, ví dụ `P2-5`.
  - Dạng đầy đủ, ví dụ `Pháo 2 bình 5`.

Cần đặc tả và kiểm thử:

- Hướng đánh số cột.
- Tấn, thoái, bình.
- Hai quân cùng loại trên một cột.
- Sự khác nhau giữa góc nhìn đỏ và đen.
- Chuyển đổi chính xác giữa tọa độ và ký phổ.

Không dùng chuỗi ký phổ hiển thị làm nguồn dữ liệu duy nhất để phục hồi ván.

---

# 6. TÍCH HỢP ENGINE

## 6.1. Nguyên tắc

Ứng dụng và engine giao tiếp qua một lớp trung gian:

```text
UI
 ↓
ViewModel
 ↓
Engine Manager
 ↓
Native Bridge / JNI
 ↓
Pikafish
```

Lớp trung gian phải che giấu chi tiết native khỏi giao diện và domain.

## 6.2. Prototype bắt buộc trước khi phát triển sâu

Trước khi xây toàn bộ chức năng:

1. Biên dịch engine cho Android bằng NDK.
2. Xác định các ABI được hỗ trợ.
3. Gọi khởi tạo engine từ Kotlin.
4. Gửi một thế cờ và yêu cầu tính nước.
5. Nhận kết quả và biến.
6. Dừng tác vụ đang tính.
7. Chạy thử trên thiết bị Android thật.
8. Đo:
   - Thời gian khởi động.
   - Dung lượng RAM.
   - Mức sử dụng CPU.
   - Mức nóng máy.
   - Kích thước ứng dụng.
9. Xác nhận cách đóng gói mạng đánh giá và giấy phép.

Không giả định bản engine desktop có thể đưa nguyên vào Android mà không cần điều chỉnh.

## 6.3. Cách gọi engine

Thiết kế interface độc lập với phương thức triển khai:

```kotlin
interface XiangqiEngine {
    suspend fun initialize(config: EngineConfig)

    suspend fun findBestMove(
        position: PositionWithHistory,
        limit: SearchLimit
    ): EngineMoveResult

    suspend fun analyze(
        position: PositionWithHistory,
        limit: SearchLimit,
        variations: Int
    ): EngineAnalysis

    suspend fun stop()

    suspend fun close()
}
```

Đây là interface định hướng. Kiểu dữ liệu thực tế có thể thay đổi trong quá trình triển khai.

## 6.4. Yêu cầu xử lý bất đồng bộ

- Không chạy engine trên main thread.
- Chỉ có một tác vụ điều khiển engine tại một thời điểm, trừ khi chủ động dùng nhiều instance và đã đo hiệu năng.
- Hủy phân tích cũ khi:
  - Người dùng đi nước mới.
  - Người dùng đi lại.
  - Người dùng chuyển sang ván khác.
  - Người dùng rời màn hình.
- Không cập nhật UI từ kết quả đã lỗi thời.
- Dùng `requestId` hoặc `positionHash` để đối chiếu kết quả với thế hiện tại.
- Nếu engine lỗi:
  - Không làm ứng dụng đóng đột ngột.
  - Cho phép thử lại hoặc khởi tạo lại.
  - Bảo toàn dữ liệu ván.

## 6.5. Thông tin nhận từ engine

Tùy phiên bản engine và giao thức hỗ trợ:

- Nước tốt nhất.
- Điểm đánh giá.
- Kết quả chiếu bí nếu engine báo.
- Độ sâu tìm kiếm.
- Số node hoặc thời gian tính.
- Biến chính.
- Nhiều biến ứng viên nếu hỗ trợ.
- Các trạng thái hoặc thông báo lỗi.

Không giả định mọi phiên bản Pikafish đều cung cấp cùng tùy chọn. Cần kiểm tra tài liệu và kết quả thực tế của phiên bản được tích hợp.

## 6.6. Giới hạn tài nguyên

Có ba chế độ:

### Tiết kiệm

- Số luồng thấp.
- Giới hạn thời gian ngắn.
- Dùng cho thiết bị yếu hoặc tiết kiệm pin.

### Cân bằng

- Mặc định.
- Đánh giá nhanh trong ván.
- Phân tích sau ván ở mức vừa phải.

### Phân tích sâu

- Dùng nhiều thời gian hơn.
- Cảnh báo có thể nóng máy và tốn pin.
- Cho phép người dùng dừng bất cứ lúc nào.

Các giá trị thời gian, số luồng và bộ nhớ phải được điều chỉnh sau khi đo trên thiết bị thật; không đặt cứng một con số và cho rằng phù hợp mọi máy.

## 6.7. Chạy nền

- Không hứa phân tích toàn bộ ván vô điều kiện khi ứng dụng bị đóng hoặc hệ thống hạn chế tác vụ nền.
- Nếu sử dụng WorkManager hoặc foreground service, phải tuân thủ chính sách Android hiện hành.
- Ưu tiên:
  - Lưu tiến độ sau mỗi thế đã phân tích.
  - Khi bị gián đoạn, cho phép tiếp tục.
  - Hiển thị rõ trạng thái.
- Phân tích đang chạy trong màn hình phải dừng hoặc điều chỉnh hợp lý khi ứng dụng xuống nền.

---

# 7. CẤP ĐỘ MÁY

## 7.1. Thiết kế cấp độ

Dự kiến có 10 cấp:

| Cấp | Mô tả |
|---|---|
| 1 | Dành cho người mới biết luật |
| 2 | Chơi đơn giản, có sai sót |
| 3 | Biết phản ứng trước nguy cơ rõ ràng |
| 4 | Giữ quân khá hơn |
| 5 | Biết chiến thuật cơ bản |
| 6 | Có khả năng phản đòn |
| 7 | Chơi tương đối chắc |
| 8 | Tính toán sâu hơn |
| 9 | Khó đối với người chơi thông thường |
| 10 | Mức mạnh theo giới hạn engine và thiết bị |

Các mô tả chỉ là mục tiêu thiết kế, cần kiểm chứng bằng ván thử.

## 7.2. Cách tạo cấp độ

Kết hợp:

- Giới hạn thời gian tìm kiếm.
- Giới hạn độ sâu hoặc số node nếu engine hỗ trợ.
- Lấy nhiều nước ứng viên nếu hỗ trợ.
- Chọn nước theo phân phối phù hợp cấp độ.
- Giảm tần suất chọn nước sai nghiêm trọng.
- Kiểm tra nước được chọn luôn hợp lệ.

Không chỉ dùng ngẫu nhiên hoàn toàn.

Không tuyên bố cấp độ tương ứng một mức Elo cụ thể nếu chưa có quy trình đo đáng tin.

## 7.3. Máy vừa sức

Tính năng tùy chọn:

- Sau một số ván, đề xuất tăng hoặc giảm cấp.
- Dựa trên:
  - Kết quả.
  - Tần suất sai lầm.
  - Mức trợ giúp đã dùng.
- Chỉ đề xuất, không tự ép thay đổi.
- Không dùng các ván có nhiều gợi ý như bằng chứng duy nhất để tăng cấp.

---

# 8. CHẾ ĐỘ CHƠI

## 8.1. Chế độ luyện tập

Cho phép:

- Đi lại không giới hạn.
- Gợi ý nhiều tầng.
- Nhận xét trực tiếp.
- Xem thanh đánh giá.
- Xem quân nguy hiểm.
- Thử lại từ một thời điểm trước đó.

Kết quả được đánh dấu là ván luyện tập.

## 8.2. Chế độ thử sức

Mặc định:

- Không gợi ý trong ván.
- Không đi lại.
- Không hiện đánh giá trực tiếp.
- Vẫn được phân tích sau ván.

Người dùng có thể cấu hình riêng nếu sản phẩm cho phép, nhưng màn hình kết quả phải ghi rõ các trợ giúp thực sự đã dùng.

## 8.3. Đồng hồ

Tùy chọn:

- Không giới hạn.
- Giới hạn thời gian cho mỗi bên.
- Có thể mở rộng các chế độ cộng thời gian sau.

Yêu cầu:

- Đồng hồ tiếp tục hoạt động đúng khi chuyển màn hình theo chính sách ván đấu.
- Lưu thời gian còn lại khi tạm dừng hoặc hệ thống đóng ứng dụng.
- Quy định rõ chế độ nào cho phép tạm dừng.
- Hết giờ phải có kết quả và lý do rõ ràng.

---

# 9. ĐÁNH GIÁ NƯỚC ĐI TRỰC TIẾP

## 9.1. Mục đích

Sau nước của người chơi, ứng dụng có thể thông báo ngắn:

- Tốt.
- Chấp nhận được.
- Chưa chính xác `?!`.
- Sai lầm `?`.
- Sai lầm nghiêm trọng `??`.

Nhãn chỉ là kết quả phân tích của engine theo giới hạn thời gian hiện tại.

## 9.2. Quy trình

1. Lưu thế cờ và lịch sử trước nước.
2. Phân tích hoặc sử dụng kết quả phân tích hợp lệ đã lưu.
3. Xác định phương án tham chiếu tốt nhất.
4. Đánh giá nước người chơi đã chọn.
5. Chuẩn hóa điểm theo cùng góc nhìn.
6. So sánh kết quả.
7. Kiểm tra các sự kiện đặc biệt:
   - Bỏ lỡ biến chiếu bí.
   - Tự dẫn đến biến thua cưỡng bức mà engine tìm thấy.
   - Thay đổi lớn về lợi thế.
8. Đưa ra nhãn và nhận xét có căn cứ.
9. Cập nhật UI nếu kết quả vẫn thuộc thế đang xem.

## 9.3. Đánh giá nhanh và sâu

Trong ván:

- Dùng phân tích nhanh.
- Gắn nhãn: **Đánh giá nhanh**.
- Có thể thay đổi sau phân tích sâu.

Sau ván:

- Phân tích sâu hơn theo cấu hình.
- Kết quả cuối ván có thể khác nhận xét trực tiếp.
- Nếu khác, ưu tiên kết quả phân tích sau ván và hiển thị rõ.

## 9.4. Ngưỡng đánh giá

- Không cố định ngưỡng mất điểm khi chưa thử nghiệm.
- Ngưỡng phải được hiệu chỉnh với đúng:
  - Phiên bản engine.
  - Cấu hình tìm kiếm.
  - Cách chuẩn hóa điểm.
- Lưu cấu hình phân tích.
- Kiểm thử trên bộ ván mẫu có người hiểu cờ đánh giá.

Không so sánh trực tiếp điểm của các engine khác nhau như thể cùng một thang đo.

## 9.5. Nhãn “nước hay”

Không tự gắn `!` chỉ vì nước đó là nước tốt nhất.

Trong bản đầu:

- Dùng nhãn **Tốt**.
- Chỉ dùng **Nước hay** khi có tiêu chí riêng được tài liệu hóa và kiểm chứng.

## 9.6. Tùy chọn hiển thị

Người dùng có thể:

- Bật/tắt đánh giá trực tiếp.
- Chỉ hiện nhãn, không hiện biến.
- Hiện nhận xét sau mỗi nước.
- Chỉ cảnh báo sai lầm lớn.
- Tắt thanh lợi thế.

---

# 10. GỢI Ý VÀ MINH HỌA BIẾN

## 10.1. Các tầng gợi ý

### Tầng 1: Gợi ý ý tưởng

Ví dụ:

> “Hãy tìm một nước có thể tạo sức ép lên quân đối thủ.”

Chỉ hiển thị khi bộ nhận diện có đủ căn cứ. Nếu không:

> “Hãy xem kỹ các nước có thể tạo sức ép.”

### Tầng 2: Chỉ quân

- Làm sáng quân được đề xuất.
- Chưa hiển thị điểm đến.

### Tầng 3: Chỉ nước

- Hiển thị điểm đi và điểm đến.
- Hiển thị ký phổ.
- Vẽ một mũi tên rõ ràng.

### Tầng 4: Xem biến

- Hiển thị chuỗi nước dự kiến.
- Có nút đi từng nước.
- Có nhận xét nếu đủ dữ kiện.
- Có thể xem phương án khác nếu engine hỗ trợ.

## 10.2. Nguyên tắc hiển thị biến

- Mỗi bước cập nhật lại thế cờ.
- Mặc định chỉ vẽ mũi tên của nước đang xem.
- Có thể bật chế độ tổng quan với một số mũi tên đánh số.
- Màu và kiểu nét phải phân biệt:
  - Nước của bên đang học.
  - Nước đáp của đối thủ.
- Không hiển thị mũi tên dẫn tới nước không hợp lệ.
- Biến phải được kiểm tra bởi lớp luật trước khi hiển thị.
- Nói rõ:

> “Đây là một phương án engine dự kiến, không phải đối thủ bắt buộc đi như vậy.”

Chỉ dùng từ “buộc” nếu đã có căn cứ phù hợp.

## 10.3. Các nút

- `Gợi ý thêm`
- `Hiện nước đi`
- `Xem biến`
- `Nước trước`
- `Nước tiếp`
- `Tự thử`
- `Xem phương án khác`
- `Đóng`

## 10.4. Theo dõi trợ giúp

Lưu:

- Số lần mở gợi ý.
- Tầng gợi ý cao nhất đã xem.
- Số lần đi lại.
- Số lần xem quân nguy hiểm.

Thông tin được hiển thị trong chi tiết ván.

---

# 11. ĐI LẠI VÀ BIẾN PHÂN TÍCH

## 11.1. Đi lại trong ván

Trong chế độ luyện tập:

- Mặc định đi lại một lượt đầy đủ:
  - Nước của người chơi.
  - Nước đáp của máy, nếu máy đã đi.
- Nếu máy chưa đi xong, hủy tác vụ máy và đi lại nước của người chơi.
- Cho phép cấu hình đi lại từng nửa lượt nếu cần.

Khi đi lại:

1. Hủy phân tích liên quan đến thế cũ.
2. Khôi phục trạng thái bàn cờ.
3. Khôi phục bên đi.
4. Khôi phục đồng hồ nếu chế độ cho phép.
5. Tạo trạng thái mới.
6. Không cho kết quả engine cũ cập nhật vào thế mới.

## 11.2. Lưu lịch sử đi lại

- Dòng ván chính chỉ chứa các nước cuối cùng được giữ lại.
- Lưu số lần đi lại.
- Có thể lưu các nhánh đã bỏ để phục vụ học tập.
- Không bắt buộc hiển thị mọi nhánh trong danh sách nước chính.

## 11.3. Biến phân tích

Trong màn phân tích:

- Cho phép người dùng đi thử nước khác.
- Tạo nhánh phân tích riêng.
- Không sửa ván gốc.
- Hiển thị nhãn:

> “Đang xem biến phân tích”

- Có nút:
  - Quay về ván gốc.
  - Lưu biến.
  - Chơi tiếp với máy từ thế này.

---

# 12. NHẬN XÉT VÀ GIẢI THÍCH

## 12.1. Mục tiêu

Nhận xét phải trả lời:

1. Nước vừa đi tốt hay chưa tốt?
2. Có sự kiện chiến thuật nào được phát hiện?
3. Phương án nào đáng xem?
4. Nếu đi theo biến minh họa thì chuyện gì có thể xảy ra?
5. Người chơi cần chú ý điều gì trong thế cờ này?

## 12.2. Kiến trúc nhận xét

```text
Thế trước và sau nước đi
        +
Kết quả engine
        +
Biến hợp lệ
        ↓
Bộ phân tích đặc điểm chiến thuật
        ↓
Dữ kiện có thể kiểm chứng
        ↓
Bộ tạo nhận xét tiếng Việt
```

## 12.3. Các loại dữ kiện có thể phát hiện

- Chiếu Tướng.
- Một quân đang bị tấn công.
- Một quân được bảo vệ hoặc mất bảo vệ.
- Ăn quân trong biến.
- Bắt đôi nếu kiểm tra được.
- Pháo có ngòi hoặc mất ngòi.
- Mã bị cản chân.
- Tướng bị lộ hoặc có ít đường thoát.
- Bỏ lỡ biến chiếu bí mà engine đã tìm.
- Chuyển từ thế có lợi sang bất lợi theo đánh giá engine.

## 12.4. Phân biệt sự kiện và kết luận

Ví dụ:

- “Xe đang bị tấn công” là một sự kiện có thể kiểm tra.
- “Xe chắc chắn sẽ mất” cần bằng chứng mạnh hơn.
- “Nước này bắt đôi Xe và Mã” cần kiểm tra cả hai mục tiêu và tính hợp lệ.
- “Đối thủ buộc phải lùi” cần kiểm tra các nước hợp lệ và biến liên quan.

Nếu chưa đủ căn cứ, dùng nhận xét an toàn:

> “Engine đánh giá nước này kém hơn phương án đề xuất. Hãy xem biến minh họa.”

## 12.5. Mẫu nhận xét

```text
Nước tốt:
“Nước này giữ thế cân bằng và tạo sức ép lên cánh phải.”

Chưa chính xác:
“Nước này cho đối thủ thêm cơ hội tấn công. Engine đề xuất ...”

Sai lầm:
“Sau nước này, Xe của bạn có thể bị tấn công trong biến sau: ...”

Bỏ lỡ chiếu bí:
“Engine đã tìm thấy một biến chiếu bí từ thế trước nước này.”

Chưa đủ dữ kiện:
“Phương án của bạn được engine đánh giá thấp hơn nước đề xuất.”
```

Các câu trên chỉ được sử dụng khi dữ kiện thực tế phù hợp.

## 12.6. AI ngôn ngữ tùy chọn

AI không phải nguồn quyết định luật hoặc nước tốt nhất.

Nếu tích hợp:

- Chỉ gửi dữ liệu đã được engine và lớp luật kiểm tra.
- AI có nhiệm vụ diễn đạt dễ hiểu hơn.
- Không cho AI tự tạo nước đi không có trong dữ liệu.
- Không dùng AI nếu người dùng chưa đồng ý gửi dữ liệu lên mạng.
- Kiểm tra câu trả lời trước khi hiển thị.
- Nếu kết quả không đáng tin, dùng nhận xét mẫu offline.

---

# 13. PHÂN TÍCH CUỐI VÁN

## 13.1. Mục tiêu

Sau trận, người chơi có thể:

- Xem lại toàn bộ ván.
- Xem bàn cờ theo từng nước.
- Tìm bước ngoặt.
- Xem nhận xét.
- So sánh nước thực tế và nước đề xuất.
- Xem biến.
- Tự đánh thử.
- Lưu thế thành bài tập.

## 13.2. Trạng thái phân tích

- Chưa phân tích.
- Đang phân tích.
- Đã phân tích một phần.
- Đã phân tích hoàn tất.
- Bị gián đoạn.
- Lỗi và có thể thử lại.

## 13.3. Quy trình

1. Lấy thế ban đầu và danh sách nước.
2. Kiểm tra tính hợp lệ của toàn bộ chuỗi.
3. Dựng lại từng thế.
4. Phân tích từng thế hoặc theo chiến lược tối ưu tài nguyên.
5. So sánh nước thực tế với phương án tham chiếu.
6. Ghi kết quả từng nước.
7. Tìm các bước ngoặt lớn.
8. Tạo nhận xét có căn cứ.
9. Đề xuất bài tập.
10. Lưu tiến độ và kết quả.

## 13.4. Tóm tắt sau phân tích

Ví dụ:

```text
Kết quả: Thua

Các điểm đáng chú ý:
- 2 sai lầm nghiêm trọng.
- 3 nước chưa chính xác.
- Bước ngoặt lớn nhất: nước 22 của bạn.
- Có 3 thế phù hợp để luyện lại.

[Xem bước ngoặt]
[Luyện lại lỗi]
[Xem toàn bộ ván]
```

## 13.5. Biểu đồ lợi thế

- Trục ngang: diễn tiến ván.
- Trục dọc: điểm đánh giá đã chuẩn hóa.
- Dương: lợi cho Đỏ.
- Âm: lợi cho Đen.
- Cho phép chạm điểm để nhảy đến thế tương ứng.
- Thể hiện trường hợp chiếu bí riêng, không ép thành điểm số thông thường.
- Đồ thị và thanh đánh giá phải lấy cùng dữ liệu cho cùng thế.

Hiển thị giải thích:

> “Điểm đánh giá là giá trị tham khảo của engine, không phải xác suất thắng chắc chắn.”

## 13.6. Danh sách nước

Mỗi dòng cần có:

- Số lượt.
- Nước Đỏ.
- Nước Đen.
- Nhãn nếu có.
- Trạng thái nước đang chọn.

Bộ lọc:

- Tất cả.
- Lỗi của tôi.
- Lỗi của đối thủ.
- Bước ngoặt.
- Nước có nhận xét.

## 13.7. Điều khiển xem lại

- Về đầu.
- Nước trước.
- Phát tự động.
- Nước tiếp.
- Đến cuối.
- Điều chỉnh tốc độ phát.
- Chạm nước bất kỳ để nhảy tới.

---

# 14. KIỂM TRA QUÂN NGUY HIỂM

## 14.1. Chức năng

Khi người dùng bật:

- Làm nổi bật quân đang bị đối thủ tấn công.
- Cho phép chạm quân để xem:
  - Quân nào đang tấn công.
  - Quân nào đang bảo vệ.
  - Biến minh họa nếu engine tìm được.
  - Mức độ chắc chắn của nhận xét.

## 14.2. Các mức thông báo

1. **Đang bị tấn công**
   - Có thể xác định bằng luật.

2. **Có nguy cơ mất quân**
   - Cần kiểm tra thêm biến và khả năng phòng thủ.

3. **Engine tìm thấy biến mất quân**
   - Hiển thị chuỗi nước minh họa.

Không gọi mọi quân bị tấn công là “quân treo”.

## 14.3. Giao diện

- Màu viền và biểu tượng khác nhau cho từng mức.
- Có mô tả bằng chữ.
- Có nút tắt để không che bàn cờ.

---

# 15. BÀI TẬP VÀ LUYỆN TẬP

## 15.1. Các nhóm bài

1. Sửa sai từ ván đã chơi.
2. Chiến thuật:
   - Bắt đôi.
   - Chiếu bí.
   - Bắt quân.
   - Phòng thủ.
   - Phản đòn.
3. Khai cuộc.
4. Tàn cuộc.
5. Bài tập đã lưu.
6. Bài cần ôn hôm nay.

## 15.2. Tạo bài từ sai lầm

Ứng dụng có thể đề xuất bài khi:

- Nước đi bị đánh giá là sai lầm.
- Có phương án tốt hơn rõ ràng.
- Engine tìm được biến đủ ổn định.
- Thế cờ và lịch sử cần thiết được lưu.

Một bài tập gồm:

- Thế trước nước sai.
- Bên cần đi.
- Nước đã đi trong ván.
- Các nước được chấp nhận.
- Một hoặc nhiều biến minh họa.
- Nhận xét.
- Nguồn ván.
- Phiên bản engine.
- Cấu hình phân tích.
- Chủ đề, nếu xác định đủ tin cậy.

## 15.3. Cách giải

1. Hiển thị bàn cờ.
2. Cho người dùng chọn nước.
3. Kiểm tra hợp lệ.
4. So sánh với tập đáp án đã lưu.
5. Nếu cần, phân tích nước khác để xem có tương đương không.
6. Thông báo:
   - Đúng.
   - Nước khác nhưng cũng tốt.
   - Chưa phải phương án tốt.
7. Cho xem biến.
8. Cập nhật lịch ôn tập.

Không yêu cầu duy nhất một đáp án nếu có nhiều nước tốt tương đương.

## 15.4. Gợi ý bài tập

- Gợi ý ý tưởng.
- Chỉ quân.
- Chỉ nước.
- Xem toàn bộ lời giải.

Lưu mức trợ giúp đã dùng.

## 15.5. Ôn tập ngắt quãng

Mỗi bài có:

- Số lần giải.
- Số lần giải đúng.
- Số lần giải đúng liên tiếp.
- Lần giải gần nhất.
- Ngày cần ôn tiếp.
- Mức gợi ý đã dùng.

Quy tắc ban đầu có thể đơn giản:

- Sai: đưa vào danh sách ôn sớm.
- Đúng nhưng dùng nhiều gợi ý: ôn lại sớm hơn.
- Đúng liên tiếp không gợi ý: tăng khoảng cách ôn.

Các khoảng thời gian cụ thể phải được cấu hình, không hard-code rải rác trong UI.

## 15.6. Chơi lại từ sai lầm

Từ bài tập:

- Chọn một nước mới.
- Cho máy chơi tiếp.
- Gắn nhãn đây là ván luyện từ thế nhập.
- Không ghi đè ván gốc.
- Có thể lưu thành ván mới.

---

# 16. THỐNG KÊ VÀ TIẾN BỘ

## 16.1. Chỉ số cơ bản

- Tổng số ván.
- Thắng/thua/hòa.
- Số ván theo cấp máy.
- Số lỗi lớn trung bình mỗi ván.
- Số bài đã giải.
- Tỉ lệ giải đúng bài tập.
- Số bài đến hạn ôn.
- Số ván có dùng gợi ý hoặc đi lại.

## 16.2. Chủ đề yếu

Chỉ hiển thị khi hệ thống nhận diện đủ dữ liệu.

Ví dụ:

```text
Các tình huống bạn nên chú ý:
- Quân bị tấn công: 5 lần trong 10 ván.
- Bỏ lỡ nước chiếu: 3 lần trong 10 ván.
```

Không suy diễn từ dữ liệu quá ít.

## 16.3. Độ chính xác

Đây là tính năng nâng cao, không phải dữ liệu gốc có sẵn từ engine.

Nếu triển khai:

- Tài liệu hóa công thức.
- Ghi rõ engine và cấu hình.
- Tách ván luyện và thử sức.
- Không so sánh trực tiếp với các nền tảng khác.
- Hiển thị đây là chỉ số tham khảo của ứng dụng.
- Không tính hoặc không hiển thị khi dữ liệu chưa đủ.

Trước khi có công thức được kiểm chứng, thay bằng các chỉ số đơn giản và minh bạch.

---

# 17. THIẾT KẾ GIAO DIỆN THEO ẢNH THAM KHẢO

## 17.1. Phong cách tổng thể

Dựa trên các ảnh người dùng cung cấp:

- Nền tối gần đen.
- Thẻ nội dung màu tối hơn hoặc sáng hơn nền một chút.
- Viền thẻ màu xám mảnh.
- Màu nhấn xanh ngọc.
- Màu đỏ/cam cho lỗi.
- Chữ chính màu trắng.
- Chữ phụ màu xám sáng.
- Bo góc vừa phải.
- Bàn cờ gỗ nổi bật.
- Quân cờ dạng tròn.
- Thanh điều hướng dưới gồm ba mục:
  - Sảnh chính.
  - Lịch sử.
  - Luyện tập.

Không sao chép nguyên văn các lỗi hiển thị trong ảnh mẫu.

## 17.2. Màu sắc gợi ý

```text
Nền chính:        #121316
Nền thẻ:          #1B1D21
Viền:             #3B3E45
Chữ chính:        #F4F5F6
Chữ phụ:          #B3B6BD
Xanh nhấn:        #10B981
Đỏ lỗi:           #EF5350
Cam cảnh báo:     #F5A623
Xanh thông tin:   #5B9BD5
```

Đây là bảng màu khởi điểm. Cần kiểm tra độ tương phản trên thiết bị thực tế.

## 17.3. Typography

- Chữ rõ, hỗ trợ tiếng Việt đầy đủ.
- Không dùng chữ quá mảnh.
- Tiêu đề lớn nhưng không chiếm quá nhiều chiều cao.
- Nội dung phụ không được quá mờ.
- Hỗ trợ cỡ chữ hệ thống ở mức hợp lý.
- Những ký hiệu như `?`, `??`, `!` phải có mô tả bằng chữ.

## 17.4. Bàn cờ

Bàn cờ lấy cảm hứng từ ảnh:

- Nền gỗ sáng.
- Đường kẻ màu nâu đậm.
- Quân tròn có viền và bóng nhẹ.
- Chữ quân rõ.
- Có thể chọn giao diện tối giản để tăng hiệu năng và dễ đọc.

Yêu cầu bắt buộc:

- Bàn cờ render từ dữ liệu thật.
- Quân đặt đúng giao điểm.
- Đủ 9 cột và 10 hàng.
- Không có quân giả hoặc quân trùng.
- Hai màu quân phân biệt rõ.
- Không dùng ảnh AI tạo sẵn để biểu diễn thế thực tế.
- Chữ sông nếu có dùng:
  - `SỞ HÀ`
  - `HÁN GIỚI`
- Không để chữ sông che quân.

## 17.5. Mũi tên

- Nước đề xuất: xanh.
- Nước đáp: đỏ hoặc màu phân biệt.
- Có số thứ tự nếu xem tổng quan.
- Có thể dùng nét liền/nét đứt để hỗ trợ người khó phân biệt màu.
- Không vẽ quá nhiều mũi tên cùng lúc.
- Mũi tên phải dựa trên nước hợp lệ.

## 17.6. Thanh điều hướng dưới

Ba mục:

1. `Sảnh chính`
2. `Lịch sử`
3. `Luyện tập`

Mục đang chọn:

- Biểu tượng màu xanh.
- Chữ màu xanh.
- Có vạch chỉ trạng thái.

Các màn chơi và phân tích có thể ẩn thanh điều hướng để tăng diện tích bàn cờ.

---

# 18. CẤU TRÚC MÀN HÌNH

## 18.1. Sơ đồ điều hướng

```text
Sảnh chính
├── Thiết lập ván
│   └── Bàn cờ
│       └── Kết quả ván
│           ├── Phân tích ván
│           └── Luyện lại lỗi
├── Tiếp tục ván dở
├── Nhập thế cờ
│   ├── Phân tích tự do
│   └── Đấu với máy từ thế nhập
└── Hồ sơ và cài đặt

Lịch sử
└── Chi tiết ván
    └── Phân tích ván

Luyện tập
├── Sửa sai từ ván
├── Chiến thuật
├── Khai cuộc
├── Tàn cuộc
├── Bài đã lưu
└── Bài cần ôn
```

---

## 18.2. Màn hình Sảnh chính

### Mục tiêu

Giúp người dùng vào chơi hoặc luyện trong ít thao tác.

### Thành phần

1. Thanh tiêu đề:
   - Tên ứng dụng.
   - Nút hồ sơ/cài đặt.

2. Thẻ `Đấu với máy`:
   - Cấp độ hiện tại.
   - Bên cầm.
   - Chế độ.
   - Nút `Bắt đầu`.

3. Thẻ `Tiếp tục ván dở`:
   - Chỉ hiện khi có ván chưa kết thúc.
   - Hiển thị lượt, cấp máy và thời gian lưu.

4. Thẻ `Nhập thế cờ`:
   - Nhập FEN.
   - Hoặc mở trình đặt quân.

5. Thẻ `Luyện hôm nay`:
   - Số bài cần ôn.
   - Nút `Luyện ngay`.

6. Thẻ thống kê:
   - Các chỉ số đơn giản, dễ hiểu.
   - Không bắt buộc dùng phần trăm độ chính xác.

### Hành vi

- Chạm `Bắt đầu`: mở thiết lập ván hoặc bắt đầu với cấu hình gần nhất.
- Chạm vào các chip cấu hình: mở điều chỉnh tương ứng.
- Có trạng thái rỗng nếu chưa có lịch sử hoặc bài tập.

---

## 18.3. Màn hình Thiết lập ván

### Thành phần

- Cấp máy.
- Bên cầm.
- Chế độ luyện tập/thử sức.
- Đồng hồ.
- Đánh giá trực tiếp.
- Gợi ý.
- Đi lại.
- Thanh đánh giá.
- Nút `Bắt đầu ván`.

### Yêu cầu

- Các tùy chọn bị khóa trong chế độ thử sức phải hiển thị rõ.
- Ghi nhớ cấu hình gần nhất.
- Có mô tả ngắn về từng chế độ.

---

## 18.4. Màn hình Bàn cờ

### Thành phần

1. Thanh trên:
   - Tên máy và cấp.
   - Số lượt.
   - Nút menu.

2. Khu vực thông tin đối thủ:
   - Bên cầm.
   - Đồng hồ nếu có.

3. Bàn cờ:
   - Quân.
   - Nước vừa đi.
   - Quân được chọn.
   - Nước hợp lệ.
   - Mũi tên gợi ý khi bật.

4. Thanh đánh giá tùy chọn:
   - Chú thích dương lợi Đỏ.
   - Không hiển thị nếu tắt trợ giúp.

5. Khu vực người chơi:
   - Bên cầm.
   - Đồng hồ nếu có.

6. Thanh nhận xét ngắn:
   - Nhãn nước.
   - Một câu ngắn.
   - Nút `Xem`.

7. Thanh thao tác:
   - Đi lại.
   - Gợi ý.
   - Xoay bàn.
   - Quân nguy hiểm.
   - Menu thêm.

### Tương tác quân

- Chạm quân, sau đó chạm điểm đến.
- Có thể hỗ trợ kéo thả.
- Hiển thị nước hợp lệ khi chọn quân.
- Không cho thực hiện nước sai luật.
- Khi đi thành công:
  - Phát âm thanh nếu bật.
  - Phát animation.
  - Lưu trạng thái.

### Trạng thái máy

- Đang suy nghĩ.
- Đã có nước.
- Bị gián đoạn.
- Lỗi engine.

Trong khi máy suy nghĩ:

- Hiện trạng thái rõ.
- Không khóa toàn bộ UI.
- Có thể cho phép mở menu hoặc đi lại theo chế độ.

---

## 18.5. Màn hình Kết quả ván

### Thành phần

- Thắng/thua/hòa.
- Lý do kết thúc.
- Cấp máy.
- Chế độ.
- Số lần gợi ý và đi lại.
- Tóm tắt lỗi nếu đã phân tích.
- Nút:
  - `Phân tích ván`.
  - `Luyện lại lỗi`.
  - `Chơi ván mới`.
  - `Về sảnh`.

Nếu chưa phân tích:

- Hiện nút `Bắt đầu phân tích`.
- Không hiển thị số lỗi giả định.

---

## 18.6. Màn hình Phân tích ván

### Bố cục tham khảo

Dựa trên ảnh mẫu:

- Tiêu đề `Phân tích ván đấu`.
- Biểu đồ lợi thế.
- Bàn cờ.
- Nhận xét nước đang chọn.
- Danh sách nước.
- Điều khiển xem lại.

### Điều chỉnh so với ảnh

- Đảm bảo đồ thị và điểm đánh giá đồng bộ.
- Bàn cờ phải có thế hợp lệ.
- Mũi tên biến phải hợp lệ.
- Có trạng thái đang phân tích.
- Có thể thu gọn đồ thị để bàn cờ lớn hơn.
- Phân biệt ván thực tế và biến thử.

### Thao tác

- Chạm đồ thị để chọn nước.
- Chạm nước trong danh sách để xem thế.
- Bấm `Xem biến`.
- Bấm `Đánh thử`.
- Bấm `Lưu bài tập`.
- Lọc lỗi của mình hoặc đối thủ.

---

## 18.7. Màn hình Lịch sử

### Thành phần

- Tiêu đề.
- Tab hoặc bộ lọc.
- Danh sách ván.
- Thẻ gợi ý luyện lại lỗi.

### Mỗi thẻ ván

- Thắng/thua/hòa.
- Máy và cấp.
- Ngày giờ.
- Bên cầm.
- Chế độ.
- Số lỗi nếu đã phân tích.
- Trạng thái phân tích.
- Số lần gợi ý/đi lại.
- Nút `Xem`.

### Bộ lọc

- Tất cả.
- Thắng.
- Thua.
- Hòa.
- Có sai lầm nghiêm trọng.
- Chưa phân tích.
- Cấp máy.
- Thời gian.

---

## 18.8. Màn hình Luyện tập

### Thành phần

- Sửa sai từ ván đã chơi.
- Bài cần ôn hôm nay.
- Chiến thuật.
- Khai cuộc.
- Tàn cuộc.
- Bài đã lưu.
- Tiến độ luyện tập.

### Lưu ý

Không chỉ tạo bài từ ván thua. Một ván thắng vẫn có thể có sai lầm đáng luyện.

---

## 18.9. Màn hình Giải bài tập

### Thành phần

- Tên bài/chủ đề.
- Bên cần đi.
- Bàn cờ.
- Nút gợi ý.
- Nút kiểm tra hoặc tự kiểm tra khi đi.
- Phản hồi.
- Lời giải.
- Biến minh họa.
- Nút bài tiếp theo.

### Trạng thái

- Chưa giải.
- Đang thử.
- Đúng.
- Nước khác nhưng tốt.
- Chưa đúng.
- Đã xem lời giải.

---

## 18.10. Màn hình Nhập thế cờ

### Chức năng

- Dán FEN.
- Kiểm tra FEN.
- Xóa bàn.
- Đặt quân.
- Chọn bên đi.
- Xoay bàn.
- Phân tích thế.
- Chơi với máy từ thế này.
- Sao chép FEN.

### Cảnh báo

Nếu không có lịch sử:

> “Một số quy tắc liên quan đến lặp thế có thể không xác định đầy đủ.”

---

## 18.11. Màn hình Hồ sơ và Cài đặt

### Cài đặt chơi

- Mặc định cấp máy.
- Mặc định bên cầm.
- Bật/tắt gợi ý.
- Bật/tắt đánh giá trực tiếp.
- Bật/tắt thanh lợi thế.

### Cài đặt giao diện

- Kiểu bàn cờ.
- Kiểu quân.
- Ký phổ rút gọn/đầy đủ.
- Hiển thị tọa độ.
- Giảm chuyển động.

### Âm thanh và rung

- Âm lượng.
- Bật/tắt âm thanh nước đi.
- Bật/tắt âm thanh ăn quân.
- Bật/tắt rung khi phát hiện nước sai.
- Bật/tắt hiệu ứng nước tốt.

### Phân tích

- Tiết kiệm.
- Cân bằng.
- Phân tích sâu.
- Tùy chọn số luồng/bộ nhớ nếu cần cho người dùng nâng cao.

### Dữ liệu

- Xuất dữ liệu.
- Nhập dữ liệu.
- Xóa lịch sử.
- Xóa bài tập.
- Xóa cache phân tích.
- Hiển thị dung lượng lưu trữ.

### Thông tin

- Phiên bản ứng dụng.
- Phiên bản engine.
- Giấy phép.
- Chính sách quyền riêng tư nếu có chức năng mạng.

---

# 19. HIỆU ỨNG CHUYỂN ĐỘNG, ÂM THANH VÀ RUNG

## 19.1. Nguyên tắc chung

- Hiệu ứng đẹp nhưng không làm chậm thao tác.
- Không che bàn cờ.
- Không gây hiểu nhầm trạng thái nước đi.
- Có thể tắt hoặc giảm.
- Tôn trọng cài đặt giảm chuyển động của hệ thống khi phù hợp.
- Không phát hiệu ứng kéo dài liên tục.

## 19.2. Animation quân cờ

Khi quân đi:

- Di chuyển mượt từ điểm đầu đến điểm cuối.
- Thời lượng ngắn.
- Khi đi lại, có thể chạy animation ngược.
- Nếu người dùng thao tác nhanh, trạng thái logic vẫn phải chính xác.
- Không chờ animation hoàn tất mới lưu dữ liệu.

Khi ăn quân:

- Quân bị ăn mờ dần hoặc thu nhỏ nhẹ.
- Không dùng hiệu ứng quá phức tạp.

## 19.3. Hiệu ứng nước tốt

Khi engine đánh giá nước là tốt:

- Viền xanh nhẹ tại điểm đến.
- Hiệu ứng sáng ngắn hoặc vòng lan mờ.
- Hiển thị nhãn `Tốt`.

Chỉ kích hoạt sau khi có kết quả đánh giá tương ứng.

Nếu sau phân tích sâu nhãn thay đổi, màn phân tích phải dùng kết quả mới; không cố giữ nhãn trực tiếp.

## 19.4. Hiệu ứng nước sai

Khi phát hiện nước sai:

- Hiển thị nhãn `Sai lầm` hoặc `Sai lầm nghiêm trọng`.
- Tô viền đỏ nhẹ.
- Rung nhẹ nếu người dùng bật.
- Không tự động đi lại nước.
- Không tự lộ nước đúng nếu người dùng chưa yêu cầu.

Phân biệt:

- **Nước không hợp lệ:** không cho đi.
- **Nước hợp lệ nhưng sai về chiến thuật:** cho đi, sau đó mới cảnh báo nếu bật đánh giá.

## 19.5. Âm thanh

Các âm thanh:

- Đi quân.
- Ăn quân.
- Chiếu Tướng.
- Thắng.
- Thua.
- Hòa.
- Trả lời đúng bài tập.
- Trả lời chưa đúng bài tập.

Yêu cầu:

- Âm thanh ngắn.
- Không chồng quá nhiều âm thanh.
- Tôn trọng chế độ âm thanh hệ thống và cài đặt ứng dụng.
- Tài nguyên âm thanh phải có giấy phép phù hợp.
- Có nút nghe thử trong cài đặt nếu cần.

## 19.6. Rung

- Rung nhẹ khi phát hiện nước sai, nếu được bật.
- Có thể rung khác khi đi nước không hợp lệ.
- Không rung liên tục trong quá trình engine cập nhật điểm.
- Kiểm tra thiết bị hỗ trợ.
- Tuân thủ API Android và quyền cần thiết theo cách triển khai.

## 19.7. Chuyển màn hình

- Fade hoặc slide ngắn.
- Không làm chậm điều hướng.
- Không áp dụng chuyển động mạnh khi người dùng bật giảm chuyển động.

---

# 20. DỮ LIỆU VÀ CƠ SỞ DỮ LIỆU

## 20.1. Nguyên tắc

- Dữ liệu ván và bài tập lưu cục bộ.
- Mọi ván cần có lịch sử nước.
- Có thể tái dựng bàn cờ từ thế ban đầu và nước đi.
- Không chỉ lưu ảnh bàn cờ.
- Có migration khi thay đổi cấu trúc database.
- Không xóa dữ liệu người dùng khi cập nhật app.

## 20.2. Bảng `games`

```text
id
createdAt
updatedAt
finishedAt
initialFen
rulesetId
playerSide
engineName
engineVersion
engineLevel
mode
result
resultReason
status
hintCount
undoCount
dangerCheckCount
analysisStatus
```

## 20.3. Bảng `moves`

```text
id
gameId
plyIndex
side
fromSquare
toSquare
pieceType
capturedPieceType
notation
positionHashAfter
playedAt
```

`plyIndex` là chỉ số từng nửa lượt, giúp phân biệt chính xác nước Đỏ và Đen.

## 20.4. Bảng `move_analysis`

```text
id
gameId
plyIndex
engineName
engineVersion
analysisConfig
scoreBefore
scoreAfter
scorePerspective
mateInfo
bestMove
playedMove
principalVariation
depth
searchTime
classification
commentary
analysisVersion
```

Nếu cần lưu nhiều biến, nên tạo bảng riêng hoặc dùng cấu trúc có phiên bản rõ ràng.

## 20.5. Bảng `analysis_variations`

```text
id
analysisId
rank
score
mateInfo
moves
```

## 20.6. Bảng `puzzles`

```text
id
sourceType
sourceGameId
sourcePlyIndex
initialFen
historyData
sideToMove
title
theme
acceptedMoves
solutionVariations
commentary
engineVersion
analysisConfig
createdAt
```

## 20.7. Bảng `puzzle_progress`

```text
puzzleId
attemptCount
correctCount
consecutiveCorrectCount
highestHintLevelUsed
lastAttemptAt
nextReviewAt
```

## 20.8. Cài đặt

Lưu bằng DataStore:

- Âm thanh.
- Rung.
- Giao diện.
- Mức engine.
- Các tùy chọn trợ giúp.
- Cấu hình mặc định.
- Giảm chuyển động.

## 20.9. Xuất và nhập dữ liệu

- Có định dạng xuất có phiên bản.
- Kiểm tra dữ liệu trước khi nhập.
- Không cho file nhập ghi đè toàn bộ dữ liệu mà không xác nhận.
- Xử lý trường hợp bản xuất mới hơn phiên bản ứng dụng.
- Không thực thi dữ liệu trong file nhập như mã chương trình.

---

# 21. KIẾN TRÚC MÃ NGUỒN

## 21.1. Cấu trúc thư mục đề xuất

```text
app/
├── src/main/
│   ├── java/.../
│   │   ├── App.kt
│   │   ├── MainActivity.kt
│   │   │
│   │   ├── core/
│   │   │   ├── common/
│   │   │   ├── model/
│   │   │   ├── ui/
│   │   │   └── util/
│   │   │
│   │   ├── domain/
│   │   │   ├── rules/
│   │   │   ├── game/
│   │   │   ├── analysis/
│   │   │   ├── commentary/
│   │   │   └── training/
│   │   │
│   │   ├── data/
│   │   │   ├── database/
│   │   │   ├── datastore/
│   │   │   ├── repository/
│   │   │   └── importexport/
│   │   │
│   │   ├── engine/
│   │   │   ├── api/
│   │   │   ├── nativebridge/
│   │   │   ├── protocol/
│   │   │   └── manager/
│   │   │
│   │   ├── feature/
│   │   │   ├── home/
│   │   │   ├── setup/
│   │   │   ├── game/
│   │   │   ├── result/
│   │   │   ├── history/
│   │   │   ├── analysis/
│   │   │   ├── training/
│   │   │   ├── puzzle/
│   │   │   ├── positioneditor/
│   │   │   └── settings/
│   │   │
│   │   ├── navigation/
│   │   └── service/
│   │       ├── sound/
│   │       └── haptic/
│   │
│   ├── cpp/
│   │   └── engine_bridge/
│   │
│   ├── assets/
│   └── res/
│
├── src/test/
├── src/androidTest/
└── build.gradle.kts
```

Cấu trúc có thể chia thành nhiều Gradle module khi dự án đủ lớn. Không bắt buộc tách module quá sớm.

## 21.2. Các lớp quan trọng

### `GameState`

Chứa:

- Bàn cờ.
- Bên đi.
- Lịch sử nước.
- Trạng thái ván.
- Đồng hồ.
- Cấu hình trợ giúp.

### `RulesEngine`

Chịu trách nhiệm:

- Sinh nước hợp lệ.
- Áp dụng nước.
- Kiểm tra chiếu.
- Kiểm tra kết thúc.
- Xử lý luật đã chọn.

### `EngineManager`

Chịu trách nhiệm:

- Khởi tạo engine.
- Xếp hàng yêu cầu.
- Hủy yêu cầu cũ.
- Trả kết quả.
- Xử lý lỗi.

### `AnalysisService`

Chịu trách nhiệm:

- Phân tích thế.
- Phân tích ván.
- So sánh nước.
- Tìm bước ngoặt.
- Lưu tiến độ.

### `CommentaryService`

Chịu trách nhiệm:

- Trích xuất dữ kiện.
- Tạo nhận xét.
- Không tự bịa biến.

### `TrainingService`

Chịu trách nhiệm:

- Tạo bài tập.
- Kiểm tra đáp án.
- Cập nhật lịch ôn.

### `BoardRenderer`

Chịu trách nhiệm:

- Vẽ bàn cờ.
- Vẽ quân.
- Vẽ điểm chọn.
- Vẽ mũi tên.
- Xử lý tọa độ khi xoay bàn.

Không để logic luật cờ nằm trực tiếp trong composable vẽ bàn.

---

# 22. TRẠNG THÁI VÀ XỬ LÝ LỖI

## 22.1. Các lỗi cần xử lý

- Engine không khởi tạo được.
- Thiếu mạng đánh giá.
- Engine không trả kết quả.
- Phân tích bị hủy.
- Dữ liệu ván không hợp lệ.
- FEN không hợp lệ.
- File nhập sai định dạng.
- Database lỗi.
- Thiết bị thiếu bộ nhớ.
- Tác vụ bị hệ thống dừng.

## 22.2. Cách hiển thị

Thông báo phải:

- Ngắn.
- Nói rõ điều gì xảy ra.
- Đưa ra hành động tiếp theo.

Ví dụ:

```text
Không thể khởi tạo máy cờ.

Ván của bạn đã được lưu.
[Thử lại] [Quay về sảnh]
```

## 22.3. Không được xảy ra

- Mất ván vì engine lỗi.
- Đóng ứng dụng khi nhập FEN sai.
- Hiển thị nước engine trả về cho thế cũ.
- Ghi đè ván gốc khi thử biến.
- Kết luận nước hợp lệ khi lớp luật chưa kiểm tra.
- Tạo bài tập từ phân tích lỗi hoặc chưa đầy đủ mà không đánh dấu.

---

# 23. QUYỀN RIÊNG TƯ VÀ BẢO MẬT

## 23.1. Offline

Mặc định:

- Ván cờ lưu trên máy.
- Phân tích chạy trên máy.
- Không cần tài khoản.
- Không cần mạng.

## 23.2. Khi bổ sung AI hoặc đồng bộ

- Thông báo rõ dữ liệu nào được gửi.
- Xin sự đồng ý phù hợp.
- Cho phép tắt.
- Không gửi dữ liệu cá nhân không cần thiết.
- Cung cấp cách xóa dữ liệu.
- Dùng kết nối bảo mật.
- Không lưu khóa API bí mật trực tiếp trong ứng dụng.

## 23.3. Quyền Android

Chỉ xin quyền cần thiết.

- Không xin quyền truy cập danh bạ.
- Không xin quyền vị trí.
- Xuất/nhập file nên dùng cơ chế chọn file của hệ thống.
- Quyền rung hoặc thông báo phụ thuộc cách triển khai và phiên bản Android.

---

# 24. KIỂM THỬ

## 24.1. Kiểm thử luật

Tạo bộ test cho:

- Nước đi từng loại quân.
- Chặn chân Mã.
- Chặn mắt Tượng.
- Tượng qua sông.
- Pháo có/không có ngòi.
- Tướng trong cung.
- Sĩ trong cung.
- Hai Tướng trực diện.
- Tự để Tướng bị chiếu.
- Thoát chiếu.
- Chiếu bí.
- Không còn nước hợp lệ.
- Lặp thế.
- Chiếu kéo dài.
- Đuổi quân kéo dài.
- Các trường hợp theo bộ luật được chọn.

## 24.2. Kiểm thử dữ liệu

- Lưu và khôi phục ván.
- Chuyển đổi FEN.
- Chuyển đổi ký phổ.
- Migration database.
- Nhập file lỗi.
- Xuất rồi nhập lại.
- Phục hồi sau khi app bị đóng.

## 24.3. Kiểm thử engine

- Khởi tạo thành công.
- Trả nước hợp lệ.
- Dừng tìm kiếm.
- Hủy kết quả cũ.
- Không cập nhật nhầm thế.
- Khôi phục khi engine lỗi.
- Phân tích nhiều biến nếu hỗ trợ.
- Kiểm thử với thế chiếu bí.
- Kiểm thử lịch sử liên quan đến lặp thế.

## 24.4. Kiểm thử giao diện

- Chạm để đi.
- Kéo thả nếu hỗ trợ.
- Xoay bàn.
- Chọn nước trong lịch sử.
- Xem biến.
- Quay về ván gốc.
- Màn hình nhỏ.
- Màn hình lớn.
- Cỡ chữ hệ thống lớn.
- Chế độ giảm chuyển động.
- Nội dung không bị thanh hệ thống che.

## 24.5. Kiểm thử hiệu năng

Trên thiết bị thật:

- Mở app.
- Mở bàn cờ.
- Đi quân liên tục.
- Máy tính nước.
- Phân tích ván dài.
- Chạy khi pin thấp.
- Chuyển app xuống nền.
- Khôi phục sau khi hệ thống đóng tiến trình.
- Theo dõi nhiệt độ và mức tiêu thụ pin.

Mục tiêu:

- Giao diện luôn phản hồi.
- Không chạy engine trên main thread.
- Không rò rỉ tài nguyên engine.
- Không làm tăng bộ nhớ liên tục khi xem nhiều ván.

## 24.6. Kiểm thử chất lượng nhận xét

Tạo bộ thế mẫu đã được kiểm tra bởi người hiểu cờ:

- Bắt đôi.
- Quân bị tấn công nhưng vẫn an toàn.
- Quân có vẻ được bảo vệ nhưng thực tế mất.
- Bỏ lỡ chiếu bí.
- Đổi quân có lợi.
- Pháo mất ngòi.
- Mã bị cản chân.
- Thế chiến lược không có kết luận chiến thuật rõ.

Đo:

- Tỉ lệ nhận xét đúng.
- Tỉ lệ nhận xét quá mức.
- Tỉ lệ không đưa ra kết luận.
- Số trường hợp biến hiển thị không hợp lệ.

Ưu tiên giảm nhận xét sai, kể cả phải tăng số trường hợp dùng nhận xét chung.

---

# 25. CÁC RỦI RO KỸ THUẬT

## 25.1. Tích hợp engine native

### Rủi ro

- Khó biên dịch.
- Thiếu tương thích ABI.
- Tốn RAM.
- Không tương thích tùy chọn engine mong muốn.

### Giảm thiểu

- Làm prototype sớm.
- Kiểm tra trên thiết bị thật.
- Tách engine sau interface.
- Ghi lại phiên bản và cấu hình.

## 25.2. Luật lặp và chiếu đuổi

### Rủi ro

- Luật phức tạp.
- Các bộ luật khác nhau.
- Engine và app có thể không đồng nhất.

### Giảm thiểu

- Chọn bộ luật rõ ràng.
- Viết test tình huống.
- Lưu lịch sử đầy đủ.
- Kiểm thử đối chiếu.

## 25.3. Nhận xét sai

### Rủi ro

- Engine cho điểm nhưng không cung cấp nguyên nhân bằng tiếng Việt.
- Phát hiện chiến thuật có thể thiếu ngữ cảnh.

### Giảm thiểu

- Tách sự kiện khỏi kết luận.
- Chỉ dùng nhận xét có căn cứ.
- Hiển thị biến.
- Có nhận xét an toàn khi chưa chắc.

## 25.4. Điện thoại nóng và hao pin

### Giảm thiểu

- Giới hạn tài nguyên.
- Có chế độ tiết kiệm.
- Dừng khi không cần.
- Cho phép hủy phân tích.
- Lưu tiến độ từng phần.

## 25.5. Bản quyền

### Giảm thiểu

- Kiểm tra giấy phép trước khi tích hợp.
- Lưu danh sách thành phần bên thứ ba.
- Chuẩn bị phương án đáp ứng nghĩa vụ phân phối.
- Không dùng tài nguyên không rõ nguồn.

---

# 26. LỘ TRÌNH PHÁT TRIỂN

Dự án chia thành **4 phase lớn**. Mỗi phase phải có sản phẩm chạy được và điều kiện hoàn thành rõ ràng.

---

## PHASE 1 — LÕI CỜ VÀ ĐẤU VỚI MÁY

### Mục tiêu

Tạo ứng dụng Android có thể chơi một ván cờ tướng với máy ổn định.

### Công việc

#### Nền tảng

- Khởi tạo dự án Android Studio.
- Cấu hình Kotlin, Compose, Room và DataStore.
- Xây hệ thống theme tối.
- Xây điều hướng cơ bản.
- Thiết lập kiểm thử.

#### Luật cờ

- Mô hình bàn cờ và quân.
- Sinh nước hợp lệ.
- Kiểm tra chiếu.
- Xử lý kết thúc ván.
- Chọn bộ luật mặc định và đặc tả các trường hợp lặp.
- Viết test luật cốt lõi.

#### Engine

- Prototype Pikafish trên Android.
- Tích hợp native bridge.
- Gửi thế cờ.
- Nhận nước máy.
- Dừng và hủy tác vụ.
- Tạo các cấp máy ban đầu.

#### Bàn cờ

- Vẽ bàn cờ.
- Vẽ quân.
- Chạm chọn quân và điểm đến.
- Đánh dấu nước hợp lệ.
- Xoay bàn.
- Animation đi quân.
- Âm thanh đi quân, ăn quân.
- Cài đặt bật/tắt âm thanh.

#### Ván đấu

- Chọn bên cầm.
- Chọn cấp.
- Chế độ luyện tập và thử sức ở mức cơ bản.
- Đi lại trong chế độ luyện tập.
- Xin thua.
- Lưu ván.
- Tiếp tục ván dở.

#### Giao diện

- Sảnh chính.
- Thiết lập ván.
- Bàn cờ.
- Kết quả ván.
- Cài đặt cơ bản.

### Điều kiện hoàn thành

- Chơi được nhiều ván liên tiếp mà không mất dữ liệu.
- Không cho phép nước sai luật.
- Máy chỉ đi nước hợp lệ.
- Đi lại hoạt động đúng.
- Đóng và mở app vẫn tiếp tục được ván dở.
- Engine không khóa giao diện.
- Chạy được trên Android 10 và thiết bị Android mới hơn.
- Có kết quả kiểm thử các luật cốt lõi.
- Các giới hạn luật lặp chưa hoàn chỉnh, nếu còn, phải được ghi rõ và không tuyên bố đã hỗ trợ đầy đủ.

---

## PHASE 2 — GỢI Ý VÀ PHÂN TÍCH VÁN

### Mục tiêu

Biến ứng dụng từ bàn cờ thành công cụ xem lại và phân tích.

### Công việc

#### Gợi ý

- Gợi ý theo tầng.
- Chỉ quân.
- Chỉ nước.
- Xem biến từng bước.
- Kiểm tra tính hợp lệ của biến.

#### Đánh giá trực tiếp

- Đánh giá nhanh sau nước đi.
- Các nhãn cơ bản.
- Bật/tắt nhận xét.
- Thanh lợi thế.
- Hủy kết quả cũ khi thế thay đổi.
- Rung nhẹ khi phát hiện sai lầm nếu bật.
- Hiệu ứng nước tốt nếu bật.

#### Phân tích cuối ván

- Phân tích từng nước.
- Lưu kết quả và cấu hình engine.
- Chỉ ra bước ngoặt.
- Biểu đồ lợi thế.
- Danh sách nước.
- Xem bàn cờ từng nước.
- Điều khiển phát lại.
- Xem biến.
- Đánh thử không sửa ván gốc.
- Tiếp tục phân tích sau gián đoạn.

#### Nhận xét

- Nhận xét mẫu dựa trên điểm và dữ kiện đã kiểm tra.
- Phát hiện chiếu, ăn quân và quân bị tấn công.
- Có câu trả lời an toàn khi chưa đủ căn cứ.

#### Giao diện

- Lịch sử.
- Phân tích ván.
- Trạng thái phân tích.
- Kết quả sau ván có đường dẫn đến phân tích.

### Điều kiện hoàn thành

- Xem lại được mọi nước trong ván.
- Biểu đồ và bàn cờ đồng bộ.
- Biến engine được kiểm tra hợp lệ.
- Đi thử không sửa ván gốc.
- Kết quả cũ không xuất hiện trên thế mới.
- Phân tích có thể dừng và tiếp tục.
- Nhận xét không khẳng định mất quân hoặc chiếu bí nếu không có căn cứ.
- Chạy phân tích mà giao diện vẫn phản hồi.

---

## PHASE 3 — LUYỆN TẬP CÁ NHÂN HÓA

### Mục tiêu

Giúp người dùng học từ sai lầm và theo dõi sự tiến bộ.

### Công việc

#### Bài tập

- Tạo bài từ sai lầm.
- Cho phép lưu thế thủ công.
- Giải bài trên bàn cờ.
- Gợi ý nhiều tầng.
- Chấp nhận phương án tương đương khi được xác minh.
- Hiển thị lời giải và biến.
- Chơi lại từ thế sai.

#### Ôn tập

- Lưu tiến độ.
- Danh sách bài đến hạn.
- Lịch ôn đơn giản.
- Phân biệt bài giải không dùng gợi ý.

#### Thống kê

- Tổng ván.
- Thắng/thua/hòa.
- Số lỗi.
- Số bài đã giải.
- Bài cần ôn.
- Thống kê theo chế độ.
- Chủ đề yếu nếu có đủ dữ kiện.

#### Dữ liệu cờ

- Nhập FEN.
- Trình đặt quân.
- Xuất FEN.
- Nhập/xuất ván theo định dạng được tài liệu hóa.
- Bộ bài tập và khai cuộc ban đầu có kiểm chứng.

#### Nhận xét nâng cao

- Bắt đôi.
- Pháo có/mất ngòi.
- Mã bị cản chân.
- Các tình huống chiến thuật khác có test.

### Điều kiện hoàn thành

- Tạo được bài tập từ ván đã phân tích.
- Người dùng giải và ôn lại được.
- Không bắt buộc một đáp án duy nhất khi có phương án tương đương đã xác minh.
- Nhập FEN sai không làm app lỗi.
- Thống kê có nguồn dữ liệu rõ ràng.
- Bài tập và nhận xét quan trọng đã được kiểm tra bằng bộ thế mẫu.

---

## PHASE 4 — HOÀN THIỆN VÀ MỞ RỘNG

### Mục tiêu

Tối ưu chất lượng sản phẩm, sẵn sàng phát hành và bổ sung chức năng nâng cao.

### Công việc bắt buộc

- Tối ưu hiệu năng, pin, bộ nhớ và nhiệt.
- Hoàn thiện khả năng tiếp cận.
- Hoàn thiện chế độ giảm chuyển động.
- Hoàn thiện âm thanh, rung và animation.
- Kiểm thử trên nhiều thiết bị.
- Kiểm tra migration.
- Kiểm tra giấy phép.
- Chuẩn bị tài liệu phát hành.
- Hoàn thiện các tình huống luật nâng cao đã chọn.
- Thu thập và sửa lỗi qua thử nghiệm thực tế.

### Công việc mở rộng nếu cần

- Máy vừa sức.
- Đoán nước đối thủ.
- Thư viện khai cuộc và tàn cuộc mở rộng.
- Đồng bộ tài khoản.
- AI diễn đạt lời giải thích.
- Độ chính xác theo công thức được kiểm chứng.
- Bổ sung engine hoặc chế độ phân tích khác.

### Điều kiện hoàn thành

- Không còn lỗi nghiêm trọng liên quan đến luật và mất dữ liệu trong bộ test phát hành.
- Đã kiểm tra giấy phép các thành phần.
- Hoạt động ổn định trên Android 10+ theo ma trận thiết bị được kiểm thử.
- Người dùng có thể tắt âm thanh, rung và hiệu ứng.
- Chức năng offline không phụ thuộc tài khoản hoặc mạng.
- Các chức năng mạng nếu có phải thông báo rõ dữ liệu được gửi.

---

# 27. TIÊU CHÍ HOÀN THÀNH TOÀN DỰ ÁN

Dự án được xem là đạt mục tiêu khi:

1. Người dùng có thể đấu với máy ở nhiều cấp.
2. Có thể chơi offline.
3. Có thể lưu và tiếp tục ván.
4. Có thể đi lại trong chế độ luyện tập.
5. Có thể bật/tắt trợ giúp.
6. Có thể xem lại toàn bộ ván theo từng nước.
7. Có thể xem nước đề xuất và biến minh họa hợp lệ.
8. Có thể phân tích các bước ngoặt.
9. Có thể tạo và giải bài từ sai lầm.
10. Có thể ôn lại bài đã lưu.
11. Nhận xét có căn cứ hoặc nói rõ giới hạn.
12. Ứng dụng không khóa giao diện khi engine chạy.
13. Có âm thanh, rung và hiệu ứng nhưng cho phép tắt.
14. Hỗ trợ Android 10 trở lên theo phạm vi thiết bị đã kiểm thử.
15. Có thông tin giấy phép và nguồn thành phần bên thứ ba.
16. Không mất dữ liệu khi cập nhật hoặc khi tác vụ phân tích bị gián đoạn.

---

# 28. THỨ TỰ ƯU TIÊN KHI CODE

Nếu cần quyết định nên làm gì trước, dùng thứ tự:

```text
1. Luật đúng
2. Engine chạy ổn
3. Lưu và khôi phục ván
4. Giao diện bàn cờ dễ dùng
5. Gợi ý và biến hợp lệ
6. Phân tích cuối ván
7. Bài tập sửa sai
8. Nhận xét có căn cứ
9. Âm thanh, rung và hiệu ứng
10. Thống kê và tính năng mở rộng
```

Lưu ý: hiệu ứng có thể được xây sớm ở mức cơ bản, nhưng không được đánh đổi tính đúng của luật và sự ổn định.

---

# 29. QUY TẮC LÀM VIỆC KHI PHÁT TRIỂN

- Mỗi chức năng cần có mô tả đầu vào, đầu ra và trạng thái lỗi.
- Không viết logic luật trực tiếp trong UI.
- Không chạy engine trên main thread.
- Không dùng dữ liệu mock làm dữ liệu thật khi phát hành.
- Không hiển thị con số thống kê chưa có công thức rõ ràng.
- Không gắn tên khai cuộc nếu chưa đủ dữ kiện.
- Không tự tạo lời nhận xét chiến thuật không có căn cứ.
- Mỗi lỗi luật được phát hiện cần bổ sung test tái hiện.
- Mỗi thay đổi database cần migration.
- Mỗi bản cập nhật engine cần kiểm thử lại phân tích và giấy phép.
- Khi có mâu thuẫn giữa tài liệu và hành vi thực tế, cập nhật tài liệu cùng với mã nguồn.

---

# 30. DANH SÁCH QUYẾT ĐỊNH CẦN CHỐT TRƯỚC KHI CODE SÂU

1. Tên chính thức và package name.
2. Bộ luật cờ tướng cụ thể được áp dụng.
3. Phiên bản Pikafish và mạng đánh giá đi kèm.
4. Cách đáp ứng giấy phép khi phân phối.
5. ABI Android cần hỗ trợ.
6. Quy ước FEN.
7. Quy ước ký phổ.
8. Cách đóng gói engine và tài nguyên native.
9. Bộ test luật ban đầu.
10. Giới hạn tài nguyên engine mặc định sau khi đo thiết bị.
11. Các nguồn âm thanh, font và hình ảnh có giấy phép.
12. Chính sách lưu dữ liệu và xuất dữ liệu.
13. Có phát hành lên Google Play hay chỉ cài APK nội bộ.
14. Có triển khai tài khoản/AI ở phase 4 hay để ngoài phạm vi bản phát hành đầu.

---

# KẾT LUẬN

Ứng dụng không chỉ là một trò chơi cờ tướng.

Sản phẩm cần hoạt động như một **người hướng dẫn luyện cờ**:

- Cho người chơi đấu với máy vừa sức.
- Chỉ ra những thời điểm quan trọng.
- Giúp người chơi tự tìm phương án tốt hơn.
- Minh họa bằng bàn cờ và biến hợp lệ.
- Giải thích trong phạm vi có thể chứng minh.
- Ghi nhớ sai lầm để tạo bài tập cá nhân.
- Theo dõi sự cải thiện theo thời gian.

Nguyên tắc quan trọng nhất:

> **Một nhận xét ngắn nhưng đúng, kèm biến minh họa hợp lệ, có giá trị hơn một lời giải thích dài và hấp dẫn nhưng không có căn cứ.**
```