package com.pushuprpg.app.store

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.pushuprpg.app.domain.CatCoat
import com.pushuprpg.app.domain.PlayerProgress
import com.pushuprpg.app.pose.PoseLandmarkerSource
import com.pushuprpg.app.ui.components.SpaceBackdrop
import com.pushuprpg.app.ui.screens.HomeScreen
import com.pushuprpg.app.ui.screens.HomeUiState
import com.pushuprpg.app.ui.screens.SurvivalScreen
import com.pushuprpg.app.ui.screens.WardrobeScreen
import com.pushuprpg.app.ui.theme.AlwaysDark
import com.pushuprpg.app.ui.theme.Palette
import com.pushuprpg.app.ui.theme.PushupRpgTheme
import com.pushuprpg.core.detect.ExerciseType
import com.pushuprpg.core.progression.CatItem
import com.pushuprpg.core.progression.Gift
import com.pushuprpg.core.progression.SetBests
import com.pushuprpg.core.progression.SetOutcome
import com.pushuprpg.core.survival.CatLine
import com.pushuprpg.core.survival.CatMood
import com.pushuprpg.core.survival.CatPhase
import com.pushuprpg.core.survival.CatSessionState
import com.pushuprpg.core.survival.CatSpeech
import com.pushuprpg.core.survival.CatView
import com.pushuprpg.core.survival.SetResult
import com.pushuprpg.core.survival.SurvivalState
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The Play listing's screenshots, drawn from the real screens on the JVM.
 *
 * Not a test: it asserts nothing and is skipped unless `pushup.storeShots` names an output folder
 * (scripts/store-screenshots.sh sets it). Each screen is fed a plausible state by hand — a set
 * under way, a rest, the hub, 꾸미기 — and drawn at 360x720 dp, three pixels to a dp; the script then
 * puts a caption over each (tools/store_screenshots.py).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h720dp-xxhdpi")
class StoreScreenshots {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val out: File? = System.getProperty("pushup.storeShots")?.takeIf { it.isNotBlank() }?.let(::File)

    private val wear = setOf(CatItem.CROWN, CatItem.BOW_TIE, CatItem.YARN)

