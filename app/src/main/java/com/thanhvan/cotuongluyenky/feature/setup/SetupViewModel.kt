package com.thanhvan.cotuongluyenky.feature.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.thanhvan.cotuongluyenky.core.model.ClockOption
import com.thanhvan.cotuongluyenky.core.model.GameMode
import com.thanhvan.cotuongluyenky.core.model.PlayerSideChoice
import com.thanhvan.cotuongluyenky.core.model.Side
import com.thanhvan.cotuongluyenky.data.datastore.UserSettingsStore
import com.thanhvan.cotuongluyenky.data.repository.GameRepository
import com.thanhvan.cotuongluyenky.engine.manager.EngineManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

data class SetupUiState(
    val engineLevel: Int = 3,
    val sideChoice: PlayerSideChoice = PlayerSideChoice.RED,
    val mode: GameMode = GameMode.PRACTICE,
    val clockOption: ClockOption = ClockOption.UNLIMITED,
    val isCreating: Boolean = false
)

class SetupViewModel(
    private val gameRepository: GameRepository,
    private val settingsStore: UserSettingsStore,
    private val engineManager: EngineManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SetupUiState())
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = settingsStore.settingsFlow.first()
            _uiState.update {
                it.copy(
                    engineLevel = saved.defaultEngineLevel,
                    sideChoice = saved.defaultPlayerSide,
                    mode = saved.defaultGameMode,
                    clockOption = saved.defaultClockOption
                )
            }
        }
    }

    fun setEngineLevel(level: Int) {
        _uiState.update { it.copy(engineLevel = level.coerceIn(1, 10)) }
    }

    fun setSideChoice(sideChoice: PlayerSideChoice) {
        _uiState.update { it.copy(sideChoice = sideChoice) }
    }

    fun setGameMode(mode: GameMode) {
        _uiState.update { it.copy(mode = mode) }
    }

    fun setClockOption(clockOption: ClockOption) {
        _uiState.update { it.copy(clockOption = clockOption) }
    }

    fun startGame(onGameCreated: (Long) -> Unit) {
        val state = _uiState.value
        if (state.isCreating) return
        _uiState.update { it.copy(isCreating = true) }

        viewModelScope.launch {
            settingsStore.updateSetupDefaults(
                level = state.engineLevel,
                side = state.sideChoice,
                mode = state.mode,
                clockOption = state.clockOption
            )

            val resolvedSide = when (state.sideChoice) {
                PlayerSideChoice.RED -> Side.RED
                PlayerSideChoice.BLACK -> Side.BLACK
                PlayerSideChoice.RANDOM -> if (Random.nextBoolean()) Side.RED else Side.BLACK
            }

            val settings = settingsStore.settingsFlow.first()
            val engineInfo = engineManager.ensureInitialized(settings.engineResourceMode)

            val gameId = gameRepository.createNewGame(
                playerSide = resolvedSide,
                engineInfo = engineInfo,
                engineLevel = state.engineLevel,
                mode = state.mode,
                clockOption = state.clockOption
            )
            _uiState.update { it.copy(isCreating = false) }
            onGameCreated(gameId)
        }
    }

    class Factory(
        private val gameRepository: GameRepository,
        private val settingsStore: UserSettingsStore,
        private val engineManager: EngineManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SetupViewModel(gameRepository, settingsStore, engineManager) as T
        }
    }
}
