package com.thanhvan.cotuongluyenky.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.thanhvan.cotuongluyenky.AppContainer
import com.thanhvan.cotuongluyenky.feature.game.GameScreen
import com.thanhvan.cotuongluyenky.feature.game.GameViewModel
import com.thanhvan.cotuongluyenky.feature.history.HistoryScreen
import com.thanhvan.cotuongluyenky.feature.history.HistoryViewModel
import com.thanhvan.cotuongluyenky.feature.home.HomeScreen
import com.thanhvan.cotuongluyenky.feature.home.HomeViewModel
import com.thanhvan.cotuongluyenky.feature.result.ResultScreen
import com.thanhvan.cotuongluyenky.feature.result.ResultViewModel
import com.thanhvan.cotuongluyenky.feature.settings.SettingsScreen
import com.thanhvan.cotuongluyenky.feature.settings.SettingsViewModel
import com.thanhvan.cotuongluyenky.feature.setup.SetupScreen
import com.thanhvan.cotuongluyenky.feature.setup.SetupViewModel
import com.thanhvan.cotuongluyenky.feature.training.TrainingScreen
import com.thanhvan.cotuongluyenky.ui.theme.AccentEmerald
import com.thanhvan.cotuongluyenky.ui.theme.BgCard
import com.thanhvan.cotuongluyenky.ui.theme.BgMain
import com.thanhvan.cotuongluyenky.ui.theme.TextSecondary

object Routes {
    const val HOME = "home"
    const val HISTORY = "history"
    const val TRAINING = "training"
    const val SETUP = "setup"
    const val GAME = "game/{gameId}"
    const val RESULT = "result/{gameId}"
    const val SETTINGS = "settings"

    fun game(gameId: Long) = "game/$gameId"
    fun result(gameId: Long) = "result/$gameId"
}

private enum class BottomTab(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    HOME(Routes.HOME, "Sảnh chính", Icons.Filled.Home),
    HISTORY(Routes.HISTORY, "Lịch sử", Icons.Filled.History),
    TRAINING(Routes.TRAINING, "Luyện tập", Icons.Filled.School)
}

@Composable
fun CoTuongAppRoot(container: AppContainer) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val showBottomBar = currentRoute in setOf(Routes.HOME, Routes.HISTORY, Routes.TRAINING)

    Scaffold(
        containerColor = BgMain,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = BgCard) {
                    BottomTab.entries.forEach { tab ->
                        val selected = currentRoute == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = AccentEmerald,
                                selectedTextColor = AccentEmerald,
                                indicatorColor = AccentEmerald.copy(alpha = 0.16f),
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier
                .fillMaxSize()
                .padding(if (showBottomBar) innerPadding else PaddingValues(0.dp))
        ) {
            composable(Routes.HOME) {
                val vm: HomeViewModel = viewModel(
                    factory = HomeViewModel.Factory(
                        gameRepository = container.gameRepository,
                        settingsStore = container.settingsStore,
                        engineManager = container.engineManager
                    )
                )
                HomeScreen(
                    viewModel = vm,
                    onOpenSetup = { navController.navigate(Routes.SETUP) },
                    onResumeGame = { id -> navController.navigate(Routes.game(id)) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) }
                )
            }

            composable(Routes.HISTORY) {
                val vm: HistoryViewModel = viewModel(
                    factory = HistoryViewModel.Factory(container.gameRepository)
                )
                HistoryScreen(
                    viewModel = vm,
                    onResumeGame = { id -> navController.navigate(Routes.game(id)) },
                    onOpenResult = { id -> navController.navigate(Routes.result(id)) }
                )
            }

            composable(Routes.TRAINING) {
                TrainingScreen()
            }

            composable(Routes.SETUP) {
                val vm: SetupViewModel = viewModel(
                    factory = SetupViewModel.Factory(
                        gameRepository = container.gameRepository,
                        settingsStore = container.settingsStore,
                        engineManager = container.engineManager
                    )
                )
                SetupScreen(
                    viewModel = vm,
                    onNavigateBack = { navController.popBackStack() },
                    onGameCreated = { gameId ->
                        navController.navigate(Routes.game(gameId)) {
                            popUpTo(Routes.SETUP) { inclusive = true }
                        }
                    }
                )
            }

            composable(
                route = Routes.GAME,
                arguments = listOf(navArgument("gameId") { type = NavType.LongType })
            ) { entry ->
                val gameId = entry.arguments?.getLong("gameId") ?: 0L
                val vm: GameViewModel = viewModel(
                    key = "game_$gameId",
                    factory = GameViewModel.Factory(
                        gameId = gameId,
                        gameRepository = container.gameRepository,
                        engineManager = container.engineManager,
                        settingsStore = container.settingsStore,
                        soundEffectManager = container.soundEffectManager,
                        hapticFeedbackManager = container.hapticFeedbackManager
                    )
                )
                GameScreen(
                    viewModel = vm,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToResult = { id ->
                        navController.navigate(Routes.result(id)) {
                            popUpTo(Routes.HOME) { inclusive = false }
                        }
                    },
                    onNavigateToSettings = { navController.navigate(Routes.SETTINGS) }
                )
            }

            composable(
                route = Routes.RESULT,
                arguments = listOf(navArgument("gameId") { type = NavType.LongType })
            ) { entry ->
                val gameId = entry.arguments?.getLong("gameId") ?: 0L
                val vm: ResultViewModel = viewModel(
                    key = "result_$gameId",
                    factory = ResultViewModel.Factory(
                        gameId = gameId,
                        gameRepository = container.gameRepository
                    )
                )
                ResultScreen(
                    viewModel = vm,
                    onPlayNewGame = {
                        navController.navigate(Routes.SETUP) {
                            popUpTo(Routes.HOME) { inclusive = false }
                        }
                    },
                    onBackToHome = {
                        navController.popBackStack(Routes.HOME, inclusive = false)
                    }
                )
            }

            composable(Routes.SETTINGS) {
                val vm: SettingsViewModel = viewModel(
                    factory = SettingsViewModel.Factory(
                        settingsStore = container.settingsStore,
                        gameRepository = container.gameRepository,
                        engineManager = container.engineManager,
                        soundEffectManager = container.soundEffectManager
                    )
                )
                SettingsScreen(
                    viewModel = vm,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
