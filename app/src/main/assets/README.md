# Bundled models

`pose_landmarker_lite.task` — MediaPipe Pose Landmarker, float16, model bundle v1.

Source:
https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/1/pose_landmarker_lite.task

The *lite* variant is bundled deliberately: it is the only one that reliably holds 30 fps on the
mid-range Android hardware most users have, and pose landmark accuracy is not the limiting factor
for rep counting — landmark *stability* is, which the One Euro filter in `:core` handles.

`full` and `heavy` variants exist at the same URL pattern (swap `lite` for `full`/`heavy`) if a
future "high accuracy" setting is added for flagship devices.
