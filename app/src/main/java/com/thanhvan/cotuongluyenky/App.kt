package com.thanhvan.cotuongluyenky

import android.app.Application
import android.content.Context
import com.thanhvan.cotuongluyenky.data.database.CoTuongDatabase
import com.thanhvan.cotuongluyenky.data.datastore.UserSettingsStore
import com.thanhvan.cotuongluyenky.data.repository.GameRepository
import com.thanhvan.cotuongluyenky.domain.analysis.AnalysisService
import com.thanhvan.cotuongluyenky.engine.builtin.BuiltInXiangqiEngine
import com.thanhvan.cotuongluyenky.engine.manager.EngineManager
import com.thanhvan.cotuongluyenky.engine.nativebridge.PikafishProcessEngine
import com.thanhvan.cotuongluyenky.service.haptic.HapticFeedbackManager
import com.thanhvan.cotuongluyenky.service.sound.SoundEffectManager

class AppContainer(context: Context) {
    val database: CoTuongDatabase = CoTuongDatabase.create(context)
    val gameRepository: GameRepository = GameRepository(database)
    val settingsStore: UserSettingsStore = UserSettingsStore(context)
    val soundEffectManager: SoundEffectManager = SoundEffectManager(context)
    val hapticFeedbackManager: HapticFeedbackManager = HapticFeedbackManager(context)

    private val pikafishEngine: PikafishProcessEngine = PikafishProcessEngine(context)
    private val builtInEngine: BuiltInXiangqiEngine = BuiltInXiangqiEngine()

    val engineManager: EngineManager = EngineManager(
        primaryEngine = if (pikafishEngine.isAvailable()) pikafishEngine else null,
        fallbackEngine = builtInEngine
    )

    val analysisService: AnalysisService = AnalysisService(
        engineManager = engineManager,
        gameRepository = gameRepository
    )
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
