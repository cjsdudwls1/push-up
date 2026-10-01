# Bundled models

`pose_landmarker_full.task` — MediaPipe Pose Landmarker *full*, float16, model bundle v1.

Source:
https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_full/float16/1/pose_landmarker_full.task
(sha256 `5134a3aad27a58b93da0088d431f366da362b44e3ccfbe3462b3827a839011b1`)

Until 2026-10-01 the *lite* variant was bundled, for its frame rate on mid-range phones. The owner
chose accuracy instead: tired reps at the end of a set went uncounted. The traces in
`core/src/test/resources/traces/` recorded before then are the lite model's.
`lite` and `heavy` live at the same URL pattern.
