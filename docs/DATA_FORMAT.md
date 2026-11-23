# Định dạng Xuất / Nhập dữ liệu (Cờ Tướng – Luyện Kỳ)

Tài liệu hóa cấu trúc file JSON dùng cho chức năng **Xuất dữ liệu** và **Nhập dữ liệu** (Mục 20.9 & 26 của đặc tả dự án).

## Cấu trúc tổng thể (`schemaVersion = 1`)

```json
{
  "format": "cotuong-luyenky-backup",
  "schemaVersion": 1,
  "appVersion": "1.0.0-phase3",
  "exportedAt": 1763856000000,
  "games": [
    {
      "createdAt": 1763855000000,
      "updatedAt": 1763855500000,
      "finishedAt": 1763855500000,
      "initialFen": "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1",
      "rulesetId": "WXF_BASIC_V1",
      "playerSide": "RED",
      "engineName": "BuiltIn Xiangqi Engine",
      "engineVersion": "1.0",
      "engineLevel": 4,
      "mode": "PRACTICE",
      "result": "RED_WIN",
      "resultReason": "CHECKMATE",
      "status": "FINISHED",
      "hintCount": 1,
      "undoCount": 0,
      "dangerCheckCount": 0,
      "clockInitialMillis": 0,
      "redRemainingMillis": 0,
      "blackRemainingMillis": 0,
      "moves": [
        {
          "plyIndex": 0,
          "side": "RED",
          "fromSquare": "h2",
          "toSquare": "e2",
          "pieceType": "CANNON",
          "capturedPieceType": null,
          "notation": "P2-5",
          "notationFull": "Pháo 2 bình 5",
          "positionHashAfter": 123456789,
          "gaveCheck": false,
          "playedAt": 1763855010000
        }
      ]
    }
  ],
  "puzzles": [
    {
      "catalogKey": null,
      "sourceType": "GAME_MISTAKE",
      "initialFen": "...",
      "historyData": "",
      "sideToMove": "RED",
      "title": "Sửa sai nước 14 (Cấp 4)",
      "theme": "FORK",
      "playedMistakeMove": "b2e2",
      "acceptedMoves": "e6d8",
      "solutionVariations": "e6d8 f9e9 d8b7",
      "commentary": "...",
      "engineVersion": "1.0",
      "analysisConfig": "balanced-full",
      "createdAt": 1763855600000,
      "progress": {
        "attemptCount": 2,
        "correctCount": 1,
        "consecutiveCorrectCount": 1,
        "unassistedCorrectCount": 1,
        "highestHintLevelUsed": 0,
        "lastAttemptAt": 1763855700000,
        "nextReviewAt": 1763942100000
      }
    }
  ]
}
```

## Quy tắc kiểm chứng khi nhập

1. Kiểm tra `format == "cotuong-luyenky-backup"` và `schemaVersion <= SUPPORTED_SCHEMA_VERSION`. Nếu phiên bản file mới hơn phiên bản ứng dụng, từ chối và thông báo người dùng cập nhật ứng dụng.
2. Mọi `initialFen` đều được kiểm chứng qua `FenCodec.parse()`.
3. Toàn bộ danh sách `moves` của từng ván được tái diễn tuần tự qua `RulesEngine.isMoveLegal()`; các bản ghi lỗi được bỏ qua an toàn thay vì làm lỗi ứng dụng.
4. Không bao giờ ghi đè toàn bộ dữ liệu nếu người dùng chưa xác nhận rõ ràng trong hộp thoại nhập (mặc định hỗ trợ cả **Gộp thêm** và **Thay thế**).
5. File nhập chỉ được phân tích như dữ liệu JSON thuần với giới hạn kích thước và độ sâu lồng nhau.
