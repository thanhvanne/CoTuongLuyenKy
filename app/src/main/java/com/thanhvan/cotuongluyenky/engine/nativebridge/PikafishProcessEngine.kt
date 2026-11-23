package com.thanhvan.cotuongluyenky.engine.nativebridge

import android.content.Context
import com.thanhvan.cotuongluyenky.core.model.Move
import com.thanhvan.cotuongluyenky.core.model.PositionWithHistory
import com.thanhvan.cotuongluyenky.domain.rules.RulesEngine
import com.thanhvan.cotuongluyenky.engine.api.EngineAnalysis
import com.thanhvan.cotuongluyenky.engine.api.EngineConfig
import com.thanhvan.cotuongluyenky.engine.api.EngineInfo
import com.thanhvan.cotuongluyenky.engine.api.EngineMoveResult
import com.thanhvan.cotuongluyenky.engine.api.EngineScore
import com.thanhvan.cotuongluyenky.engine.api.EngineVariation
import com.thanhvan.cotuongluyenky.engine.api.SearchLimit
import com.thanhvan.cotuongluyenky.engine.api.XiangqiEngine
import com.thanhvan.cotuongluyenky.engine.protocol.UciInfo
import com.thanhvan.cotuongluyenky.engine.protocol.UciProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter

/**
 * Pikafish native UCI engine driver for Android 10+ (API 29+).
 *
 * Expects:
 * - Native executable packaged as `jniLibs/<abi>/libpikafish.so` (accessible in `nativeLibraryDir`).
 * - Optional NNUE network packaged in `assets/engine/pikafish.nnue`.
 *
 * All returned moves and principal variations are strictly verified by [RulesEngine]
 * before being surfaced to gameplay or analysis.
 */
