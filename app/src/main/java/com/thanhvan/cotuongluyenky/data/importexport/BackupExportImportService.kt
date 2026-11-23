package com.thanhvan.cotuongluyenky.data.importexport

import com.thanhvan.cotuongluyenky.core.model.Move
import com.thanhvan.cotuongluyenky.data.database.entity.GameEntity
import com.thanhvan.cotuongluyenky.data.database.entity.MoveEntity
import com.thanhvan.cotuongluyenky.data.database.entity.PuzzleEntity
import com.thanhvan.cotuongluyenky.data.database.entity.PuzzleProgressEntity
import com.thanhvan.cotuongluyenky.data.repository.GameRepository
import com.thanhvan.cotuongluyenky.data.repository.PuzzleRepository
import com.thanhvan.cotuongluyenky.domain.rules.FenCodec
import com.thanhvan.cotuongluyenky.domain.rules.FenParseResult
import com.thanhvan.cotuongluyenky.domain.rules.RulesEngine

sealed interface BackupParseResult {
    data class Valid(
        val schemaVersion: Int,
        val appVersion: String,
        val exportedAt: Long,
        val games: List<Pair<GameEntity, List<MoveEntity>>>,
        val puzzles: List<Pair<PuzzleEntity, PuzzleProgressEntity?>>,
        val skippedInvalidEntries: Int
    ) : BackupParseResult

    data class Error(val message: String) : BackupParseResult
}

data class BackupImportSummary(
    val importedGames: Int,
    val importedPuzzles: Int,
    val skippedInvalidEntries: Int
)

/**
 * Versioned JSON backup export/import service (Section 20.9 & 26).
 * Uses a self-contained pure-Kotlin JSON serializer/parser with strict depth & schema validation
 * so it runs identically on Android and JVM unit tests with zero external dependencies.
 */
