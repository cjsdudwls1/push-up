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
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.net.toUri
import com.pushuprpg.app.AppContainer
import com.pushuprpg.app.domain.AppSettings
import com.pushuprpg.app.domain.FreeTier
import com.pushuprpg.app.domain.capacityOf
import com.pushuprpg.core.detect.ExerciseType
import com.pushuprpg.app.pose.PoseFrameSink
import com.pushuprpg.app.share.ShareCardData
import com.pushuprpg.app.telemetry.Event
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
import com.pushuprpg.core.progression.Rank
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
    onShare: (ShareCardData) -> Unit,
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
                    !progress.classChosen -> Routes.ONBOARDING
                    !granted -> Routes.PERMISSION
                    !progress.onboarded -> Routes.survival(tutorial = true)
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
                        capacity = progress.capacityOf(ExerciseType.PUSHUP),
                        onPick = { playerClass: PlayerClass ->
                            scope.launch {
                                container.progressRepository.update {
                                    it.copy(playerClass = playerClass, classChosen = true)
                                }
                                container.telemetry.log(Event.ClassPicked(playerClass.name))
                            }
                            // Onboarding is not finished here: the tutorial run is what completes
                            // it, because that run is also the calibration set every dungeon is
                            // sized from. Marking it done earlier would let someone reach a dungeon
                            // with no measured capacity at all.
                            navController.navigate(
                                if (granted) Routes.survival(tutorial = true) else Routes.PERMISSION
                            ) {
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
                            val next = if (progress.onboarded) Routes.HOME else Routes.survival(tutorial = true)
                            navController.navigate(next) {
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
                        onRequestPaywall = {
                            container.telemetry.log(Event.PaywallShown("home"))
                            navController.navigate(Routes.PAYWALL)
                        },
                        onDungeonSelect = { navController.navigate(Routes.DUNGEON_SELECT) },
                        onSurvival = { navController.navigate(Routes.survival()) },
                        onRecords = { navController.navigate(Routes.RECORDS) },
                        onSettings = { navController.navigate(Routes.SETTINGS) },
                    )
                }

                composable(Routes.DUNGEON_SELECT) {
                    DungeonSelectScreen(
                        highestCleared = progress.highestDungeonCleared,
                        capacity = progress.capacityOf(ExerciseType.PUSHUP),
                        difficulty = settings.difficulty,
                        entitlement = entitlement,
                        onDifficultyChange = { difficulty ->
                            scope.launch {
                                container.settingsRepository.update { it.copy(difficulty = difficulty) }
                            }
                        },
                        onStart = { navController.navigate(Routes.battle(it)) },
                        onRequestPaywall = {
                            container.telemetry.log(Event.PaywallShown("dungeon_select"))
                            navController.navigate(Routes.PAYWALL)
                        },
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

                    val detected by vm.detectedExercise.collectAsState()

                    BattleScreen(
                        state = state,
                        playerClass = progress.playerClass,
                        poseSource = poseSource,
                        sessionBestDepth = vm.currentSessionBestDepth(),
                        gaugeOnRight = settings.gaugeOnRight,
                        showGaugeNumber = settings.showGaugeNumber,
                        audioOnly = settings.audioOnly,
                        detectedExercise = detected,
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
                            onShare = {
                                onShare(
                                    ShareCardData.Dungeon(
                                        dungeonName = Dungeons.byIndex(dungeonIndex)?.korean.orEmpty(),
                                        cleared = outcome.cleared,
                                        reps = outcome.reps,
                                        maxCombo = outcome.maxCombo,
                                        seconds = (outcome.durationMs / 1000).toInt(),
                                        rankKorean = Rank.forLifetimeReps(progress.lifetimeReps).korean,
                                        lifetimeReps = progress.lifetimeReps,
                                    )
                                )
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

                composable(
                    route = Routes.SURVIVAL,
                    arguments = listOf(navArgument(Routes.ARG_TUTORIAL) { type = NavType.BoolType }),
                ) { entry ->
                    val isTutorial = entry.arguments?.getBoolean(Routes.ARG_TUTORIAL) ?: false
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
                        isTutorial = isTutorial,
                        onRetry = vm::restart,
                        onShare = onShare,
                        onHome = {
                            if (isTutorial) {
                                vm.finishTutorial()
                                navController.navigate(Routes.HOME) {
                                    popUpTo(Routes.SURVIVAL) { inclusive = true }
                                }
                            } else {
                                navController.popBackStack()
                            }
                        },
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
                                // Under auto-detection there is no single "current" exercise to
                                // reset, and a button that silently cleared only the one showing in
                                // the picker would leave the range that is actually wrong in place.
                                val targets: List<ExerciseType> = if (settings.autoExercise) {
                                    ExerciseType.entries
                                } else {
                                    listOf(settings.exercise)
                                }
                                targets.forEach { type ->
                                    container.progressRepository.saveCalibrationProfile(
                                        type,
                                        com.pushuprpg.core.detect.UserProfile.empty(),
                                    )
                                }
                            }
                        },
                        onOpenPrivacy = { openUrl(context, context.getString(R.string.privacy_policy_url)) },
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

/**
 * Opens a link in whatever the device uses for the web.
 *
 * Failure is surfaced rather than swallowed: the one link this app has is its privacy policy, and a
 * settings row that does nothing when tapped looks like the policy does not exist.
 */
private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, context.getString(R.string.browser_unavailable), Toast.LENGTH_SHORT)
            .show()
    }
}