class PikafishProcessEngine(
    private val context: Context
) : XiangqiEngine {

    private var process: Process? = null
    private var writer: BufferedWriter? = null
    private var reader: BufferedReader? = null
    private var detectedName: String = "Pikafish"
    private var detectedVersion: String = "native-uci"

    override val info: EngineInfo
        get() = EngineInfo(
            name = detectedName,
            version = detectedVersion,
            isNativePikafish = true
        )

    fun isAvailable(): Boolean {
        val binary = resolveBinaryFile()
        return binary.exists() && binary.canExecute()
    }

    override suspend fun initialize(config: EngineConfig) = withContext(Dispatchers.IO) {
        close()
        val binary = resolveBinaryFile()
        require(binary.exists() && binary.canExecute()) {
            "Không tìm thấy binary Pikafish khả thi tại ${binary.absolutePath}"
        }

        val pb = ProcessBuilder(binary.absolutePath)
            .redirectErrorStream(true)
        val started = pb.start()
        process = started
        writer = BufferedWriter(OutputStreamWriter(started.outputStream, Charsets.UTF_8))
        reader = BufferedReader(InputStreamReader(started.inputStream, Charsets.UTF_8))

        sendLine("uci")
        while (true) {
            val line = reader?.readLine() ?: error("Pikafish kết thúc đột ngột khi khởi tạo UCI.")
            if (line.startsWith("id name ")) {
                val raw = line.removePrefix("id name ").trim()
                if (raw.isNotEmpty()) {
                    detectedName = "Pikafish"
                    detectedVersion = raw
                }
            }
            if (line.trim() == "uciok") break
        }

        sendLine(UciProtocol.buildSetOptionCommand("Threads", config.threads.coerceAtLeast(1).toString()))
        sendLine(UciProtocol.buildSetOptionCommand("Hash", config.hashMb.coerceAtLeast(8).toString()))

        val evalPath = config.evalFilePath ?: extractBundledNnueIfPresent()
        if (evalPath != null && File(evalPath).exists()) {
            sendLine(UciProtocol.buildSetOptionCommand("EvalFile", evalPath))
        }

        sendLine("isready")
        waitForToken("readyok")
    }

    override suspend fun findBestMove(
        position: PositionWithHistory,
        limit: SearchLimit
    ): EngineMoveResult {
        val analysis = analyze(position, limit, variations = 1)
        val best = analysis.variations.firstOrNull()
            ?: error("Pikafish không trả về nước đi hợp lệ.")
        return EngineMoveResult(
            bestMove = best.moves.first(),
            score = best.score,
            depth = analysis.depth,
            nodes = analysis.nodes,
            elapsedMs = analysis.elapsedMs,
            principalVariation = best.moves,
            engineInfo = info
        )
    }

    override suspend fun analyze(
        position: PositionWithHistory,
        limit: SearchLimit,
        variations: Int
    ): EngineAnalysis = withContext(Dispatchers.IO) {
        ensureRunning()
        val startMs = System.currentTimeMillis()
        val safeMultiPv = variations.coerceIn(1, 8)
        sendLine(UciProtocol.buildSetOptionCommand("MultiPV", safeMultiPv.toString()))
        sendLine("isready")
        waitForToken("readyok")

        sendLine(UciProtocol.buildPositionCommand(position))
        sendLine(UciProtocol.buildGoCommand(limit))

        val latestByRank = LinkedHashMap<Int, UciInfo>()
        var bestMoveFromEngine: Move? = null
        var maxDepth = 0
        var lastNodes = 0L

        while (true) {
            val line = reader?.readLine() ?: break
            val parsedInfo = UciProtocol.parseInfoLine(line)
            if (parsedInfo != null) {
                latestByRank[parsedInfo.multiPv] = parsedInfo
                if (parsedInfo.depth > maxDepth) maxDepth = parsedInfo.depth
                if (parsedInfo.nodes > lastNodes) lastNodes = parsedInfo.nodes
                continue
            }
            val bestParsed = UciProtocol.parseBestMoveLine(line)
            if (bestParsed != null) {
                bestMoveFromEngine = bestParsed.bestMove
                break
            }
        }

        val board = position.currentBoard
        val verifiedVariations = latestByRank.entries
            .sortedBy { it.key }
            .mapNotNull { (rank, uciInfo) ->
                val verifiedPv = validatePv(board, uciInfo.pv)
                if (verifiedPv.isEmpty()) return@mapNotNull null
                EngineVariation(
                    rank = rank,
                    score = uciInfo.score ?: EngineScore.Centipawns(0),
                    depth = uciInfo.depth,
                    moves = verifiedPv
                )
            }
            .toMutableList()

        if (verifiedVariations.isEmpty() && bestMoveFromEngine != null && RulesEngine.isMoveLegal(board, bestMoveFromEngine)) {
            verifiedVariations.add(
                EngineVariation(
                    rank = 1,
                    score = EngineScore.Centipawns(0),
                    depth = maxDepth,
                    moves = listOf(bestMoveFromEngine)
                )
            )
        }

        EngineAnalysis(
            variations = verifiedVariations,
            depth = maxDepth,
            nodes = lastNodes,
            elapsedMs = (System.currentTimeMillis() - startMs).coerceAtLeast(1L),
            engineInfo = info
        )
    }

    override suspend fun stop() = withContext(Dispatchers.IO) {
        try {
            sendLine("stop")
        } catch (_: Exception) {
            // Engine process may already be closed
        }
    }

    override suspend fun close() = withContext(Dispatchers.IO) {
        try {
            writer?.write("quit\n")
            writer?.flush()
        } catch (_: Exception) {
        }
        try {
            writer?.close()
            reader?.close()
            process?.destroy()
        } catch (_: Exception) {
        }
        writer = null
        reader = null
        process = null
    }

    private fun resolveBinaryFile(): File =
        File(context.applicationInfo.nativeLibraryDir, "libpikafish.so")

    private fun extractBundledNnueIfPresent(): String? {
        val outDir = File(context.filesDir, "engine")
        val outFile = File(outDir, "pikafish.nnue")
        if (outFile.exists() && outFile.length() > 0L) {
            return outFile.absolutePath
        }
        return try {
            val assetList = context.assets.list("engine").orEmpty()
            if ("pikafish.nnue" !in assetList) return null
            outDir.mkdirs()
            context.assets.open("engine/pikafish.nnue").use { input ->
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                }
            }
            outFile.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    private fun ensureRunning() {
        check(process != null && process?.isAlive == true && writer != null && reader != null) {
            "Engine Pikafish chưa được khởi tạo."
        }
    }

    private fun sendLine(cmd: String) {
        val w = writer ?: error("Engine writer không sẵn sàng.")
        w.write(cmd)
        w.newLine()
        w.flush()
    }

    private fun waitForToken(token: String) {
        while (true) {
            val line = reader?.readLine() ?: error("Engine đóng trước khi nhận '$token'.")
            if (line.trim() == token) return
        }
    }

    private fun validatePv(startBoard: com.thanhvan.cotuongluyenky.core.model.BoardState, pv: List<Move>): List<Move> {
        val valid = ArrayList<Move>(pv.size)
        var current = startBoard
        for (move in pv) {
            if (!RulesEngine.isMoveLegal(current, move)) break
            valid.add(move)
            current = RulesEngine.applyMoveUnchecked(current, move)
        }
        return valid
    }
}
