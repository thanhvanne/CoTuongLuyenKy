package com.thanhvan.cotuongluyenky.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.thanhvan.cotuongluyenky.core.model.BoardThemeStyle
import com.thanhvan.cotuongluyenky.core.model.ClockOption
import com.thanhvan.cotuongluyenky.core.model.EngineResourceMode
import com.thanhvan.cotuongluyenky.core.model.GameMode
import com.thanhvan.cotuongluyenky.core.model.NotationStyle
import com.thanhvan.cotuongluyenky.core.model.PieceDisplayStyle
import com.thanhvan.cotuongluyenky.core.model.PlayerSideChoice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.userSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "cotuong_user_settings"
)

data class UserSettings(
    val defaultEngineLevel: Int = 3,
    val defaultPlayerSide: PlayerSideChoice = PlayerSideChoice.RED,
    val defaultGameMode: GameMode = GameMode.PRACTICE,
    val defaultClockOption: ClockOption = ClockOption.UNLIMITED,
    val pieceDisplayStyle: PieceDisplayStyle = PieceDisplayStyle.HANZI,
    val boardThemeStyle: BoardThemeStyle = BoardThemeStyle.CLASSIC_WOOD,
    val notationStyle: NotationStyle = NotationStyle.SHORT,
    val showCoordinates: Boolean = true,
    val reduceMotion: Boolean = false,
    val soundEnabled: Boolean = true,
    val soundVolume: Float = 0.85f,
    val hapticEnabled: Boolean = true,
    val engineResourceMode: EngineResourceMode = EngineResourceMode.BALANCED
)

class UserSettingsStore(context: Context) {
    private val dataStore = context.applicationContext.userSettingsDataStore

    private object Keys {
        val DEFAULT_ENGINE_LEVEL = intPreferencesKey("default_engine_level")
        val DEFAULT_PLAYER_SIDE = stringPreferencesKey("default_player_side")
        val DEFAULT_GAME_MODE = stringPreferencesKey("default_game_mode")
        val DEFAULT_CLOCK_MILLIS = longPreferencesKey("default_clock_millis")
        val PIECE_DISPLAY_STYLE = stringPreferencesKey("piece_display_style")
        val BOARD_THEME_STYLE = stringPreferencesKey("board_theme_style")
        val NOTATION_STYLE = stringPreferencesKey("notation_style")
        val SHOW_COORDINATES = booleanPreferencesKey("show_coordinates")
        val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val SOUND_VOLUME = floatPreferencesKey("sound_volume")
        val HAPTIC_ENABLED = booleanPreferencesKey("haptic_enabled")
        val ENGINE_RESOURCE_MODE = stringPreferencesKey("engine_resource_mode")
    }

    val settingsFlow: Flow<UserSettings> = dataStore.data.map { prefs ->
        UserSettings(
            defaultEngineLevel = (prefs[Keys.DEFAULT_ENGINE_LEVEL] ?: 3).coerceIn(1, 10),
            defaultPlayerSide = enumOrDefault(prefs[Keys.DEFAULT_PLAYER_SIDE], PlayerSideChoice.RED),
            defaultGameMode = enumOrDefault(prefs[Keys.DEFAULT_GAME_MODE], GameMode.PRACTICE),
            defaultClockOption = ClockOption.fromMillis(prefs[Keys.DEFAULT_CLOCK_MILLIS] ?: 0L),
            pieceDisplayStyle = enumOrDefault(prefs[Keys.PIECE_DISPLAY_STYLE], PieceDisplayStyle.HANZI),
            boardThemeStyle = enumOrDefault(prefs[Keys.BOARD_THEME_STYLE], BoardThemeStyle.CLASSIC_WOOD),
            notationStyle = enumOrDefault(prefs[Keys.NOTATION_STYLE], NotationStyle.SHORT),
            showCoordinates = prefs[Keys.SHOW_COORDINATES] ?: true,
            reduceMotion = prefs[Keys.REDUCE_MOTION] ?: false,
            soundEnabled = prefs[Keys.SOUND_ENABLED] ?: true,
            soundVolume = (prefs[Keys.SOUND_VOLUME] ?: 0.85f).coerceIn(0f, 1f),
            hapticEnabled = prefs[Keys.HAPTIC_ENABLED] ?: true,
            engineResourceMode = enumOrDefault(prefs[Keys.ENGINE_RESOURCE_MODE], EngineResourceMode.BALANCED)
        )
    }

    suspend fun updateSetupDefaults(
        level: Int,
        side: PlayerSideChoice,
        mode: GameMode,
        clockOption: ClockOption
    ) {
        dataStore.edit { prefs ->
            prefs[Keys.DEFAULT_ENGINE_LEVEL] = level.coerceIn(1, 10)
            prefs[Keys.DEFAULT_PLAYER_SIDE] = side.name
            prefs[Keys.DEFAULT_GAME_MODE] = mode.name
            prefs[Keys.DEFAULT_CLOCK_MILLIS] = clockOption.initialMillis
        }
    }

    suspend fun setDefaultEngineLevel(level: Int) {
        dataStore.edit { it[Keys.DEFAULT_ENGINE_LEVEL] = level.coerceIn(1, 10) }
    }

    suspend fun setDefaultPlayerSide(side: PlayerSideChoice) {
        dataStore.edit { it[Keys.DEFAULT_PLAYER_SIDE] = side.name }
    }

    suspend fun setDefaultGameMode(mode: GameMode) {
        dataStore.edit { it[Keys.DEFAULT_GAME_MODE] = mode.name }
    }

    suspend fun setPieceDisplayStyle(style: PieceDisplayStyle) {
        dataStore.edit { it[Keys.PIECE_DISPLAY_STYLE] = style.name }
    }

    suspend fun setBoardThemeStyle(style: BoardThemeStyle) {
        dataStore.edit { it[Keys.BOARD_THEME_STYLE] = style.name }
    }

    suspend fun setNotationStyle(style: NotationStyle) {
        dataStore.edit { it[Keys.NOTATION_STYLE] = style.name }
    }

    suspend fun setShowCoordinates(enabled: Boolean) {
        dataStore.edit { it[Keys.SHOW_COORDINATES] = enabled }
    }

    suspend fun setReduceMotion(enabled: Boolean) {
        dataStore.edit { it[Keys.REDUCE_MOTION] = enabled }
    }

    suspend fun setSoundEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.SOUND_ENABLED] = enabled }
    }

    suspend fun setSoundVolume(volume: Float) {
        dataStore.edit { it[Keys.SOUND_VOLUME] = volume.coerceIn(0f, 1f) }
    }

    suspend fun setHapticEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.HAPTIC_ENABLED] = enabled }
    }

    suspend fun setEngineResourceMode(mode: EngineResourceMode) {
        dataStore.edit { it[Keys.ENGINE_RESOURCE_MODE] = mode.name }
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(raw: String?, default: T): T {
        if (raw == null) return default
        return runCatching { enumValueOf<T>(raw) }.getOrDefault(default)
    }
}
