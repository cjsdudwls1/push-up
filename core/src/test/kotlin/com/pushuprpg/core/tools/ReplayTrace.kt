package com.pushuprpg.core.tools

import com.pushuprpg.core.detect.DetectorFactory
import com.pushuprpg.core.detect.ExerciseType
import com.pushuprpg.core.detect.RepEvent
import com.pushuprpg.core.detect.UserProfile
import com.pushuprpg.core.trace.PoseTrace
import com.pushuprpg.core.trace.TraceReplay
import java.io.File

/**
 * Replays a trace sent from a phone through the detector and says, rep by rep, what it decided.
 *
 * Run it with `scripts/replay-trace.sh <trace.json>`. The trace's notes carry what the run started
 * from — the movement and the calibration profile — so the replay starts from the same place the
 * phone did. Lives with the tests rather than in `:core` proper because it is a tool for people,
 * not something the app ships.
 */
fun main(args: Array<String>) {
    val path = args.firstOrNull() ?: error("usage: replay-trace <trace.json>")
    val trace = PoseTrace.decode(File(path).readText())
    val notes = trace.notes.split(' ').mapNotNull { part ->
        part.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] }
    }.toMap()

    val exercise = notes["exercise"]?.let { name -> ExerciseType.entries.firstOrNull { it.name == name } }
        ?: ExerciseType.PUSHUP
    val profile = notes["profile"]?.split('/')?.takeIf { it.size == 3 }?.let {
        UserProfile(it[0].toFloat(), it[1].toFloat(), it[2].toInt())
    } ?: UserProfile.empty()

    val detector = DetectorFactory.create(exercise, profile = profile)
    val result = TraceReplay.run(detector, trace, keepTicks = true)

    val t0 = trace.frames.firstOrNull()?.t ?: 0L
    val durationS = ((trace.frames.lastOrNull()?.t ?: t0) - t0) / 1000f
    fun at(tMs: Long) = "%7.2fs".format((tMs - t0) / 1000f)

    println("device    ${trace.device}")
    println("notes     ${trace.notes}")
    println("frames    ${trace.frames.size} over %.1fs (%.1f fps), %dx%d".format(
        durationS, trace.frames.size / durationS.coerceAtLeast(0.001f), trace.imageWidth, trace.imageHeight))
    println("replayed  $exercise from ${if (profile.isEmpty) "no calibration" else profile.toString()}")
    println()

    for (e in result.events) {
        val line = when (e) {
            is RepEvent.Strike -> "STRIKE    rep ${e.repIndex} ${e.grade} at depth %.0f".format(e.depth)
            is RepEvent.Abandoned -> "REFUSED   ${e.reason}"
            is RepEvent.Shallow -> "SHALLOW   reached %.0f".format(e.maxDepth)
            is RepEvent.QualityChanged -> "QUALITY   ${e.quality}"
            is RepEvent.Completed -> null
            is RepEvent.DeepUpgrade -> "DEEP      rep ${e.repIndex} reached %.0f".format(e.depth)
            else -> e::class.simpleName
        } ?: continue
        println("${at(e.tMs)}  $line")
    }

    println()
    println("reps      ${result.repCount}")
    val refusals = result.events.filterIsInstance<RepEvent.Abandoned>().groupingBy { it.reason }.eachCount()
    println("refused   ${refusals.ifEmpty { "none" }}")
    println("shallow   ${result.events.count { it is RepEvent.Shallow }}")
    val quality = result.ticks.groupingBy { it.quality }.eachCount()
    println("quality   " + quality.entries.sortedByDescending { it.value }
        .joinToString { "${it.key} %.0f%%".format(100f * it.value / result.ticks.size) })
    val phases = result.ticks.groupingBy { it.phase }.eachCount()
    println("phases    " + phases.entries.sortedByDescending { it.value }
        .joinToString { "${it.key} %.0f%%".format(100f * it.value / result.ticks.size) })
    println("max depth %.0f".format(result.ticks.maxOfOrNull { it.depth } ?: 0f))
}
