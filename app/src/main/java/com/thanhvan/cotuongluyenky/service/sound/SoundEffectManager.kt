package com.thanhvan.cotuongluyenky.service.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.thanhvan.cotuongluyenky.R

enum class GameSoundEffect {
    MOVE,
    CAPTURE,
    CHECK,
    WIN,
    LOSE,
    DRAW
}

/**
 * Low-latency short sound effect player backed by Android [SoundPool] (Section 3.1 & 19.5).
 * Uses original synthesized WAV assets with zero external licensing restrictions.
 */
class SoundEffectManager(context: Context) {

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(3)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val soundIds: Map<GameSoundEffect, Int> = mapOf(
        GameSoundEffect.MOVE to soundPool.load(context, R.raw.sfx_move, 1),
        GameSoundEffect.CAPTURE to soundPool.load(context, R.raw.sfx_capture, 1),
        GameSoundEffect.CHECK to soundPool.load(context, R.raw.sfx_check, 1),
        GameSoundEffect.WIN to soundPool.load(context, R.raw.sfx_win, 1),
        GameSoundEffect.LOSE to soundPool.load(context, R.raw.sfx_lose, 1),
        GameSoundEffect.DRAW to soundPool.load(context, R.raw.sfx_draw, 1)
    )

    fun play(effect: GameSoundEffect, enabled: Boolean, volume: Float) {
        if (!enabled || volume <= 0f) return
        val id = soundIds[effect] ?: return
        val clamped = volume.coerceIn(0f, 1f)
        soundPool.play(id, clamped, clamped, 1, 0, 1.0f)
    }

    fun release() {
        soundPool.release()
    }
}
