package com.pushuprpg.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
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
import com.pushuprpg.app.domain.ThemeMode
import com.pushuprpg.app.domain.wearing
import com.pushuprpg.app.domain.withWear
import com.pushuprpg.core.detect.ExerciseType
import com.pushuprpg.app.pose.PoseFrameSink
import com.pushuprpg.app.share.ShareCardData
import com.pushuprpg.app.pose.PoseLandmarkerSource
import com.pushuprpg.app.ui.components.ModelErrorBanner
import com.pushuprpg.app.ui.components.RunMusic
import com.pushuprpg.app.ui.screens.*
import com.pushuprpg.app.R
import com.pushuprpg.app.ui.theme.AlwaysDark
import com.pushuprpg.app.ui.theme.Palette
import com.pushuprpg.app.ui.theme.PushupRpgTheme
import com.pushuprpg.core.progression.Gift
import com.pushuprpg.core.progression.Gifts
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

    // Collected nullably so "not loaded yet" is distinguishable from "not onboarded". With a
    // non-null default the graph starts at onboarding for a returning user and then rebuilds when
    // the real value lands. Settings the same way, because the theme is one: a default there would
    // draw a light-mode user's first frames dark.
    val settingsState by container.settingsRepository.settings.collectAsState(initial = null)
    val settings = settingsState ?: AppSettings()
    val progressState by container.progressRepository.progress.collectAsState(initial = null)
    val progress = progressState ?: com.pushuprpg.app.domain.PlayerProgress()
    val granted by cameraGranted.collectAsState()
    val permanentlyDenied by permissionPermanentlyDenied.collectAsState()

    var poseError by remember { mutableStateOf<String?>(null) }

    // Frames are handed over through an atomic sink rather than a Compose state var: the consumer
    // is swapped from the composition but read from MediaPipe's own thread.
    val frameSink = remember { PoseFrameSink() }

    val poseSource = remember {
        PoseLandmarkerSource(
            context = context,
            onFrame = { frame ->
                frameSink.emit(frame)
                // Frames coming again are the model working again. One error used to leave the
                // banner on every screen for the rest of the session.
                if (poseError != null) poseError = null
            },
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
            //
            // The permission comes first only on the way through onboarding, whose tutorial needs
            // the camera. After it, the hub: the camera screens ask for it themselves (CameraGate),
            // and someone who turned the camera off could otherwise not reach their records, the
            // settings or the privacy policy — which says the other screens stay usable.
            val startDestination = remember {
                when {
                    !progress.introSeen -> Routes.ONBOARDING
                    !granted && !progress.onboarded -> Routes.PERMISSION
                    !progress.onboarded -> Routes.survival(tutorial = true)
                    else -> Routes.HOME
                }
            }

            NavHost(
                navController = navController,
                startDestination = startDestination,
            ) {
                composable(Routes.ONBOARDING) { entry ->
                    OnboardingScreen(
                        themeMode = settings.themeMode,
                        onToggleTheme = toggleTheme,
                        onContinue = {
                            // A second tap during the transition would push a second tutorial
                            // behind the first.
                            if (navController.isOnTop(entry)) {
                                scope.launch {
                                    container.progressRepository.update { it.copy(introSeen = true) }
                                }
                                // Onboarding is not finished here: the tutorial finishes it, run or
                                // skipped.
                                navController.navigate(
                                    if (granted) Routes.survival(tutorial = true) else Routes.PERMISSION
                                ) {
                                    popUpTo(Routes.ONBOARDING) { inclusive = true }
                                }
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

                composable(Routes.HOME) { entry ->
                    val vm: HomeViewModel = viewModel(factory = HomeViewModel.factory(container))
                    // Not collected in the background, so the totals are subscribed again on the way
                    // back, and the day read again with them: see HomeViewModel.followToday.
                    val state by vm.state.collectAsStateWithLifecycle()
                    LaunchedEffect(vm) {
                        entry.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.followToday() }
                    }
                    HomeScreen(
                        state = state,
                        themeMode = settings.themeMode,
                        onToggleTheme = toggleTheme,
                        // Straight in with the movement the button names; changing it is the link under it.
                        onPlayCat = { navController.navigateFrom(entry, Routes.survival(exercise = settings.exercise)) },
                        onChangeExercise = { navController.navigateFrom(entry, Routes.SURVIVAL_PICK) },
                        onRecords = { navController.navigateFrom(entry, Routes.RECORDS) },
                        onSettings = { navController.navigateFrom(entry, Routes.SETTINGS) },
                        onWardrobe = { navController.navigateFrom(entry, Routes.WARDROBE) },
                        lastExercise = settings.exercise,
                        catName = settings.catName,
                        catCoat = settings.catCoat,
                        catWear = settings.wearing(),
                    )
                }

                composable(Routes.WARDROBE) {
                    val factsFlow = remember { container.sessionRepository.facts() }
                    val facts by factsFlow.collectAsState(initial = null)
                    // Worked out from the runs and the longest streak, as the hub does, once both are in.
                    val found = remember(facts, progressState) {
                        val runs = facts
                        val stored = progressState
                        if (runs == null || stored == null) null else Gifts.earned(runs, stored.bestStreakDays)
                    }
                    // What was new as the screen opened stays marked for this visit; from now on it
                    // has been seen, and the hub stops counting it.
                    var fresh by remember { mutableStateOf<Set<Gift>?>(null) }
                    LaunchedEffect(found) {
                        val now = found ?: return@LaunchedEffect
                        val seen = container.progressRepository.current().giftsSeen
                        if (fresh == null) fresh = now.filterTo(mutableSetOf()) { it.name !in seen }
                        if (now.any { it.name !in seen }) {
                            container.progressRepository.update { p -> p.copy(giftsSeen = p.giftsSeen + now.map { it.name }) }
                        }
                    }
                    WardrobeScreen(
                        catName = settings.catName,
                        catCoat = settings.catCoat,
                        onCatChange = { name, coat ->
                            scope.launch {
                                container.settingsRepository.update { it.copy(catName = name, catCoat = coat) }
                            }
                        },
                        wearing = settings.wearing(),
                        found = found.orEmpty(),
                        fresh = fresh.orEmpty(),
                        onWear = { item ->
                            scope.launch {
                                container.settingsRepository.update { it.withWear(Gifts.wear(it.wearing(), item)) }
                            }
                        },
                        onTakeOff = { slot ->
                            scope.launch {
                                container.settingsRepository.update { it.withWear(Gifts.takeOff(it.wearing(), slot)) }
                            }
                        },
                    )
                }

                composable(Routes.SURVIVAL_PICK) { entry ->
                    ExercisePickScreen(
                        catName = settings.catName,
                        catCoat = settings.catCoat,
                        catWear = settings.wearing(),
                        onCatChange = { name, coat ->
                            scope.launch {
                                container.settingsRepository.update { it.copy(catName = name, catCoat = coat) }
                            }
                        },
                        initial = settings.exercise,
                        onStart = { picked ->
                            // Remembered as the last choice, which the hub's button starts. Not
                            // awaited: the run takes its movement from the route, so there is nothing
                            // for the write to race.
                            scope.launch {
                                container.settingsRepository.update { it.copy(exercise = picked) }
                            }
                            if (navController.isOnTop(entry)) {
                                navController.navigate(Routes.survival(exercise = picked)) {
                                    popUpTo(Routes.SURVIVAL_PICK) { inclusive = true }
                                }
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
                        viewModel(factory = SurvivalViewModel.factory(container, exercise, isTutorial))
                    val state by vm.state.collectAsState()
                    val best by vm.bestScore.collectAsState()
                    val cat by vm.catView.collectAsState()
                    val placement by vm.placement.collectAsState()
                    val setupSkeleton by vm.setupSkeleton.collectAsState()
                    val nearMisses by vm.nearMisses.collectAsState()
                    val growth by vm.growth.collectAsState()

                    DisposableEffect(vm) {
                        val consumer: (com.pushuprpg.core.pose.PoseFrame) -> Unit = vm::onPoseFrame
                        frameSink.attach(consumer)
                        onDispose { frameSink.detach(consumer) }
                    }

                    AlwaysDark {
                        CameraGate(
                            granted = granted,
                            permanentlyDenied = permanentlyDenied,
                            onRequestCameraPermission = onRequestCameraPermission,
                            onOpenAppSettings = onOpenAppSettings,
                        ) {
                            SurvivalScreen(
                                state = state,
                                bestScore = best,
                                cat = cat,
                                placement = placement,
                                setupSkeleton = setupSkeleton,
                                catName = settings.catName,
                                catCoat = settings.catCoat,
                                catWear = settings.wearing(),
                                poseSource = poseSource,
                                exercise = exercise,
                                isTutorial = isTutorial,
                                onRetry = vm::restart,
                                onShare = onShare,
                                modelFailed = poseError != null,
                                nearMisses = nearMisses,
                                growth = growth,
                                onSkip = {
                                    if (navController.isOnTop(entry)) {
                                        vm.skipTutorial()
                                        navController.navigate(Routes.HOME) {
                                            popUpTo(Routes.SURVIVAL) { inclusive = true }
                                        }
                                    }
                                },
                                onClose = {
                                    // Ends the session where it is and shows its ending; with nothing
                                    // played there is none, and it leaves as 홈으로 does.
                                    if (navController.isOnTop(entry) && !vm.endHere()) {
                                        vm.leave()
                                        navController.popBackStack(Routes.SURVIVAL, inclusive = true)
                                    }
                                },
                                onHome = {
                                    if (isTutorial) {
                                        // The done card's button, or back once the run has started. A
                                        // double tap on the card finished it twice and pushed a second
                                        // hub.
                                        if (navController.isOnTop(entry)) {
                                            vm.finishTutorial()
                                            // To the hub, whose biggest button is the cat again: the
                                            // tutorial was the mode itself, with one life.
                                            navController.navigate(Routes.HOME) {
                                                popUpTo(Routes.SURVIVAL) { inclusive = true }
                                            }
                                        }
                                    } else {
                                        // The ending's 홈으로, where the session is banked already;
                                        // the screen going any other way banks it on the way out.
                                        vm.leave()
                                        // By route, so a second tap cannot pop the hub along with it.
                                        navController.popBackStack(Routes.SURVIVAL, inclusive = true)
                                    }
                                },
                            )
                        }
                    }
                }

                composable(Routes.RECORDS) {
                    // Remembered, because collectAsState keys on the flow instance: building a new
                    // one each recomposition would cancel and restart both Room subscriptions every
                    // time a run is banked.
                    val recentFlow = remember { container.sessionRepository.recent(50) }
                    val totalsFlow = remember { container.sessionRepository.dailyTotals(91) }
                    val factsFlow = remember { container.sessionRepository.facts() }
                    val sessions by recentFlow.collectAsState(initial = emptyList())
                    val totals by totalsFlow.collectAsState(initial = emptyList())
                    val facts by factsFlow.collectAsState(initial = emptyList())
                    RecordsScreen(
                        progress = progress,
                        sessions = sessions,
                        dailyTotals = totals,
                        facts = facts,
                        exercise = settings.exercise,
                    )
                }

                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        settings = settings,
                        onChange = { transform ->
                            scope.launch { container.settingsRepository.update(transform) }
                        },
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
            }

            // Both delegates failing leaves a live preview with a counter frozen at zero. Saying
            // so is the difference between a broken app and a recoverable one.
            poseError?.let {
                // Over whichever screen is up, camera included, on a fixed dark scrim that runs
                // under the navigation bar with the line above it. The camera screens put their
                // placement line away while it shows; the two used to sit one over the other.
                AlwaysDark {
                    ModelErrorBanner(Modifier.align(Alignment.BottomCenter))
                }
            }
        }
    }
}

/**
 * The permission screen in place of a camera screen that has no camera to show.
 *
 * The graph asks about the permission once, when it picks where to start, and a restored back stack
 * skips even that. A permission given 이번만 lapses with the process, and the app then came back from
 * recents onto the run it was on: a black preview saying 화면 안으로 들어와 주세요 to someone
 * standing in front of it, and no way to be asked again. MainActivity reads the permission again on
 * resume, so turning it on in settings brings back the screen that was here.
 */
@Composable
private fun CameraGate(
    granted: Boolean,
    permanentlyDenied: Boolean,
    onRequestCameraPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (granted) {
        content()
    } else {
        // Its own surface: the camera screens are dark in either theme, and the root under them
        // is not.
        Box(Modifier.fillMaxSize().background(Palette.Bg1)) {
            PermissionScreen(
                permanentlyDenied = permanentlyDenied,
                onRequestPermission = onRequestCameraPermission,
                onOpenAppSettings = onOpenAppSettings,
            )
        }
    }
}

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

/**
 * Whether [entry] is still the screen on top, and so has not already left.
 *
 * Asked before a navigation that pops the screen it starts from. A second tap during the
 * transition, or two taps let through by an awaited write, arrived after the first had popped the
 * screen: its popUpTo then named a route no longer on the stack, and it pushed a second run
 * behind the first. Unlike waiting for RESUMED, a tap while the screen is still sliding in counts.
 */
private fun NavController.isOnTop(entry: NavBackStackEntry): Boolean =
    currentBackStackEntry?.id == entry.id

/**
 * Opens [route] over [entry], unless [entry] has already been left.
 *
 * For a screen that stays under the one it opens. A second tap during the transition, on the same
 * button or another, stacked a second screen over the first: back from a result went to a movement
 * picker left over from the hub.
 */
private fun NavController.navigateFrom(entry: NavBackStackEntry, route: String) {
    if (isOnTop(entry)) navigate(route)
}

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
