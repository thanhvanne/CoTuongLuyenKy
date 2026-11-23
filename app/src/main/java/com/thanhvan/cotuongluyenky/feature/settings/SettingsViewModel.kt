package com.thanhvan.cotuongluyenky.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.thanhvan.cotuongluyenky.core.model.BoardThemeStyle
import com.thanhvan.cotuongluyenky.core.model.EngineResourceMode
import com.thanhvan.cotuongluyenky.core.model.NotationStyle
import com.thanhvan.cotuongluyenky.core.model.PieceDisplayStyle
import com.thanhvan.cotuongluyenky.data.datastore.UserSettings
import com.thanhvan.cotuongluyenky.data.datastore.UserSettingsStore
import com.thanhvan.cotuongluyenky.data.repository.GameRepository
import com.thanhvan.cotuongluyenky.engine.api.EngineInfo
import com.thanhvan.cotuongluyenky.engine.manager.EngineManager
import com.thanhvan.cotuongluyenky.service.sound.GameSoundEffect
import com.thanhvan.cotuongluyenky.service.sound.SoundEffectManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsStore: UserSettingsStore,
    private val gameRepository: GameRepository,
    private val engineManager: EngineManager,
    private val soundEffectManager: SoundEffectManager
) : ViewModel() {

    val settings: StateFlow<UserSettings> = settingsStore.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UserSettings()
    )

    val engineInfo: EngineInfo
        get() = engineManager.activeEngineInfo

    fun setPieceDisplayStyle(style: PieceDisplayStyle) = viewModelScope.launch {
        settingsStore.setPieceDisplayStyle(style)
    }

    fun setBoardThemeStyle(style: BoardThemeStyle) = viewModelScope.launch {
        settingsStore.setBoardThemeStyle(style)
    }

    fun setNotationStyle(style: NotationStyle) = viewModelScope.launch {
        settingsStore.setNotationStyle(style)
    }

    fun setShowCoordinates(enabled: Boolean) = viewModelScope.launch {
        settingsStore.setShowCoordinates(enabled)
    }

    fun setReduceMotion(enabled: Boolean) = viewModelScope.launch {
        settingsStore.setReduceMotion(enabled)
    }

    fun setSoundEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsStore.setSoundEnabled(enabled)
    }

    fun setSoundVolume(volume: Float) = viewModelScope.launch {
        settingsStore.setSoundVolume(volume)
    }

    fun testSound() {
        val current = settings.value
        soundEffectManager.play(GameSoundEffect.MOVE, enabled = true, volume = current.soundVolume)
    }

    fun setHapticEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsStore.setHapticEnabled(enabled)
    }

    fun setEngineResourceMode(mode: EngineResourceMode) = viewModelScope.launch {
        settingsStore.setEngineResourceMode(mode)
    }

    fun setLiveEvaluationEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsStore.setLiveEvaluationEnabled(enabled)
    }

    fun setShowEvalBar(enabled: Boolean) = viewModelScope.launch {
        settingsStore.setShowEvalBar(enabled)
    }

    fun setOnlyWarnMajorBlunders(enabled: Boolean) = viewModelScope.launch {
        settingsStore.setOnlyWarnMajorBlunders(enabled)
    }

    fun setShowGoodMoveEffects(enabled: Boolean) = viewModelScope.launch {
        settingsStore.setShowGoodMoveEffects(enabled)
    }

    fun setVibrateOnBlunder(enabled: Boolean) = viewModelScope.launch {
        settingsStore.setVibrateOnBlunder(enabled)
    }

    fun setHintsEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsStore.setHintsEnabled(enabled)
    }

    fun clearAnalysisCache() = viewModelScope.launch {
        gameRepository.clearAllAnalysisCache()
    }

    fun clearHistory() = viewModelScope.launch {
        gameRepository.clearAllHistory()
    }

    class Factory(
        private val settingsStore: UserSettingsStore,
        private val gameRepository: GameRepository,
        private val engineManager: EngineManager,
        private val soundEffectManager: SoundEffectManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(settingsStore, gameRepository, engineManager, soundEffectManager) as T
        }
    }
}
