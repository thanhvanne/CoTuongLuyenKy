package com.thanhvan.cotuongluyenky

import android.app.Application
import android.content.Context
import com.thanhvan.cotuongluyenky.data.database.CoTuongDatabase
import com.thanhvan.cotuongluyenky.data.datastore.UserSettingsStore
import com.thanhvan.cotuongluyenky.data.importexport.BackupExportImportService
import com.thanhvan.cotuongluyenky.data.repository.GameRepository
import com.thanhvan.cotuongluyenky.data.repository.PuzzleRepository
import com.thanhvan.cotuongluyenky.domain.analysis.AnalysisService
import com.thanhvan.cotuongluyenky.domain.training.StatisticsService
import com.thanhvan.cotuongluyenky.domain.training.TrainingService
import com.thanhvan.cotuongluyenky.engine.builtin.BuiltInXiangqiEngine
import com.thanhvan.cotuongluyenky.engine.manager.EngineManager
import com.thanhvan.cotuongluyenky.engine.nativebridge.PikafishProcessEngine
import com.thanhvan.cotuongluyenky.service.haptic.HapticFeedbackManager
import com.thanhvan.cotuongluyenky.service.sound.SoundEffectManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val database: CoTuongDatabase = CoTuongDatabase.create(appContext)
    val gameRepository: GameRepository = GameRepository(database)
    val puzzleRepository: PuzzleRepository = PuzzleRepository(database)
    val settingsStore: UserSettingsStore = UserSettingsStore(appContext)
    val soundEffectManager: SoundEffectManager = SoundEffectManager(appContext)
    val hapticFeedbackManager: HapticFeedbackManager = HapticFeedbackManager(appContext)

    private val pikafishEngine: PikafishProcessEngine = PikafishProcessEngine(appContext)
    private val builtInEngine: BuiltInXiangqiEngine = BuiltInXiangqiEngine()

    val engineManager: EngineManager = EngineManager(
        primaryEngine = if (pikafishEngine.isAvailable()) pikafishEngine else null,
        fallbackEngine = builtInEngine
    )

    val analysisService: AnalysisService = AnalysisService(
        engineManager = engineManager,
        gameRepository = gameRepository
    )

    val trainingService: TrainingService = TrainingService(
        gameRepository = gameRepository,
        puzzleRepository = puzzleRepository,
        analysisService = analysisService,
        engineManager = engineManager
    )

    val statisticsService: StatisticsService = StatisticsService(
        gameRepository = gameRepository,
        puzzleRepository = puzzleRepository
    )

    val backupService: BackupExportImportService = BackupExportImportService(
        gameRepository = gameRepository,
        puzzleRepository = puzzleRepository
    )

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        appScope.launch {
            runCatching { trainingService.ensureCatalogSeeded() }
        }
    }
}

class App : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun onTerminate() {
        container.soundEffectManager.release()
        super.onTerminate()
    }
}
