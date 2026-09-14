package preview

import android.content.res.Resources
import com.pushuprpg.app.share.ShareCardData
import com.pushuprpg.app.share.ShareCardRenderer
import java.io.File

/**
 * Renders every share card variant to PNG so they can be looked at.
 *
 * The variants are chosen to break the layout rather than to flatter it: a six-digit score, the
 * longest dungeon name in the content table, a rank name that is four Hangul syllables. If those
 * fit, the real ones do.
 */
fun main(args: Array<String>) {
    val outDir = File(args.getOrElse(0) { "build/cards" })
    outDir.mkdirs()

    val res = Resources(STRING_TABLE)

    val cards = listOf(
        "survival-typical" to ShareCardData.Survival(score = 1_240, best = 1_240, reps = 31, seconds = 96),
        "survival-first-try" to ShareCardData.Survival(score = 180, best = 940, reps = 6, seconds = 23),
        "survival-extreme" to ShareCardData.Survival(score = 184_500, best = 184_500, reps = 412, seconds = 1_247),
        "dungeon-cleared" to ShareCardData.Dungeon(
            dungeonName = "고블린 왕의 알현실",
            cleared = true,
            reps = 64,
            maxCombo = 21,
            seconds = 214,
            rankKorean = "그랜드마스터",
            lifetimeReps = 21_400,
        ),
        "dungeon-defeat" to ShareCardData.Dungeon(
            dungeonName = "무너진 초소",
            cleared = false,
            reps = 18,
            maxCombo = 5,
            seconds = 71,
            rankKorean = "새싹",
            lifetimeReps = 40,
        ),
    )

    cards.forEach { (name, data) ->
        val bitmap = ShareCardRenderer.render(res, data)
        val file = File(outDir, "$name.png")
        javax.imageio.ImageIO.write(bitmap.image, "png", file)
        println("${file.path}  ${bitmap.image.width}×${bitmap.image.height}")
    }
}
