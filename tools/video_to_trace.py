#!/usr/bin/env python3
"""
Turns a screen recording of the app into a pose trace that scripts/replay-trace.sh can replay.

When a device report comes with a video instead of a trace, this is the next best thing: it runs
the app's own pose model (app/src/main/assets/pose_landmarker_lite.task) over every frame, with the
app's thresholds, and writes the same JSON the debug build's 동작 기록 보내기 shares. The two sets in
core/src/test/resources/traces came from this.

It is not the phone's own trace, and the differences matter when reading the result:
- The model sees the recorded screen, overlay and all, not the camera frame.
- The frame rate is the recording's (40 fps and up), not the rate the phone ran the model at
  (15-30). Replay every second and third frame as well; RealTraceTest does.
- Timestamps start at an hour, like a real trace, so nothing reads them as a session start.

Usage:  tools/video_to_trace.py recording.mp4 out.json "what the set was"
Needs:  pip install mediapipe opencv-python-headless
        and libEGL/libGLES (apt-get install libegl1 libgles2) on a headless Linux box.
"""
import json
import os
import sys

import cv2
import mediapipe as mp
from mediapipe.tasks import python as mpt
from mediapipe.tasks.python import vision

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODEL = os.path.join(ROOT, "app/src/main/assets/pose_landmarker_lite.task")
# PoseLandmarkerSource's thresholds: a different model or different thresholds would not be the app.
CONFIDENCE = 0.5
START_MS = 3_600_000


def main():
    if len(sys.argv) < 3:
        sys.exit(__doc__)
    src, out = sys.argv[1], sys.argv[2]
    notes = sys.argv[3] if len(sys.argv) > 3 else ""
    options = vision.PoseLandmarkerOptions(
        base_options=mpt.BaseOptions(model_asset_path=MODEL),
        running_mode=vision.RunningMode.VIDEO,
        num_poses=1,
        min_pose_detection_confidence=CONFIDENCE,
        min_pose_presence_confidence=CONFIDENCE,
        min_tracking_confidence=CONFIDENCE,
    )
    cap = cv2.VideoCapture(src)
    width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
    frames = []
    last_t = -1
    with vision.PoseLandmarker.create_from_options(options) as landmarker:
        while True:
            ok, image = cap.read()
            if not ok:
                break
            t = int(round(cap.get(cv2.CAP_PROP_POS_MSEC)))
            # VIDEO mode refuses a timestamp that does not increase, and recordings repeat some.
            if t <= last_t:
                t = last_t + 1
            last_t = t
            rgb = cv2.cvtColor(image, cv2.COLOR_BGR2RGB)
            result = landmarker.detect_for_video(mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb), t)
            frame = {"t": START_MS + t, "lm": [], "world": []}
            if result.pose_landmarks:
                frame["lm"] = [
                    round(v, 4)
                    for p in result.pose_landmarks[0]
                    for v in (p.x, p.y, p.z,
                              1.0 if p.visibility is None else p.visibility,
                              1.0 if p.presence is None else p.presence)
                ]
                frame["world"] = [round(v, 4) for p in result.pose_world_landmarks[0] for v in (p.x, p.y, p.z)]
            frames.append(frame)
    trace = {
        "version": 1,
        "imageWidth": width,
        "imageHeight": height,
        "device": "screen recording, re-run offline",
        "notes": notes,
        "frames": frames,
    }
    with open(out, "w") as f:
        json.dump(trace, f, separators=(",", ":"))
    seen = sum(1 for f in frames if f["lm"])
    print(f"{out}: {len(frames)} frames, {seen} with a pose")


if __name__ == "__main__":
    main()
