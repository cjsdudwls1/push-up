package com.pushuprpg.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pushuprpg.app.AppContainer
import com.pushuprpg.app.domain.AppSettings
import com.pushuprpg.app.domain.FreeTier
import com.pushuprpg.app.pose.PoseFrameSink
import com.pushuprpg.app.pose.PoseLandmarkerSource
import com.pushuprpg.app.ui.battle.BattleScreen
import com.pushuprpg.app.ui.battle.BattleViewModel
import com.pushuprpg.app.ui.result.ResultScreen
import com.pushuprpg.app.ui.screens.*
import androidx.compose.material3.Text
import com.pushuprpg.app.R
import com.pushuprpg.app.ui.theme.Palette
import com.pushuprpg.app.ui.theme.Type
import com.pushuprpg.app.ui.theme.PushupRpgTheme
import com.pushuprpg.core.game.Dungeons
import com.pushuprpg.core.game.PlayerClass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

/**
 * The navigation graph.
 *
 * The pose pipeline is created once here and shared by every screen that needs the camera, rather
 * than per-screen: constructing a landmarker takes long enough to be visible, and doing it on each
 * navigation would put a stutter right at the moment the user is getting into position.
 */
@Composable
fun PushupRpgApp(
    container: AppContainer,
    cameraGranted: MutableStateFlow<Boolean>,
    permissionPermanentlyDenied: MutableStateFlow<Boolean>,
    onRequestCameraPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onShare: (Int) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()

    val settings by container.settingsRepository.settings.collectAsState(initial = AppSettings())
    // Collected nullably so "not loaded yet" is distinguishable from "not onboarded". With a
    // non-null default the graph starts at onboarding for a returning user and then rebuilds when
    // the real value lands.
    val progressState by container.progressRepository.progress.collectAsState(initial = null)
    val progress = progressState ?: com.pushuprpg.app.domain.PlayerProgress()
    val entitlement by container.entitlementRepository.entitlement.collectAsState(
        initial = com.pushuprpg.app.domain.Entitlement()
    )
    val granted by cameraGranted.collectAsState()
    val permanentlyDenied by permissionPermanentlyDenied.collectAsState()

    var poseError by remember { mutableStateOf<String?>(null) }

    // Frames are handed over through an atomic sink rather than a Compose state var: the consumer
    // is swapped from the composition but read from MediaPipe's own thread.
    val frameSink = remember { PoseFrameSink() }

    val poseSource = remember {
        PoseLandmarkerSource(
            context = context,
            onFrame = frameSink::emit,
            onError = { poseError = it },
        )
    }

    // createFromOptions loads a 5.8 MB model and initialises a GPU delegate — and on failure pays
    // for the whole thing twice on the way to the CPU fallback. On the main thread that is a frozen
    // UI at exactly the moment the user is getting into position, and an ANR on a slow device.
    LaunchedEffect(granted) {
        if (granted) withContext(Dispatchers.Default) { poseSource.setup() }
    }
    DisposableEffect(Unit) {
        onDispose { poseSource.close() }
    }

    PushupRpgTheme(
        colourBlindSafe = settings.colourBlindSafe,
        reduceMotion = settings.reduceMotion,
    ) {
        Box(Modifier.fillMaxSize().background(Palette.Bg1)) {
            // Nothing is drawn until persisted progress has landed; see the nullable collect above.
            if (progressState == null) return@Box

            // NavHost memoises its graph on startDestination, and a changed one wipes the whole
            // back stack. It is therefore decided exactly once.
            val startDestination = remember {
                when {
                    !progress.onboarded -> Routes.ONBOARDING
                    !granted -> Routes.PERMISSION
                    else -> Routes.HOME
                }
            }

            NavHost(
                navController = navController,
                startDestination = startDestination,
            ) {
                composable(Routes.ONBOARDING) {
                    OnboardingScreen(onContinue = { navController.navigate(Routes.CLASS_PICK) })
                }

                composable(Routes.CLASS_PICK) {
                    ClassPickScreen(
                        capacity = progress.capacityPushup,
                        onPick = { playerClass: PlayerClass ->
                            scope.launch {
                                container.progressRepository.update {
                                    it.copy(playerClass = playerClass, onboarded = true)
                                }
                            }
                            navController.navigate(if (granted) Routes.HOME else Routes.PERMISSION) {
                                popUpTo(Routes.ONBOARDING) { inclusive = true }
                            }
                        },
                    )
                }

                composable(Routes.PERMISSION) {
                    // Navigating away the instant permission lands avoids leaving the user staring
                    // at a rationale for something they have already agreed to.
                    LaunchedEffect(granted) {
                        if (granted) {
                            navController.navigate(Routes.HOME) {
                                popUpTo(Routes.PERMISSION) { inclusive = true }
                            }
                        }
                    }
                    PermissionScreen(
                        permanentlyDenied = permanentlyDenied,
                        onRequestPermission = onRequestCameraPermission,
                        onOpenAppSettings = onOpenAppSettings,
                    )
                }

                composable(Routes.HOME) {
                    val vm: HomeViewModel = viewModel(factory = HomeViewModel.factory(container))
                    val state by vm.state.collectAsState()
                    HomeScreen(
                        state = state,
                        onStartDungeon = { navController.navigate(Routes.battle(it)) },
                        onRequestPaywall = { navController.navigate(Routes.PAYWALL) },
                        onDungeonSelect = { navController.navigate(Routes.DUNGEON_SELECT) },
                        onSurvival = { navController.navigate(Routes.SURVIVAL) },
                        onRecords = { navController.navigate(Routes.RECORDS) },
                        onSettings = { navController.navigate(Routes.SETTINGS) },
                    )
                }

                composable(Routes.DUNGEON_SELECT) {
                    DungeonSelectScreen(
                        highestCleared = progress.highestDungeonCleared,
                        capacity = progress.capacityPushup,
                        difficulty = settings.difficulty,
                        entitlement = entitlement,
                        onDifficultyChange = { difficulty ->
                            scope.launch {
                                container.settingsRepository.update { it.copy(difficulty = difficulty) }
                            }
                        },
                        onStart = { navController.navigate(Routes.battle(it)) },
                        onRequestPaywall = { navController.navigate(Routes.PAYWALL) },
                    )
                }

                composable(
                    route = Routes.BATTLE,
                    arguments = listOf(navArgument(Routes.ARG_DUNGEON_INDEX) { type = NavType.IntType }),
                ) { entry ->
                    val dungeonIndex = entry.arguments?.getInt(Routes.ARG_DUNGEON_INDEX) ?: 1
                    val vm: BattleViewModel = viewModel(factory = BattleViewModel.factory(container))
                    val state by vm.state.collectAsState()

                    LaunchedEffect(dungeonIndex) { vm.start(dungeonIndex) }
                    DisposableEffect(vm) {
                        val consumer: (com.pushuprpg.core.pose.PoseFrame) -> Unit = vm::onPoseFrame
                        frameSink.attach(consumer)
                        onDispose { frameSink.detach(consumer) }
                    }

                    LaunchedEffect(state.outcome) {
                        state.outcome?.let { outcome ->
                            lastOutcome = outcome
                            lastLevelsGained = vm.levelsGained.value
                            navController.navigate(Routes.result(dungeonIndex)) {
                                popUpTo(Routes.BATTLE) { inclusive = true }
                            }
                        }
                    }

                    BattleScreen(
                        state = state,
                        poseSource = poseSource,
                        sessionBestDepth = vm.currentSessionBestDepth(),
                        gaugeOnRight = settings.gaugeOnRight,
                        showGaugeNumber = settings.showGaugeNumber,
                        audioOnly = settings.audioOnly,
                        onQuit = {
                            lastOutcome = vm.quit()
                            lastLevelsGained = vm.levelsGained.value
                            navController.navigate(Routes.result(dungeonIndex)) {
                                popUpTo(Routes.BATTLE) { inclusive = true }
                            }
                        },
                    )
                }

                composable(
                    route = Routes.RESULT,
                    arguments = listOf(navArgument(Routes.ARG_DUNGEON_INDEX) { type = NavType.IntType }),
                ) { entry ->
                    val dungeonIndex = entry.arguments?.getInt(Routes.ARG_DUNGEON_INDEX) ?: 1
                    val outcome = lastOutcome
                    if (outcome == null) {
                        LaunchedEffect(Unit) {
                            navController.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } }
                        }
                    } else {
                        ResultScreen(
                            outcome = outcome,
                            dungeonName = Dungeons.byIndex(dungeonIndex)?.korean.orEmpty(),
                            lifetimeReps = progress.lifetimeReps,
                            level = progress.level,
                            levelsGained = lastLevelsGained,
                            hasNextDungeon = dungeonIndex < Dungeons.ALL.size,
                            onNextDungeon = {
                                val next = dungeonIndex + 1
                                // The same gate the dungeon list applies; without it the clear
                                // screen was a way past the paywall.
                                val route = if (FreeTier.canPlayDungeon(next, entitlement)) {
                                    Routes.battle(next)
                                } else {
                                    Routes.PAYWALL
                                }
                                navController.navigate(route) {
                                    popUpTo(Routes.RESULT) { inclusive = true }
                                }
                            },
                            onRetry = {
                                navController.navigate(Routes.battle(dungeonIndex)) {
                                    popUpTo(Routes.RESULT) { inclusive = true }
                                }
                            },
                            onRecords = { navController.navigate(Routes.RECORDS) },
                            onHome = {
                                navController.navigate(Routes.HOME) {
                                    popUpTo(Routes.HOME) { inclusive = true }
                                }
                            },
                        )
                    }
                }

                composable(Routes.SURVIVAL) {
                    val vm: SurvivalViewModel = viewModel(factory = SurvivalViewModel.factory(container))
                    val state by vm.state.collectAsState()
                    val best by vm.bestScore.collectAsState()

                    DisposableEffect(vm) {
                        val consumer: (com.pushuprpg.core.pose.PoseFrame) -> Unit = vm::onPoseFrame
                        frameSink.attach(consumer)
                        onDispose { frameSink.detach(consumer) }
                    }

                    SurvivalScreen(
                        state = state,
                        bestScore = best,
                        poseSource = poseSource,
                        onRetry = vm::restart,
                        onShare = onShare,
                        onHome = { navController.popBackStack() },
                    )
                }

                composable(Routes.RECORDS) {
                    // Remembered, because collectAsState keys on the flow instance: building a new
                    // one each recomposition would cancel and restart both Room subscriptions every
                    // time a run is banked.
                    val recentFlow = remember { container.sessionRepository.recent(50) }
                    val totalsFlow = remember { container.sessionRepository.dailyTotals(91) }
                    val sessions by recentFlow.collectAsState(initial = emptyList())
                    val totals by totalsFlow.collectAsState(initial = emptyList())
                    RecordsScreen(
                        progress = progress,
                        sessions = sessions,
                        dailyTotals = totals,
                    )
                }

                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        settings = settings,
                        onChange = { transform ->
                            scope.launch { container.settingsRepository.update(transform) }
                        },
                        onRecalibrate = {
                            scope.launch {
                                container.progressRepository.saveCalibrationProfile(
                                    settings.exercise,
                                    com.pushuprpg.core.detect.UserProfile.empty(),
                                )
                            }
                        },
                        onOpenPrivacy = { /* wired to the hosted policy URL before release */ },
                    )
                }

                composable(Routes.PAYWALL) {
                    val plans by container.billing.plans.collectAsState()
                    val activity = context as? android.app.Activity
                    PaywallScreen(
                        plans = plans,
                        lifetimeReps = progress.lifetimeReps,
                        streakDays = progress.streakDays,
                        level = progress.level,
                        onPurchase = { plan ->
                            activity?.let { container.billing.launchPurchaseFlow(it, plan) }
                        },
                        onRestore = { scope.launch { container.entitlementRepository.refresh() } },
                        onDismiss = { navController.popBackStack() },
                    )
                }
            }

            // Both delegates failing leaves a live preview with a counter frozen at zero. Saying
            // so is the difference between a broken app and a recoverable one.
            poseError?.let {
                Text(
                    text = stringResource(R.string.error_model_load),
                    style = Type.bodyM,
                    color = Palette.TextPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Palette.ScrimPanelHigh)
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                )
            }
        }
    }
}

/**
 * The finished run, handed from the battle destination to the result destination.
 *
 * A module-level holder rather than a navigation argument: [com.pushuprpg.core.run.Outcome] is a
 * structured value and serialising it through a route string would be a lot of ceremony for a
 * hand-off that lives for exactly one screen transition.
 *
 * It does not survive process death, and that is an accepted trade rather than an oversight: the
 * run is already written to the database before this screen opens, so the worst case is the user
 * returning to a killed app and landing on the hub instead of on a summary — with every rep, every
 * point of XP and the streak already banked. Losing a screen is acceptable; losing the work is not.
 */
internal var lastOutcome: com.pushuprpg.core.run.Outcome? = null

/** Travels with [lastOutcome]; the battle entry is popped before the result screen composes. */
internal var lastLevelsGained: Int = 0
