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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.net.toUri
import com.pushuprpg.app.AppContainer
import com.pushuprpg.app.BuildConfig
import com.pushuprpg.app.trace.TraceFiles
import com.pushuprpg.app.domain.AppSettings
import com.pushuprpg.app.domain.FreeTier
import com.pushuprpg.app.domain.ThemeMode
import com.pushuprpg.app.domain.capacityOf
import com.pushuprpg.core.detect.ExerciseType
import com.pushuprpg.app.pose.PoseFrameSink
import com.pushuprpg.app.share.ShareCardData
import com.pushuprpg.app.telemetry.Event
import com.pushuprpg.app.pose.PoseLandmarkerSource
import com.pushuprpg.app.ui.battle.BattleScreen
import com.pushuprpg.app.ui.components.RunMusic
import com.pushuprpg.app.ui.battle.BattleViewModel
import com.pushuprpg.app.ui.result.ResultScreen
import com.pushuprpg.app.ui.screens.*
import androidx.compose.material3.Text
import com.pushuprpg.app.R
import com.pushuprpg.app.ui.theme.AlwaysDark
import com.pushuprpg.app.ui.theme.Palette
import com.pushuprpg.app.ui.theme.Type
import com.pushuprpg.app.ui.theme.PushupRpgTheme
import com.pushuprpg.core.game.Dungeons
import com.pushuprpg.core.game.PlayerClass
import com.pushuprpg.core.progression.Rank
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

    // Collected nullably so "not loaded yet" is distinguishable from "not onboarded". With a
    // non-null default the graph starts at onboarding for a returning user and then rebuilds when
    // the real value lands. Settings the same way, because the theme is one: a default there would
    // draw a light-mode user's first frames dark.
    val settingsState by container.settingsRepository.settings.collectAsState(initial = null)
    val settings = settingsState ?: AppSettings()
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

    val darkTheme = settings.themeMode == ThemeMode.DARK
    val backStackEntry by navController.currentBackStackEntryAsState()
    SystemBars(darkSurface = darkTheme || backStackEntry?.destination?.route in Routes.ALWAYS_DARK)

    val toggleTheme: () -> Unit = {
        scope.launch {
            container.settingsRepository.update { it.copy(themeMode = it.themeMode.toggled()) }
        }
    }
    val changeClass: () -> Unit = {
        navController.navigate(Routes.CLASS_CHANGE) { launchSingleTop = true }
    }

    PushupRpgTheme(
        darkTheme = darkTheme,
        colourBlindSafe = settings.colourBlindSafe,
        reduceMotion = settings.reduceMotion,
    ) {
        Box(Modifier.fillMaxSize().background(Palette.Bg1)) {
            // Nothing is drawn until persisted progress and settings have landed; see the nullable
            // collects above.
            if (progressState == null || settingsState == null) return@Box

            // The music plays from the moment the app opens, not only in a run, and follows the
            // setting as it changes — so picking a track in settings is hearing it. It is placed
            // after the settings have loaded so someone who turned it off never hears a first bar.
            RunMusic(track = settings.music, player = container.music)

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
                    OnboardingScreen(
                        themeMode = settings.themeMode,
                        onToggleTheme = toggleTheme,
                        onContinue = { navController.navigate(Routes.CLASS_PICK) },
                    )
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

                composable(Routes.CLASS_CHANGE) {
                    ClassPickScreen(
                        capacity = progress.capacityOf(ExerciseType.PUSHUP),
                        current = progress.playerClass,
                        onPick = { playerClass: PlayerClass ->
                            val from = progress.playerClass
                            if (playerClass != from) {
                                scope.launch {
                                    container.progressRepository.update {
                                        it.copy(playerClass = playerClass)
                                    }
                                    container.telemetry.log(
                                        Event.ClassChanged(from = from.name, to = playerClass.name)
                                    )
                                }
                            }
                            // By route rather than a bare pop, so a second tap that lands before
                            // the screen has gone cannot pop the hub along with it.
                            navController.popBackStack(Routes.CLASS_CHANGE, inclusive = true)
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
                        themeMode = settings.themeMode,
                        onToggleTheme = toggleTheme,
                        onChangeClass = changeClass,
                        onStartDungeon = { navController.navigate(Routes.exercisePick(it)) },
                        onRequestPaywall = {
                            container.telemetry.log(Event.PaywallShown("home"))
                            navController.navigate(Routes.PAYWALL)
                        },
                        onDungeonSelect = { navController.navigate(Routes.DUNGEON_SELECT) },
                        onSurvival = { navController.navigate(Routes.SURVIVAL_PICK) },
                        onRecords = { navController.navigate(Routes.RECORDS) },
                        onSettings = { navController.navigate(Routes.SETTINGS) },
                    )
                }

                composable(Routes.DUNGEON_SELECT) {
                    DungeonSelectScreen(
                        highestCleared = progress.highestDungeonCleared,
                        exercise = settings.exercise,
                        capacity = progress.capacityOf(settings.exercise),
                        difficulty = settings.difficulty,
                        playerClass = progress.playerClass,
                        entitlement = entitlement,
                        onDifficultyChange = { difficulty ->
                            scope.launch {
                                container.settingsRepository.update { it.copy(difficulty = difficulty) }
                            }
                        },
                        onStart = { navController.navigate(Routes.exercisePick(it)) },
                        onRequestPaywall = {
                            container.telemetry.log(Event.PaywallShown("dungeon_select"))
                            navController.navigate(Routes.PAYWALL)
                        },
                    )
                }

                composable(
                    route = Routes.EXERCISE_PICK,
                    arguments = listOf(navArgument(Routes.ARG_DUNGEON_INDEX) { type = NavType.IntType }),
                ) { entry ->
                    val dungeonIndex = entry.arguments?.getInt(Routes.ARG_DUNGEON_INDEX) ?: 1
                    val dungeon = Dungeons.byIndex(dungeonIndex)
                    ExercisePickScreen(
                        dungeon = dungeon,
                        initial = settings.exercise,
                        difficulty = settings.difficulty,
                        playerClass = progress.playerClass,
                        onStart = { picked ->
                            scope.launch {
                                // Persisted before navigating, and awaited, because BattleViewModel
                                // reads the choice out of settings when it starts. Firing the write
                                // and navigating in parallel would race, and losing that race means
                                // a run counted with the previous movement's detector.
                                container.settingsRepository.update { it.copy(exercise = picked) }
                                navController.navigate(Routes.battle(dungeonIndex)) {
                                    popUpTo(Routes.EXERCISE_PICK) { inclusive = true }
                                }
                            }
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

                    AlwaysDark {
                        BattleScreen(
                            state = state,
                            playerClass = progress.playerClass,
                            poseSource = poseSource,
                            sessionBestDepth = vm.currentSessionBestDepth(),
                            gaugeOnRight = settings.gaugeOnRight,
                            showGaugeNumber = settings.showGaugeNumber,
                            audioOnly = settings.audioOnly,
                            onSwitchExercise = vm::switchExercise,
                            onQuit = {
                                lastOutcome = vm.quit()
                                lastLevelsGained = vm.levelsGained.value
                                navController.navigate(Routes.result(dungeonIndex)) {
                                    popUpTo(Routes.BATTLE) { inclusive = true }
                                }
                            },
                        )
                    }
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
                        // Auto-advance: after a clear, rest, then the next dungeon with the same
                        // movement. Only where the next one can be played at all — the same gate as
                        // the button — and never after a loss, which gets a retry, not a harder floor.
                        val next = dungeonIndex + 1
                        val canAutoNext = settings.autoNextRestSeconds > 0 && outcome.cleared &&
                            dungeonIndex < Dungeons.ALL.size && FreeTier.canPlayDungeon(next, entitlement)
                        var autoNextCancelled by rememberSaveable { mutableStateOf(false) }
                        var restLeft by rememberSaveable { mutableIntStateOf(settings.autoNextRestSeconds) }
                        val startNextNow: () -> Unit = {
                            scope.launch {
                                // The movement the run ended on, which is the one the user was just
                                // doing, and awaited for the same reason as on the picker.
                                val exercise = outcome.segments.lastOrNull()?.exercise ?: settings.exercise
                                container.settingsRepository.update { it.copy(exercise = exercise) }
                                navController.navigate(Routes.battle(next)) {
                                    popUpTo(Routes.RESULT) { inclusive = true }
                                }
                            }
                        }
                        if (canAutoNext && !autoNextCancelled) {
                            LaunchedEffect(Unit) {
                                while (restLeft > 0) {
                                    delay(1_000)
                                    restLeft--
                                    // Heard from across the room, where the rest is taken.
                                    if (restLeft == 10) container.voice.say(context.getString(R.string.voice_rest_ten))
                                }
                                container.voice.say(context.getString(R.string.voice_rest_go))
                                startNextNow()
                            }
                        }
                        AlwaysDark {
                            ResultScreen(
                                restLeftSeconds = restLeft.takeIf { canAutoNext && !autoNextCancelled },
                                nextDungeonName = Dungeons.byIndex(next)?.korean.orEmpty(),
                                onStartNextNow = startNextNow,
                                onCancelAutoNext = { autoNextCancelled = true },
                                outcome = outcome,
                                playerClass = progress.playerClass,
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
                                        Routes.exercisePick(next)
                                    } else {
                                        Routes.PAYWALL
                                    }
                                    navController.navigate(route) {
                                        popUpTo(Routes.RESULT) { inclusive = true }
                                    }
                                },
                                onRetry = {
                                    navController.navigate(Routes.exercisePick(dungeonIndex)) {
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
                }

                composable(Routes.SURVIVAL_PICK) {
                    ExercisePickScreen(
                        dungeon = null,
                        survival = true,
                        catName = settings.catName,
                        catCoat = settings.catCoat,
                        onCatChange = { name, coat ->
                            scope.launch {
                                container.settingsRepository.update { it.copy(catName = name, catCoat = coat) }
                            }
                        },
                        initial = settings.exercise,
                        onStart = { picked ->
                            // Remembered as the last choice, for this picker and the dungeon one.
                            // Not awaited: unlike a battle, the survival run takes its movement from
                            // the route, so there is nothing for the write to race.
                            scope.launch {
                                container.settingsRepository.update { it.copy(exercise = picked) }
                            }
                            navController.navigate(Routes.survival(exercise = picked)) {
                                popUpTo(Routes.SURVIVAL_PICK) { inclusive = true }
                            }
                        },
                    )
                }

                composable(
                    route = Routes.SURVIVAL,
                    arguments = listOf(
                        navArgument(Routes.ARG_TUTORIAL) { type = NavType.BoolType },
                        navArgument(Routes.ARG_EXERCISE) { type = NavType.StringType },
                    ),
                ) { entry ->
                    val isTutorial = entry.arguments?.getBoolean(Routes.ARG_TUTORIAL) ?: false
                    val exerciseName = entry.arguments?.getString(Routes.ARG_EXERCISE)
                    val exercise = ExerciseType.entries.firstOrNull { it.name == exerciseName }
                        ?: ExerciseType.PUSHUP
                    val vm: SurvivalViewModel =
                        viewModel(factory = SurvivalViewModel.factory(container, exercise))
                    val state by vm.state.collectAsState()
                    val best by vm.bestScore.collectAsState()
                    val cat by vm.catView.collectAsState()
                    val placement by vm.placement.collectAsState()
                    val setupSkeleton by vm.setupSkeleton.collectAsState()

                    DisposableEffect(vm) {
                        val consumer: (com.pushuprpg.core.pose.PoseFrame) -> Unit = vm::onPoseFrame
                        frameSink.attach(consumer)
                        onDispose { frameSink.detach(consumer) }
                    }

                    AlwaysDark {
                        SurvivalScreen(
                            state = state,
                            bestScore = best,
                            cat = cat,
                            placement = placement,
                            setupSkeleton = setupSkeleton,
                            catName = settings.catName,
                            catCoat = settings.catCoat,
                            poseSource = poseSource,
                            exercise = exercise,
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
                                // Every movement's range, not just the last one played. The button
                                // lives on the settings screen, which no longer names an exercise,
                                // so clearing only one would be clearing one the user cannot see —
                                // and a range relearns itself within a few reps anyway.
                                ExerciseType.entries.forEach { type ->
                                    container.progressRepository.saveCalibrationProfile(
                                        type,
                                        com.pushuprpg.core.detect.UserProfile.empty(),
                                    )
                                }
                                // It used to finish in silence, and a reset nobody can see looks
                                // exactly like a button that does nothing.
                                toast(context, R.string.settings_recalibrate_done)
                            }
                        },
                        onChangeClass = changeClass,
                        onOpenPrivacy = { openUrl(context, context.getString(R.string.privacy_policy_url)) },
                        onOpenTerms = { openUrl(context, context.getString(R.string.terms_url)) },
                        onPreviewHaptic = container.audio::previewHaptic,
                        traceTools = BuildConfig.DEBUG,
                        onSendTrace = {
                            scope.launch {
                                val trace = container.traces.latest()
                                val uri = trace?.let { TraceFiles.write(context, it) }
                                when {
                                    trace == null -> toast(context, R.string.trace_none)
                                    uri == null -> toast(context, R.string.trace_write_failed)
                                    else -> try {
                                        context.startActivity(TraceFiles.chooser(context, uri))
                                    } catch (e: ActivityNotFoundException) {
                                        toast(context, R.string.share_unavailable)
                                    }
                                }
                            }
                        },
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
                // Over whichever screen is up, camera included, on a fixed dark scrim.
                AlwaysDark {
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
 * Status and navigation bar icons that match the surface under them.
 *
 * The bars are drawn edge to edge, so only their icons (and the navigation scrim below API 29) are
 * ours to choose. Left at [enableEdgeToEdge]'s defaults they follow the *system* theme, which put
 * dark icons over this app's dark screens for anyone whose phone is in light mode — and, now that
 * the menus can be light, would do the reverse as well.
 */
@Composable
private fun SystemBars(darkSurface: Boolean) {
    val activity = LocalContext.current as? ComponentActivity ?: return
    DisposableEffect(activity, darkSurface) {
        activity.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ) { darkSurface },
            navigationBarStyle = SystemBarStyle.auto(LIGHT_NAV_SCRIM, DARK_NAV_SCRIM) { darkSurface },
        )
        onDispose {}
    }
}

/** [enableEdgeToEdge]'s own default scrims, which it keeps private. */
private val LIGHT_NAV_SCRIM = android.graphics.Color.argb(0xE6, 0xFF, 0xFF, 0xFF)
private val DARK_NAV_SCRIM = android.graphics.Color.argb(0x80, 0x1B, 0x1B, 0x1B)

private fun toast(context: Context, message: Int) {
    Toast.makeText(context, context.getString(message), Toast.LENGTH_SHORT).show()
}

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
