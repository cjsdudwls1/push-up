package com.pushuprpg.core.pose

/**
 * One inference result: 33 landmarks plus the timestamp of the camera frame they came from.
 *
 * [timestampMs] is the *frame* timestamp, not the time the result arrived. MediaPipe's live-stream
 * mode delivers results asynchronously and can drop or reorder them under load, so every rate
 * computation downstream (velocity, tempo, hold duration) must use this value rather than a clock
 * read at delivery time — otherwise a thermal-throttled phone silently changes the game's physics.
 */
data class PoseFrame(
    val landmarks: List<Landmark>,
    val timestampMs: Long,
) {
    init {
        require(landmarks.size == PoseLandmarks.COUNT) {
            "expected ${PoseLandmarks.COUNT} landmarks, got ${landmarks.size}"
        }
    }

    operator fun get(index: Int): Landmark = landmarks[index]

    fun isReliable(index: Int, threshold: Float = Landmark.DEFAULT_RELIABILITY): Boolean =
        landmarks[index].isReliable(threshold)

    /** True when every one of [indices] is reliable. */
    fun allReliable(vararg indices: Int, threshold: Float = Landmark.DEFAULT_RELIABILITY): Boolean =
        indices.all { landmarks[it].isReliable(threshold) }

    /**
     * Midpoint of two landmarks, used constantly (shoulder centre, hip centre). The result carries
     * the *lower* of the two confidences so an uncertain endpoint cannot launder itself into a
     * confident midpoint.
     */
    fun midpoint(a: Int, b: Int): Landmark {
        val la = landmarks[a]
        val lb = landmarks[b]
        return Landmark(
            x = (la.x + lb.x) / 2f,
            y = (la.y + lb.y) / 2f,
            z = (la.z + lb.z) / 2f,
            visibility = minOf(la.visibility, lb.visibility),
            presence = minOf(la.presence, lb.presence),
        )
    }

    companion object {
        val EMPTY_LANDMARKS: List<Landmark> = List(PoseLandmarks.COUNT) { Landmark.ZERO }

        /** A frame with no person in it — all landmarks present but zero-confidence. */
        fun empty(timestampMs: Long): PoseFrame = PoseFrame(EMPTY_LANDMARKS, timestampMs)

        /**
         * Builds a frame from the flat `[x, y, z, visibility, presence] * 33` array the Android
         * layer fills in, avoiding per-frame allocation of intermediate objects on the hot path.
         */
        fun fromFlatArray(values: FloatArray, timestampMs: Long): PoseFrame {
            require(values.size == PoseLandmarks.COUNT * STRIDE) {
                "expected ${PoseLandmarks.COUNT * STRIDE} floats, got ${values.size}"
            }
            val landmarks = ArrayList<Landmark>(PoseLandmarks.COUNT)
            for (i in 0 until PoseLandmarks.COUNT) {
                val o = i * STRIDE
                landmarks.add(
                    Landmark(
                        x = values[o],
                        y = values[o + 1],
                        z = values[o + 2],
                        visibility = values[o + 3],
                        presence = values[o + 4],
                    )
                )
            }
            return PoseFrame(landmarks, timestampMs)
        }

        const val STRIDE = 5
    }
}
