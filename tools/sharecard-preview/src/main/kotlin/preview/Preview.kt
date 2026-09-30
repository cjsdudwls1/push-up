package preview

import android.content.res.Resources
import com.pushuprpg.app.share.ShareCardData
import com.pushuprpg.app.share.ShareCardRenderer
import java.io.File

/**
 * Renders every share card variant to PNG so they can be looked at.
 *
 * The variants are chosen to break the layout rather than to flatter it: a six-digit score and the
 * longest run a person could play. If those fit, the real ones do.
 */
fun main(args: Array<String>) {
    val outDir = File(args.getOrElse(0) { "build/cards" })
    outDir.mkdirs()

    val res = Resources(STRING_TABLE)

    val cards = listOf(
        "survival-typical" to ShareCardData.Survival(movement = "푸쉬업", score = 1_240, best = 1_240, reps = 31, seconds = 96),
        "survival-first-try" to ShareCardData.Survival(movement = "턱걸이", score = 180, best = 940, reps = 6, seconds = 23),
        "survival-extreme" to ShareCardData.Survival(movement = "스쿼트", score = 184_500, best = 184_500, reps = 412, seconds = 1_247),
    )

    val banner = File(outDir, "feature-graphic.png")
    javax.imageio.ImageIO.write(FeatureGraphic.render().image, "png", banner)
    println("${banner.path}  ${FeatureGraphic.WIDTH}x${FeatureGraphic.HEIGHT}")

    cards.forEach { (name, data) ->
        val bitmap = ShareCardRenderer.render(res, data)
        val file = File(outDir, "$name.png")
        javax.imageio.ImageIO.write(bitmap.image, "png", file)
        println("${file.path}  ${bitmap.image.width}×${bitmap.image.height}")
    }
}
