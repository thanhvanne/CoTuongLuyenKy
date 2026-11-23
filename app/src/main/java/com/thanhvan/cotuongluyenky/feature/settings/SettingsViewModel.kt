package com.thanhvan.cotuongluyenky.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.thanhvan.cotuongluyenky.BuildConfig
import com.thanhvan.cotuongluyenky.core.model.BoardThemeStyle
import com.thanhvan.cotuongluyenky.core.model.EngineResourceMode
import com.thanhvan.cotuongluyenky.core.model.NotationStyle
import com.thanhvan.cotuongluyenky.core.model.PieceDisplayStyle
import com.thanhvan.cotuongluyenky.data.database.CoTuongDatabase
import com.thanhvan.cotuongluyenky.data.datastore.UserSettings
import com.thanhvan.cotuongluyenky.data.datastore.UserSettingsStore
import com.thanhvan.cotuongluyenky.data.importexport.BackupExportImportService
import com.thanhvan.cotuongluyenky.data.importexport.BackupParseResult
import com.thanhvan.cotuongluyenky.data.repository.GameRepository
import com.thanhvan.cotuongluyenky.data.repository.PuzzleRepository
import com.thanhvan.cotuongluyenky.engine.api.EngineInfo
import com.thanhvan.cotuongluyenky.engine.manager.EngineManager
import com.thanhvan.cotuongluyenky.service.sound.GameSoundEffect
import com.thanhvan.cotuongluyenky.service.sound.SoundEffectManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DataBackupUiState(
    val storageSizeText: String = "",
    val pendingImport: BackupParseResult.Valid? = null,
    val statusMessage: String? = null,
    val isError: Boolean = false
)

class SettingsViewModel(
    private val appContext: Context,
    private val settingsStore: UserSettingsStore,
    private val gameRepository: GameRepository,
    private val puzzleRepository: PuzzleRepository,
    private val backupService: BackupExportImportService,
    private val engineManager: EngineManager,
    private val soundEffectManager: SoundEffectManager
) : ViewModel() {

    val settings: StateFlow<UserSettings> = settingsStore.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UserSettings()
    )

    private val _backupState = MutableStateFlow(DataBackupUiState(storageSizeText = computeStorageSize()))
    val backupState: StateFlow<DataBackupUiState> = _backupState.asStateFlow()

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
        refreshStorageSize("Đã xóa bộ nhớ đệm phân tích.")
    }

    fun clearUserPuzzles() = viewModelScope.launch {
        puzzleRepository.clearUserPuzzles()
        refreshStorageSize("Đã xóa toàn bộ bài tập cá nhân (giữ lại bộ bài mẫu chuẩn).")
    }

    fun clearHistory() = viewModelScope.launch {
        gameRepository.clearAllHistory()
        refreshStorageSize("Đã xóa toàn bộ lịch sử ván đấu.")
    }

    fun exportBackup(onJsonReady: (String) -> Unit) = viewModelScope.launch {
        try {
            val json = backupService.exportBackupJson(BuildConfig.VERSION_NAME)
            onJsonReady(json)
            refreshStorageSize("Đã xuất dữ liệu sao lưu thành công (v${BackupExportImportService.SUPPORTED_SCHEMA_VERSION}).")
        } catch (t: Throwable) {
            _backupState.value = _backupState.value.copy(
                statusMessage = "Không thể xuất dữ liệu: ${t.message ?: "lỗi không xác định"}",
                isError = true
            )
        }
    }

    /**
     * Validates an imported JSON payload BEFORE mutating any user data (Section 20.9).
     */
    fun inspectImportJson(rawJson: String) {
        when (val result = BackupExportImportService.parseAndValidateBackup(rawJson)) {
            is BackupParseResult.Valid -> {
                _backupState.value = _backupState.value.copy(
                    pendingImport = result,
                    statusMessage = null,
                    isError = false
                )
            }
            is BackupParseResult.Error -> {
                _backupState.value = _backupState.value.copy(
                    pendingImport = null,
                    statusMessage = result.message,
                    isError = true
                )
            }
        }
    }

    fun confirmImport(replaceExisting: Boolean) = viewModelScope.launch {
        val pending = _backupState.value.pendingImport ?: return@launch
        try {
            val summary = backupService.applyValidatedImport(pending, replaceExisting)
            val skipNote = if (summary.skippedInvalidEntries > 0) {
                " (bỏ qua ${summary.skippedInvalidEntries} mục lỗi)"
            } else ""
            _backupState.value = _backupState.value.copy(pendingImport = null)
            refreshStorageSize(
                "Đã nhập ${summary.importedGames} ván và ${summary.importedPuzzles} bài tập$skipNote."
            )
        } catch (t: Throwable) {
            _backupState.value = _backupState.value.copy(
                pendingImport = null,
                statusMessage = "Lỗi khi ghi dữ liệu nhập: ${t.message ?: "không rõ"}",
                isError = true
            )
        }
    }

    fun cancelPendingImport() {
        _backupState.value = _backupState.value.copy(pendingImport = null)
    }

    fun dismissBackupStatus() {
        _backupState.value = _backupState.value.copy(statusMessage = null)
    }

    private fun refreshStorageSize(message: String) {
        _backupState.value = _backupState.value.copy(
            storageSizeText = computeStorageSize(),
            statusMessage = message,
            isError = false
        )
    }

    private fun computeStorageSize(): String {
        val dbFile = appContext.getDatabasePath(CoTuongDatabase.DATABASE_NAME)
        val bytes = listOf(
            dbFile,
            java.io.File(dbFile.path + "-wal"),
            java.io.File(dbFile.path + "-shm")
        ).sumOf { if (it.exists()) it.length() else 0L }
        return when {
            bytes < 1024L -> "$bytes B"
            bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
            else -> String.format(java.util.Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
        }
    }

    class Factory(
        private val appContext: Context,
        private val settingsStore: UserSettingsStore,
        private val gameRepository: GameRepository,
        private val puzzleRepository: PuzzleRepository,
        private val backupService: BackupExportImportService,
        private val engineManager: EngineManager,
        private val soundEffectManager: SoundEffectManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(
                appContext = appContext,
                settingsStore = settingsStore,
                gameRepository = gameRepository,
                puzzleRepository = puzzleRepository,
                backupService = backupService,
                engineManager = engineManager,
                soundEffectManager = soundEffectManager
            ) as T
        }
    }
}
