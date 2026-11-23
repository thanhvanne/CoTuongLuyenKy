package com.thanhvan.cotuongluyenky.engine.protocol

import com.thanhvan.cotuongluyenky.core.model.Move
import com.thanhvan.cotuongluyenky.core.model.PositionWithHistory
import com.thanhvan.cotuongluyenky.engine.api.EngineScore
import com.thanhvan.cotuongluyenky.engine.api.SearchLimit

data class UciInfo(
    val depth: Int = 0,
    val multiPv: Int = 1,
    val score: EngineScore? = null,
    val nodes: Long = 0L,
    val timeMs: Long = 0L,
    val pv: List<Move> = emptyList()
)

data class UciBestMove(
    val bestMove: Move?,
    val ponderMove: Move?
)

/**
 * UCI protocol serializer and parser for Pikafish (Section 6.5).
 */
object UciProtocol {

    fun buildPositionCommand(position: PositionWithHistory): String {
        val sb = StringBuilder("position fen ")
        sb.append(position.initialFen.trim())
        if (position.moves.isNotEmpty()) {
            sb.append(" moves")
            for (record in position.moves) {
                sb.append(' ').append(record.move.toUci())
            }
        }
        return sb.toString()
    }

    fun buildGoCommand(limit: SearchLimit): String {
        val parts = mutableListOf("go")
        if (limit.maxDepth > 0) {
            parts.add("depth")
            parts.add(limit.maxDepth.toString())
        }
        if (limit.maxNodes != null && limit.maxNodes > 0L) {
            parts.add("nodes")
            parts.add(limit.maxNodes.toString())
        }
        if (limit.moveTimeMs > 0L) {
            parts.add("movetime")
            parts.add(limit.moveTimeMs.toString())
        }
        return parts.joinToString(" ")
    }

    fun buildSetOptionCommand(name: String, value: String): String =
        "setoption name $name value $value"

    fun parseInfoLine(line: String): UciInfo? {
        val trimmed = line.trim()
        if (!trimmed.startsWith("info ")) return null

        val tokens = trimmed.split(Regex("\\s+"))
        var depth = 0
        var multiPv = 1
        var score: EngineScore? = null
        var nodes = 0L
        var timeMs = 0L
        val pv = mutableListOf<Move>()

        var i = 1
        while (i < tokens.size) {
            when (tokens[i]) {
                "string" -> return null // Informational text, not search metrics
                "depth" -> {
                    depth = tokens.getOrNull(i + 1)?.toIntOrNull() ?: depth
                    i += 2
                }
                "multipv" -> {
                    multiPv = tokens.getOrNull(i + 1)?.toIntOrNull() ?: multiPv
                    i += 2
                }
                "nodes" -> {
                    nodes = tokens.getOrNull(i + 1)?.toLongOrNull() ?: nodes
                    i += 2
                }
                "time" -> {
                    timeMs = tokens.getOrNull(i + 1)?.toLongOrNull() ?: timeMs
                    i += 2
                }
                "score" -> {
                    val kind = tokens.getOrNull(i + 1)
                    val rawVal = tokens.getOrNull(i + 2)?.toIntOrNull()
                    if (rawVal != null) {
                        score = when (kind) {
                            "cp" -> EngineScore.Centipawns(rawVal)
                            "mate" -> EngineScore.Mate(rawVal)
                            else -> score
                        }
                    }
                    i += 3
                }
                "pv" -> {
                    i++
                    while (i < tokens.size) {
                        val move = Move.fromUci(tokens[i]) ?: break
                        pv.add(move)
                        i++
                    }
                }
                else -> i++
            }
        }

        return if (depth > 0 || score != null || pv.isNotEmpty()) {
            UciInfo(
                depth = depth,
                multiPv = multiPv,
                score = score,
                nodes = nodes,
                timeMs = timeMs,
                pv = pv
            )
        } else {
            null
        }
    }

    fun parseBestMoveLine(line: String): UciBestMove? {
        val trimmed = line.trim()
        if (!trimmed.startsWith("bestmove ")) return null
        val tokens = trimmed.split(Regex("\\s+"))
        val bestToken = tokens.getOrNull(1) ?: return UciBestMove(null, null)
        val bestMove = if (bestToken == "(none)" || bestToken == "0000") null else Move.fromUci(bestToken)
        val ponderIdx = tokens.indexOf("ponder")
        val ponderMove = if (ponderIdx >= 0) {
            tokens.getOrNull(ponderIdx + 1)?.let { Move.fromUci(it) }
        } else {
            null
        }
        return UciBestMove(bestMove = bestMove, ponderMove = ponderMove)
    }
}
