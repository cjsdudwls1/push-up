package com.pushuprpg.core.tools

import com.pushuprpg.core.detect.DetectorFactory
import com.pushuprpg.core.detect.ExerciseType
import com.pushuprpg.core.detect.PoseTick
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
 * phone did, and every `switch=<ms>:<MOVEMENT>:<profile>` the run made mid-way, so it changes
 * movement at the same frame the phone did. Lives with the tests rather than in `:core` proper because it is a tool for people,
 * not something the app ships.
 */
fun main(args: Array<String>) {
    val path = args.firstOrNull() ?: error("usage: replay-trace <trace.json>")
    val trace = PoseTrace.decode(File(path).readText())
    val pairs = trace.notes.split(' ').mapNotNull { part ->
        part.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] }
    }
    val notes = pairs.toMap()
    fun movement(name: String?) = ExerciseType.entries.firstOrNull { it.name == name } ?: ExerciseType.PUSHUP
    fun profileOf(text: String?) = text?.split('/')?.takeIf { it.size == 3 }?.let {
        UserProfile(it[0].toFloat(), it[1].toFloat(), it[2].toInt())
    } ?: UserProfile.empty()

    val exercise = movement(notes["exercise"])
    val profile = profileOf(notes["profile"])
    val switches = pairs.filter { it.first == "switch" }.map { (_, v) ->
        val (at, name, prof) = (v.split(':', limit = 3) + listOf("", "", "")).take(3)
        Triple(at.toLong(), movement(name), profileOf(prof))
    }.sortedBy { it.first }

    // One detector per stretch, swapped at the frame the phone swapped it.
    var detector = DetectorFactory.create(exercise, profile = profile)
    var nextSwitch = 0
    val switchedAt = mutableListOf<Pair<Long, ExerciseType>>()
    val allEvents = mutableListOf<RepEvent>()
    val allTicks = mutableListOf<PoseTick>()
    var reps = 0
    for (frame in TraceReplay.frames(trace)) {
        while (nextSwitch < switches.size && frame.timestampMs >= switches[nextSwitch].first) {
            reps += detector.sessionSummary().repCount
            val (_, to, prof) = switches[nextSwitch++]
            detector = DetectorFactory.create(to, profile = prof)
            switchedAt += frame.timestampMs to to
        }
        val tick = detector.onFrame(frame)
        allEvents += tick.events
        allTicks += tick
    }
    reps += detector.sessionSummary().repCount
    val result = object {
        val repCount = reps
        val events = allEvents
        val ticks = allTicks
    }

    val t0 = trace.frames.firstOrNull()?.t ?: 0L
    val durationS = ((trace.frames.lastOrNull()?.t ?: t0) - t0) / 1000f
    fun at(tMs: Long) = "%7.2fs".format((tMs - t0) / 1000f)

    println("device    ${trace.device}")
    println("notes     ${trace.notes}")
    println("frames    ${trace.frames.size} over %.1fs (%.1f fps), %dx%d".format(
        durationS, trace.frames.size / durationS.coerceAtLeast(0.001f), trace.imageWidth, trace.imageHeight))
    println("replayed  $exercise from ${if (profile.isEmpty) "no calibration" else profile.toString()}")
    println()

    var switchIndex = 0
    for (e in result.events) {
        while (switchIndex < switchedAt.size && switchedAt[switchIndex].first <= e.tMs) {
            val (at, to) = switchedAt[switchIndex++]
            println("${at(at)}  SWITCH    to $to")
        }
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
