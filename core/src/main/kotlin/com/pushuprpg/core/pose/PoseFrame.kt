package com.pushuprpg.core.pose

/**
 * One inference result: the normalized landmarks, the optional world landmarks, and the timestamp
 * of the camera frame they came from.
 *
 * [timestampMs] is the *frame* timestamp, not the time the result arrived. MediaPipe's live-stream
 * mode delivers results asynchronously and can drop or reorder them under load, so every rate
 * computation downstream — velocity, tempo, hold duration — must use this value rather than a
 * clock read at delivery time. Otherwise a thermally throttled phone silently changes the game's
 * physics, and `:core` stops being replayable from a recorded trace.
 *
 * [landmarks] is empty when no pose was detected; that is a normal frame, not an error.
 */
data class PoseFrame(
    val timestampMs: Long,
    val imageWidth: Int,
    val imageHeight: Int,
    val landmarks: List<Landmark> = emptyList(),
    /** 33 world landmarks in metres, hip-origin, or empty when unavailable. */
    val worldLandmarks: List<Landmark> = emptyList(),
    val inferenceLatencyMs: Int = 0,
) {
    init {
        require(landmarks.isEmpty() || landmarks.size == PoseLandmarks.COUNT) {
            "expected 0 or ${PoseLandmarks.COUNT} landmarks, got ${landmarks.size}"
        }
        require(worldLandmarks.isEmpty() || worldLandmarks.size == PoseLandmarks.COUNT) {
            "expected 0 or ${PoseLandmarks.COUNT} world landmarks, got ${worldLandmarks.size}"
        }
        require(imageWidth > 0 && imageHeight > 0) {
            "image size must be positive, got ${imageWidth}x$imageHeight"
        }
    }

    val hasPose: Boolean get() = landmarks.isNotEmpty()
    val hasWorld: Boolean get() = worldLandmarks.size == PoseLandmarks.COUNT

    operator fun get(index: Int): Landmark = landmarks[index]

    /**
     * Width-to-height ratio used to convert x into isotropic units.
     *
     * MediaPipe normalizes x by width and y by height *independently*, so on any non-square frame
     * a distance computed from raw (x, y) is wrong — and every quantity in this pipeline is a
     * distance or an angle. Correcting at ingest is not optional.
     */
    val aspect: Float get() = imageWidth.toFloat() / imageHeight.toFloat()

    /** x in isotropic units, where 1.0 is one image *height*. */
    fun u(index: Int): Float = landmarks[index].x * aspect

    /** y in isotropic units (already normalized by height, so unchanged). */
    fun v(index: Int): Float = landmarks[index].y

    companion object {
        /** A frame in which the model found nobody. */
        fun empty(timestampMs: Long, imageWidth: Int = 640, imageHeight: Int = 480): PoseFrame =
            PoseFrame(timestampMs, imageWidth, imageHeight)

        const val STRIDE = 5
        const val WORLD_STRIDE = 3

        /**
         * Builds a frame from the flat `[x, y, z, visibility, presence] * 33` array the Android
         * layer fills in, so the hot path does not allocate intermediate objects per frame.
         * Pass `null` for [world] when world landmarks are unavailable.
         */
        fun fromFlatArray(
            values: FloatArray,
            world: FloatArray?,
            timestampMs: Long,
            imageWidth: Int,
            imageHeight: Int,
            inferenceLatencyMs: Int = 0,
        ): PoseFrame {
            require(values.size == PoseLandmarks.COUNT * STRIDE) {
                "expected ${PoseLandmarks.COUNT * STRIDE} floats, got ${values.size}"
            }
            val landmarks = ArrayList<Landmark>(PoseLandmarks.COUNT)
            for (i in 0 until PoseLandmarks.COUNT) {
                val o = i * STRIDE
                landmarks.add(
                    Landmark(values[o], values[o + 1], values[o + 2], values[o + 3], values[o + 4])
                )
            }
            val worldLandmarks = if (world == null) {
                emptyList()
            } else {
                require(world.size == PoseLandmarks.COUNT * WORLD_STRIDE) {
                    "expected ${PoseLandmarks.COUNT * WORLD_STRIDE} world floats, got ${world.size}"
                }
                ArrayList<Landmark>(PoseLandmarks.COUNT).apply {
                    for (i in 0 until PoseLandmarks.COUNT) {
                        val o = i * WORLD_STRIDE
                        add(Landmark(world[o], world[o + 1], world[o + 2]))
                    }
                }
            }
            return PoseFrame(
                timestampMs, imageWidth, imageHeight, landmarks, worldLandmarks, inferenceLatencyMs
            )
        }
    }
}
