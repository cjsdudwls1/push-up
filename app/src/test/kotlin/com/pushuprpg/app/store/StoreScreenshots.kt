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
import com.pushuprpg.app.pose.PoseLandmarkerSource
import com.pushuprpg.app.ui.components.CatPortrait
import com.pushuprpg.app.ui.screens.SurvivalScreen
import com.pushuprpg.app.ui.theme.AlwaysDark
import com.pushuprpg.app.ui.theme.Palette
import com.pushuprpg.app.ui.theme.PushupRpgTheme
import com.pushuprpg.core.detect.ExerciseType
import com.pushuprpg.core.progression.CatItem
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
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The app's own screens for the Play listing's pictures, drawn on the JVM.
 *
 * Not a test: it asserts nothing and is skipped unless `pushup.storeShots` names an output folder
 * (scripts/store-screenshots.sh sets it). Each screen is fed a plausible state by hand — a set with
 * the ceiling on the cat and with it pushed up, a set passing its best, the ending's chart, the cat
 * dressed four ways — and drawn three pixels to a dp. tools/store_screenshots.py lays them out with
 * the pixel people (tools/pixel_people.py) and the captions.
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
    private fun Run(state: CatSessionState, cat: CatView, bests: SetBests, outcome: SetOutcome? = null) {
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
                    catCoat = CatCoat.CHEESE,
                    catWear = wear,
                    setBests = bests,
                    setOutcome = outcome,
                    depth = { 62f },
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
            (field.get(source) as MutableStateFlow<Boolean>).value = true
        }

    private val earlierSets = listOf(
        SetResult(reps = 14, score = 168, survivedMs = 52_000, deepReps = 9),
        SetResult(reps = 12, score = 140, survivedMs = 47_000, deepReps = 7),
    )

    private val threeSetBests = SetBests(bests = listOf(15, 13, 12), last = listOf(14, 12, 10))

    /** A set passing its best (지금 13 / 최고 12), the ceiling up: the callout's screen and the records' line. */
    @Test
    fun pushedUp() = shoot("02-push") {
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
            bests = threeSetBests,
        )
    }

    /** The ceiling all but on the cat: 가만히 있으면. */
    @Test
    fun gameLow() = shoot("game-low") {
        Run(
            state = CatSessionState(
                phase = CatPhase.PLAYING,
                life = SurvivalState(
                    height = 0.06f, score = 96, elapsedMs = 30_000, started = true,
                    reps = 8, combo = 0, bestCombo = 5, alive = true, intensity = 1f,
                ),
                ended = earlierSets,
                setDeepReps = 5,
            ),
            cat = CatView(mood = CatMood.PANIC),
            bests = threeSetBests,
        )
    }

    /** The ceiling pushed all the way up: 운동하면. */
    @Test
    fun gameHigh() = shoot("game-high") {
        Run(
            state = CatSessionState(
                phase = CatPhase.PLAYING,
                life = SurvivalState(
                    height = 0.97f, score = 184, elapsedMs = 46_000, started = true,
                    reps = 14, combo = 8, bestCombo = 8, alive = true, intensity = 0f,
                ),
                ended = earlierSets,
                setDeepReps = 10,
            ),
            cat = CatView(mood = CatMood.CALM, cheer = 1f, hearts = 4),
            bests = threeSetBests,
        )
    }

    private val tenSets = listOf(15, 14, 13, 12, 12, 11, 10, 10, 9, 8).mapIndexed { i, reps ->
        SetResult(reps = reps, score = reps * 13, survivedMs = 40_000L + i * 1_000, deepReps = reps - 3)
    }

    /** A full session's ending: every set, the ones that beat their best starred. */
    @Test
    fun ending() = shoot("07-ending") {
        // Set 1 beats its best too: a blind reviewer asked why the tallest bar had no star.
        val before = SetBests(bests = listOf(14, 13, 12, 11, 12, 10, 9, 10, 8, 8))
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

    @Composable
    private fun Portrait(coat: CatCoat, wear: Set<CatItem>) {
        PushupRpgTheme(darkTheme = true) {
            Box(Modifier.fillMaxSize().background(Palette.Bg1)) {
                CatPortrait(coat = coat, wear = wear, modifier = Modifier.fillMaxSize())
            }
        }
    }

    @Test
    @Config(qualifiers = "w300dp-h220dp-xxhdpi")
    fun cat1() = shoot("cat-1") { Portrait(CatCoat.CHEESE, setOf(CatItem.CROWN, CatItem.BOW_TIE, CatItem.YARN, CatItem.SPACE)) }

    @Test
    @Config(qualifiers = "w300dp-h220dp-xxhdpi")
    fun cat2() = shoot("cat-2") {
        Portrait(CatCoat.TUXEDO, setOf(CatItem.HEADPHONES, CatItem.STAR_GLASSES, CatItem.CAT_TOWER, CatItem.STAGE))
    }

    @Test
    @Config(qualifiers = "w300dp-h220dp-xxhdpi")
    fun cat3() = shoot("cat-3") { Portrait(CatCoat.CALICO, setOf(CatItem.FLOWER_CROWN, CatItem.PEARLS, CatItem.CHERRY_BLOSSOM)) }

    @Test
    @Config(qualifiers = "w300dp-h220dp-xxhdpi")
    fun cat4() = shoot("cat-4") {
        Portrait(CatCoat.MACKEREL, setOf(CatItem.HEADBAND, CatItem.SPORTS_TOWEL, CatItem.BALL, CatItem.GYM))
    }
}