class BackupExportImportService(
    private val gameRepository: GameRepository,
    private val puzzleRepository: PuzzleRepository
) {
    companion object {
        const val FORMAT_ID = "cotuong-luyenky-backup"
        const val SUPPORTED_SCHEMA_VERSION = 1
        private const val MAX_JSON_DEPTH = 16
        private const val MAX_INPUT_CHARS = 8_000_000

        /**
         * Pure function: builds a versioned JSON backup string from raw entities.
         */
        fun encodeBackupJson(
            appVersion: String,
            exportedAt: Long,
            games: List<Pair<GameEntity, List<MoveEntity>>>,
            puzzles: List<Pair<PuzzleEntity, PuzzleProgressEntity?>>
        ): String {
            val root = linkedMapOf<String, Any?>(
                "format" to FORMAT_ID,
                "schemaVersion" to SUPPORTED_SCHEMA_VERSION,
                "appVersion" to appVersion,
                "exportedAt" to exportedAt,
                "games" to games.map { (g, moves) ->
                    linkedMapOf<String, Any?>(
                        "createdAt" to g.createdAt,
                        "updatedAt" to g.updatedAt,
                        "finishedAt" to g.finishedAt,
                        "initialFen" to g.initialFen,
                        "rulesetId" to g.rulesetId,
                        "playerSide" to g.playerSide,
                        "engineName" to g.engineName,
                        "engineVersion" to g.engineVersion,
                        "engineLevel" to g.engineLevel,
                        "mode" to g.mode,
                        "result" to g.result,
                        "resultReason" to g.resultReason,
                        "status" to g.status,
                        "hintCount" to g.hintCount,
                        "undoCount" to g.undoCount,
                        "dangerCheckCount" to g.dangerCheckCount,
                        "clockInitialMillis" to g.clockInitialMillis,
                        "redRemainingMillis" to g.redRemainingMillis,
                        "blackRemainingMillis" to g.blackRemainingMillis,
                        "moves" to moves.map { m ->
                            linkedMapOf<String, Any?>(
                                "plyIndex" to m.plyIndex,
                                "side" to m.side,
                                "fromSquare" to m.fromSquare,
                                "toSquare" to m.toSquare,
                                "pieceType" to m.pieceType,
                                "capturedPieceType" to m.capturedPieceType,
                                "notationShort" to m.notationShort,
                                "notationFull" to m.notationFull,
                                "positionHashAfter" to m.positionHashAfter,
                                "gaveCheck" to m.gaveCheck,
                                "playedAt" to m.playedAt
                            )
                        }
                    )
                },
                "puzzles" to puzzles.map { (p, pr) ->
                    linkedMapOf<String, Any?>(
                        "catalogKey" to p.catalogKey,
                        "sourceType" to p.sourceType,
                        "initialFen" to p.initialFen,
                        "historyData" to p.historyData,
                        "sideToMove" to p.sideToMove,
                        "title" to p.title,
                        "theme" to p.theme,
                        "playedMistakeMove" to p.playedMistakeMove,
                        "acceptedMoves" to p.acceptedMoves,
                        "solutionVariations" to p.solutionVariations,
                        "commentary" to p.commentary,
                        "engineVersion" to p.engineVersion,
                        "analysisConfig" to p.analysisConfig,
                        "createdAt" to p.createdAt,
                        "progress" to pr?.let {
                            linkedMapOf<String, Any?>(
                                "attemptCount" to it.attemptCount,
                                "correctCount" to it.correctCount,
                                "consecutiveCorrectCount" to it.consecutiveCorrectCount,
                                "unassistedCorrectCount" to it.unassistedCorrectCount,
                                "highestHintLevelUsed" to it.highestHintLevelUsed,
                                "lastAttemptAt" to it.lastAttemptAt,
                                "nextReviewAt" to it.nextReviewAt
                            )
                        }
                    )
                }
            )
            return SimpleJson.stringify(root)
        }

        /**
         * Pure function: parses and validates a backup JSON string without mutating database state.
         */
        fun parseAndValidateBackup(rawJson: String): BackupParseResult {
            if (rawJson.isBlank()) {
                return BackupParseResult.Error("File sao lưu rỗng.")
            }
            if (rawJson.length > MAX_INPUT_CHARS) {
                return BackupParseResult.Error("File sao lưu vượt quá kích thước cho phép.")
            }

            val parsed = try {
                SimpleJson.parse(rawJson)
            } catch (e: Exception) {
                return BackupParseResult.Error("File không đúng định dạng JSON hợp lệ: ${e.message ?: "lỗi cú pháp"}")
            }

            val root = parsed as? Map<*, *>
                ?: return BackupParseResult.Error("Cấu trúc file sao lưu không hợp lệ (thiếu đối tượng gốc).")

            val format = root["format"] as? String
            if (format != FORMAT_ID) {
                return BackupParseResult.Error("Đây không phải file sao lưu của Cờ Tướng – Luyện Kỳ.")
            }

            val schemaVersion = (root["schemaVersion"] as? Number)?.toInt()
                ?: return BackupParseResult.Error("Thiếu thông tin phiên bản định dạng (schemaVersion).")

            if (schemaVersion <= 0) {
                return BackupParseResult.Error("Phiên bản định dạng sao lưu không hợp lệ ($schemaVersion).")
            }
            if (schemaVersion > SUPPORTED_SCHEMA_VERSION) {
                return BackupParseResult.Error(
                    "File sao lưu thuộc phiên bản mới hơn (v$schemaVersion > v$SUPPORTED_SCHEMA_VERSION). Hãy cập nhật ứng dụng trước khi nhập."
                )
            }

            val appVersion = (root["appVersion"] as? String) ?: "unknown"
            val exportedAt = (root["exportedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
            var skipped = 0

            val validGames = mutableListOf<Pair<GameEntity, List<MoveEntity>>>()
            val rawGames = root["games"] as? List<*> ?: emptyList<Any?>()
            for (item in rawGames) {
                val map = item as? Map<*, *>
                if (map == null) {
                    skipped++
                    continue
                }
                val initialFen = map["initialFen"] as? String ?: FenCodec.INITIAL_FEN
                val fenParsed = FenCodec.parse(initialFen)
                if (fenParsed !is FenParseResult.Success) {
                    skipped++
                    continue
                }

                // Replay & validate moves strictly through RulesEngine
                var board = fenParsed.board
                val rawMoves = map["moves"] as? List<*> ?: emptyList<Any?>()
                val moveEntities = mutableListOf<MoveEntity>()
                var movesValid = true
                for ((idx, mItem) in rawMoves.withIndex()) {
                    val mm = mItem as? Map<*, *>
                    val fromSq = mm?.get("fromSquare") as? String
                    val toSq = mm?.get("toSquare") as? String
                    val move = if (fromSq != null && toSq != null) Move.fromUci(fromSq + toSq) else null
                    if (mm == null || move == null || !RulesEngine.isMoveLegal(board, move)) {
                        movesValid = false
                        break
                    }
                    val piece = board.pieceAt(move.from)!!
                    val captured = board.pieceAt(move.to)?.type
                    val after = RulesEngine.applyMoveUnchecked(board, move)
                    moveEntities.add(
                        MoveEntity(
                            gameId = 0L,
                            plyIndex = (mm["plyIndex"] as? Number)?.toInt() ?: idx,
                            side = board.sideToMove.name,
                            fromSquare = fromSq,
                            toSquare = toSq,
                            pieceType = piece.type.name,
                            capturedPieceType = captured?.name,
                            notationShort = (mm["notationShort"] as? String) ?: move.toUci(),
                            notationFull = (mm["notationFull"] as? String) ?: move.toUci(),
                            positionHashAfter = after.positionHash,
                            gaveCheck = RulesEngine.isInCheck(after, after.sideToMove),
                            playedAt = (mm["playedAt"] as? Number)?.toLong() ?: exportedAt
                        )
                    )
                    board = after
                }

                if (!movesValid) {
                    skipped++
                    continue
                }

                val gameEntity = GameEntity(
                    createdAt = (map["createdAt"] as? Number)?.toLong() ?: exportedAt,
                    updatedAt = (map["updatedAt"] as? Number)?.toLong() ?: exportedAt,
                    finishedAt = (map["finishedAt"] as? Number)?.toLong(),
                    initialFen = initialFen,
                    rulesetId = (map["rulesetId"] as? String) ?: "WXF_BASIC_V1",
                    playerSide = (map["playerSide"] as? String) ?: "RED",
                    engineName = (map["engineName"] as? String) ?: "Engine",
                    engineVersion = (map["engineVersion"] as? String) ?: "1.0",
                    engineLevel = ((map["engineLevel"] as? Number)?.toInt() ?: 3).coerceIn(1, 10),
                    mode = (map["mode"] as? String) ?: "PRACTICE",
                    result = (map["result"] as? String) ?: "ONGOING",
                    resultReason = (map["resultReason"] as? String) ?: "NONE",
                    status = (map["status"] as? String) ?: "FINISHED",
                    hintCount = ((map["hintCount"] as? Number)?.toInt() ?: 0).coerceAtLeast(0),
                    undoCount = ((map["undoCount"] as? Number)?.toInt() ?: 0).coerceAtLeast(0),
                    dangerCheckCount = ((map["dangerCheckCount"] as? Number)?.toInt() ?: 0).coerceAtLeast(0),
                    analysisStatus = "NOT_ANALYZED",
                    clockInitialMillis = (map["clockInitialMillis"] as? Number)?.toLong() ?: 0L,
                    redRemainingMillis = (map["redRemainingMillis"] as? Number)?.toLong() ?: 0L,
                    blackRemainingMillis = (map["blackRemainingMillis"] as? Number)?.toLong() ?: 0L
                )
                validGames.add(gameEntity to moveEntities)
            }

            val validPuzzles = mutableListOf<Pair<PuzzleEntity, PuzzleProgressEntity?>>()
            val rawPuzzles = root["puzzles"] as? List<*> ?: emptyList<Any?>()
            for (item in rawPuzzles) {
                val map = item as? Map<*, *>
                if (map == null) {
                    skipped++
                    continue
                }
                val fen = map["initialFen"] as? String
                val fenParsed = if (fen != null) FenCodec.parse(fen) else null
                if (fen == null || fenParsed !is FenParseResult.Success) {
                    skipped++
                    continue
                }
                val acceptedRaw = (map["acceptedMoves"] as? String).orEmpty()
                val legalAccepted = acceptedRaw.split(",")
                    .mapNotNull { Move.fromUci(it.trim()) }
                    .filter { RulesEngine.isMoveLegal(fenParsed.board, it) }
                if (legalAccepted.isEmpty()) {
                    skipped++
                    continue
                }
                val createdAt = (map["createdAt"] as? Number)?.toLong() ?: exportedAt
                val puzzle = PuzzleEntity(
                    catalogKey = map["catalogKey"] as? String,
                    sourceType = (map["sourceType"] as? String) ?: "IMPORTED",
                    sourceGameId = null,
                    sourcePlyIndex = null,
                    initialFen = fen,
                    historyData = (map["historyData"] as? String).orEmpty(),
                    sideToMove = fenParsed.board.sideToMove.name,
                    title = (map["title"] as? String)?.takeIf { it.isNotBlank() } ?: "Bài tập đã nhập",
                    theme = (map["theme"] as? String) ?: "MISTAKE_FIX",
                    playedMistakeMove = map["playedMistakeMove"] as? String,
                    acceptedMoves = legalAccepted.joinToString(",") { it.toUci() },
                    solutionVariations = (map["solutionVariations"] as? String).orEmpty(),
                    commentary = (map["commentary"] as? String).orEmpty(),
                    engineVersion = (map["engineVersion"] as? String) ?: "imported",
                    analysisConfig = (map["analysisConfig"] as? String) ?: "imported",
                    createdAt = createdAt
                )
                val prMap = map["progress"] as? Map<*, *>
                val progress = prMap?.let {
                    PuzzleProgressEntity(
                        puzzleId = 0L,
                        attemptCount = ((it["attemptCount"] as? Number)?.toInt() ?: 0).coerceAtLeast(0),
                        correctCount = ((it["correctCount"] as? Number)?.toInt() ?: 0).coerceAtLeast(0),
                        consecutiveCorrectCount = ((it["consecutiveCorrectCount"] as? Number)?.toInt() ?: 0).coerceAtLeast(0),
                        unassistedCorrectCount = ((it["unassistedCorrectCount"] as? Number)?.toInt() ?: 0).coerceAtLeast(0),
                        highestHintLevelUsed = ((it["highestHintLevelUsed"] as? Number)?.toInt() ?: 0).coerceAtLeast(0),
                        lastAttemptAt = (it["lastAttemptAt"] as? Number)?.toLong(),
                        nextReviewAt = (it["nextReviewAt"] as? Number)?.toLong() ?: createdAt
                    )
                }
                validPuzzles.add(puzzle to progress)
            }

            return BackupParseResult.Valid(
                schemaVersion = schemaVersion,
                appVersion = appVersion,
                exportedAt = exportedAt,
                games = validGames,
                puzzles = validPuzzles,
                skippedInvalidEntries = skipped
            )
        }
    }

    suspend fun exportBackupJson(appVersion: String): String {
        val games = gameRepository.getAllGamesWithRawMoves()
        val (puzzles, progresses) = puzzleRepository.getAllRawForExport()
        val progressMap = progresses.associateBy { it.puzzleId }
        val puzzlePairs = puzzles.map { p -> p to progressMap[p.id] }
        return encodeBackupJson(
            appVersion = appVersion,
            exportedAt = System.currentTimeMillis(),
            games = games,
            puzzles = puzzlePairs
        )
    }

    suspend fun applyValidatedImport(
        validated: BackupParseResult.Valid,
        replaceExisting: Boolean
    ): BackupImportSummary {
        val importedGames = gameRepository.importGamesWithMoves(validated.games, replaceExisting)
        val importedPuzzles = puzzleRepository.importPuzzles(validated.puzzles, replaceExisting)
        return BackupImportSummary(
            importedGames = importedGames,
            importedPuzzles = importedPuzzles,
            skippedInvalidEntries = validated.skippedInvalidEntries
        )
    }
}

/**
 * Minimal, strict RFC-8259 JSON serializer & parser with recursion depth guard.
 */
internal object SimpleJson {

    fun stringify(value: Any?): String {
        val sb = StringBuilder(2048)
        writeValue(sb, value)
        return sb.toString()
    }

    private fun writeValue(sb: StringBuilder, value: Any?) {
        when (value) {
            null -> sb.append("null")
            is Boolean -> sb.append(if (value) "true" else "false")
            is Number -> sb.append(value.toString())
            is String -> writeString(sb, value)
            is Map<*, *> -> {
                sb.append('{')
                var first = true
                for ((k, v) in value) {
                    if (!first) sb.append(',')
                    first = false
                    writeString(sb, k.toString())
                    sb.append(':')
                    writeValue(sb, v)
                }
                sb.append('}')
            }
            is Iterable<*> -> {
                sb.append('[')
                var first = true
                for (item in value) {
                    if (!first) sb.append(',')
                    first = false
                    writeValue(sb, item)
                }
                sb.append(']')
            }
            else -> writeString(sb, value.toString())
        }
    }

    private fun writeString(sb: StringBuilder, s: String) {
        sb.append('"')
        for (ch in s) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (ch.code < 0x20) {
                    sb.append(String.format("\\u%04x", ch.code))
                } else {
                    sb.append(ch)
                }
            }
        }
        sb.append('"')
    }

    fun parse(input: String): Any? {
        val parser = Parser(input)
        val result = parser.parseValue(0)
        parser.skipWhitespace()
        if (!parser.isAtEnd()) {
            throw IllegalArgumentException("Ký tự thừa ở vị trí ${parser.pos}")
        }
        return result
    }

    private class Parser(private val s: String) {
        var pos = 0

        fun isAtEnd(): Boolean = pos >= s.length

        fun skipWhitespace() {
            while (pos < s.length && s[pos] in charArrayOf(' ', '\t', '\r', '\n')) {
                pos++
            }
        }

        fun parseValue(depth: Int): Any? {
            if (depth > 16) throw IllegalArgumentException("Độ sâu JSON vượt quá giới hạn.")
            skipWhitespace()
            if (isAtEnd()) throw IllegalArgumentException("Kết thúc JSON bất ngờ.")
            return when (s[pos]) {
                '{' -> parseObject(depth + 1)
                '[' -> parseArray(depth + 1)
                '"' -> parseString()
                't' -> { expect("true"); true }
                'f' -> { expect("false"); false }
                'n' -> { expect("null"); null }
                '-', in '0'..'9' -> parseNumber()
                else -> throw IllegalArgumentException("Ký tự không hợp lệ '${s[pos]}' tại vị trí $pos")
            }
        }

        private fun parseObject(depth: Int): Map<String, Any?> {
            pos++ // '{'
            skipWhitespace()
            val map = linkedMapOf<String, Any?>()
            if (!isAtEnd() && s[pos] == '}') {
                pos++
                return map
            }
            while (true) {
                skipWhitespace()
                if (isAtEnd() || s[pos] != '"') throw IllegalArgumentException("Thiếu khóa chuỗi tại $pos")
                val key = parseString()
                skipWhitespace()
                if (isAtEnd() || s[pos] != ':') throw IllegalArgumentException("Thiếu dấu ':' tại $pos")
                pos++
                val value = parseValue(depth)
                map[key] = value
                skipWhitespace()
                if (isAtEnd()) throw IllegalArgumentException("Đối tượng JSON chưa đóng.")
                when (s[pos]) {
                    ',' -> pos++
                    '}' -> { pos++; return map }
                    else -> throw IllegalArgumentException("Thiếu ',' hoặc '}' tại $pos")
                }
            }
        }

        private fun parseArray(depth: Int): List<Any?> {
            pos++ // '['
            skipWhitespace()
            val list = mutableListOf<Any?>()
            if (!isAtEnd() && s[pos] == ']') {
                pos++
                return list
            }
            while (true) {
                list.add(parseValue(depth))
                skipWhitespace()
                if (isAtEnd()) throw IllegalArgumentException("Mảng JSON chưa đóng.")
                when (s[pos]) {
                    ',' -> pos++
                    ']' -> { pos++; return list }
                    else -> throw IllegalArgumentException("Thiếu ',' hoặc ']' tại $pos")
                }
            }
        }

        private fun parseString(): String {
            pos++ // opening '"'
            val sb = StringBuilder()
            while (!isAtEnd()) {
                val c = s[pos++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (isAtEnd()) throw IllegalArgumentException("Chuỗi thoát không hoàn chỉnh.")
                        when (val esc = s[pos++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (pos + 4 > s.length) throw IllegalArgumentException("Mã unicode không hợp lệ.")
                                val hex = s.substring(pos, pos + 4)
                                sb.append(hex.toInt(16).toChar())
                                pos += 4
                            }
                            else -> throw IllegalArgumentException("Ký tự thoát không hỗ trợ '\\$esc'")
                        }
                    }
                    else -> sb.append(c)
                }
            }
            throw IllegalArgumentException("Chuỗi JSON chưa đóng.")
        }

        private fun parseNumber(): Number {
            val start = pos
            if (s[pos] == '-') pos++
            while (!isAtEnd() && s[pos] in '0'..'9') pos++
            var isFloat = false
            if (!isAtEnd() && s[pos] == '.') {
                isFloat = true
                pos++
                while (!isAtEnd() && s[pos] in '0'..'9') pos++
            }
            if (!isAtEnd() && (s[pos] == 'e' || s[pos] == 'E')) {
                isFloat = true
                pos++
                if (!isAtEnd() && (s[pos] == '+' || s[pos] == '-')) pos++
                while (!isAtEnd() && s[pos] in '0'..'9') pos++
            }
            val token = s.substring(start, pos)
            return if (isFloat) token.toDouble() else token.toLong()
        }

        private fun expect(literal: String) {
            if (!s.startsWith(literal, pos)) {
                throw IllegalArgumentException("Kỳ vọng '$literal' tại $pos")
            }
            pos += literal.length
        }
    }
}