    private fun shoot(name: String, content: @Composable () -> Unit) {
        val dir = out
        assumeTrue("set -Dpushup.storeShots=<dir> to draw the store screenshots", dir != null)
        // The run animates every frame and is never idle: the clock moves only when told to.
        compose.mainClock.autoAdvance = false
        compose.setContent { content() }
        compose.mainClock.advanceTimeBy(2_000)
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        dir!!.mkdirs()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Composable
    private fun Menu(content: @Composable () -> Unit) {
        PushupRpgTheme(darkTheme = true) {
            Box(Modifier.fillMaxSize().background(Palette.Bg1)) {
                SpaceBackdrop(Modifier.fillMaxSize())
                content()
            }
        }
    }

    @Composable
    private fun Run(
        state: CatSessionState,
        cat: CatView,
        bests: SetBests,
        depth: Float = 62f,
        coat: CatCoat = CatCoat.CHEESE,
        outcome: SetOutcome? = null,
    ) {
        val context = LocalContext.current
        PushupRpgTheme(darkTheme = true) {
            AlwaysDark {
                SurvivalScreen(
                    state = state,
                    bestScore = 620,
                    poseSource = readySource(context),
                    exercise = ExerciseType.PUSHUP,
                    isTutorial = false,
                    onRetry = {},
                    onShare = {},
                    onHome = {},
                    cat = cat,
                    catName = "",
                    catCoat = coat,
                    catWear = wear,
                    setBests = bests,
                    setOutcome = outcome,
                    depth = { depth },
                    cameraOff = true,
                )
            }
        }
    }

    /** A pose source that says it is ready, so the run is not waiting on a camera this JVM has none of. */
    private fun readySource(context: android.content.Context) =
        PoseLandmarkerSource(context, onFrame = {}, onError = {}).also { source ->
            val field = PoseLandmarkerSource::class.java.getDeclaredField("_ready").apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            (field.get(source) as kotlinx.coroutines.flow.MutableStateFlow<Boolean>).value = true
        }

    private val earlierSets = listOf(
        SetResult(reps = 14, score = 168, survivedMs = 52_000, deepReps = 9),
        SetResult(reps = 12, score = 140, survivedMs = 47_000, deepReps = 7),
    )

    @Test
    fun run_ceilingLow() = shoot("01-run") {
        Run(
            state = CatSessionState(
                phase = CatPhase.PLAYING,
                life = SurvivalState(
                    height = 0.22f, score = 132, elapsedMs = 41_000, started = true,
                    reps = 11, combo = 4, bestCombo = 6, alive = true, intensity = 0.85f,
                ),
                ended = earlierSets,
                setDeepReps = 7,
            ),
            cat = CatView(mood = CatMood.PANIC, speech = CatSpeech(CatLine.PANIC, serial = 0, atMs = 40_500)),
            bests = SetBests(bests = listOf(15, 13, 12), last = listOf(14, 12, 10)),
        )
    }

    @Test
    fun run_pushedUp() = shoot("02-push") {
        Run(
            state = CatSessionState(
                phase = CatPhase.PLAYING,
                life = SurvivalState(
                    height = 0.78f, score = 164, elapsedMs = 44_000, started = true,
                    reps = 13, combo = 7, bestCombo = 7, alive = true, intensity = 0.15f,
                ),
                ended = earlierSets,
                setDeepReps = 9,
            ),
            cat = CatView(mood = CatMood.CALM, cheer = 1f, hearts = 3,
                speech = CatSpeech(CatLine.RECORD, serial = 0, atMs = 43_800)),
            bests = SetBests(bests = listOf(15, 13, 12), last = listOf(14, 12, 10)),
        )
    }

    @Test
    fun rest() = shoot("03-rest") {
        Run(
            state = CatSessionState(
                phase = CatPhase.RESTING,
                life = SurvivalState(height = 0f, score = 170, elapsedMs = 49_000, started = true, reps = 13, alive = false),
                ended = earlierSets + SetResult(reps = 13, score = 170, survivedMs = 49_000, deepReps = 9),
                restLeftMs = 42_000,
                restLengthMs = 60_000,
            ),
            cat = CatView(mood = CatMood.CALM),
            bests = SetBests(bests = listOf(15, 13, 12, 11), last = listOf(14, 12, 10, 9)),
        )
    }

    @Test
    fun run_deep() = shoot("06-deep") {
        Run(
            state = CatSessionState(
                phase = CatPhase.PLAYING,
                life = SurvivalState(
                    height = 0.55f, score = 210, elapsedMs = 30_000, started = true,
                    reps = 9, combo = 9, bestCombo = 9, alive = true, intensity = 0.4f,
                ),
                ended = earlierSets + earlierSets,
                setDeepReps = 8,
            ),
            cat = CatView(mood = CatMood.UNEASY, cheer = 0.8f, hearts = 4),
            bests = SetBests(bests = listOf(15, 13, 12, 12, 10), last = listOf(14, 12, 10, 11, 9)),
            depth = 96f,
            coat = CatCoat.TUXEDO,
        )
    }

    private val tenSets = listOf(15, 14, 13, 12, 12, 11, 10, 10, 9, 8).mapIndexed { i, reps ->
        SetResult(reps = reps, score = reps * 13, survivedMs = 40_000L + i * 1_000, deepReps = reps - 3)
    }

    @Test
    fun ending() = shoot("07-ending") {
        val before = SetBests(bests = listOf(15, 13, 12, 11, 12, 10, 9, 10, 8, 8))
        Run(
            state = CatSessionState(
                phase = CatPhase.OVER,
                life = SurvivalState(height = 0f, score = 104, elapsedMs = 49_000, started = true, reps = 8, alive = false),
                ended = tenSets,
            ),
            cat = CatView(mood = CatMood.CALM),
            bests = before,
            outcome = SetOutcome.of(before, tenSets, hold = false),
        )
    }

    @Test
    @Config(qualifiers = "w360dp-h4000dp-xxhdpi")
    fun shop() = shoot("08-shop-tall") {
        Menu {
            WardrobeScreen(
                catName = "",
                catCoat = CatCoat.CHEESE,
                onCatChange = { _, _ -> },
                wearing = setOf(CatItem.HEADPHONES, CatItem.STAR_GLASSES, CatItem.CAT_TOWER, CatItem.STAGE),
                found = Gift.entries.take(12).toSet(),
                fresh = emptySet(),
                onWear = {},
                onTakeOff = {},
                churu = 31,
                bought = setOf(CatItem.HEADPHONES, CatItem.STAR_GLASSES, CatItem.CAT_TOWER, CatItem.STAGE),
            )
        }
    }

    @Test
    fun hub() = shoot("04-hub") {
        Menu {
            HomeScreen(
                state = HomeUiState(
                    progress = PlayerProgress(streakDays = 5, bestStreakDays = 6, lastActiveEpochDay = 20_409),
                    todayReps = 3,
                    todayWork = mapOf(ExerciseType.PUSHUP to 3),
                    loading = false,
                    today = 20_410,
                    lastWorkoutDay = 20_410,
                ),
                onPlayCat = {},
                onSelectExercise = {},
                onRecords = {},
                onSettings = {},
                onWardrobe = {},
                lastExercise = ExerciseType.PUSHUP,
                catName = "",
                catCoat = CatCoat.CREAM,
                catWear = wear,
            )
        }
    }

    @Test
    fun wardrobe() = shoot("05-wardrobe") {
        Menu {
            WardrobeScreen(
                catName = "",
                catCoat = CatCoat.CREAM,
                onCatChange = { _, _ -> },
                wearing = wear + CatItem.SPACE,
                found = Gift.entries.take(14).toSet() +
                    Gift.entries.filter { it.item in wear || it.item == CatItem.SPACE },
                fresh = emptySet(),
                onWear = {},
                onTakeOff = {},
                churu = 23,
            )
        }
    }
}
