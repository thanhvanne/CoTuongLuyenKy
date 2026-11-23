package com.thanhvan.cotuongluyenky.feature.result

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.thanhvan.cotuongluyenky.data.repository.GameRepository
import com.thanhvan.cotuongluyenky.data.repository.PersistedGame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ResultUiState(
    val isLoading: Boolean = true,
    val game: PersistedGame? = null
)

class ResultViewModel(
    private val gameId: Long,
    private val gameRepository: GameRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ResultUiState())
    val uiState: StateFlow<ResultUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val loaded = gameRepository.loadGame(gameId)
            _uiState.value = ResultUiState(isLoading = false, game = loaded)
        }
    }

    class Factory(
        private val gameId: Long,
        private val gameRepository: GameRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ResultViewModel(gameId, gameRepository) as T
        }
    }
}
