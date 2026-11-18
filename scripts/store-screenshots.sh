#!/usr/bin/env bash
#
# The Play listing's screenshots and feature graphic, drawn from the app's real screens.
#
# Robolectric draws each screen with a hand-fed state (app/src/test/.../store/StoreScreenshots.kt) on
# the JVM, through Android's own graphics; tools/store_screenshots.py then puts the captions on and
# writes docs/store-assets/screenshots/*.png and feature-graphic.png. Needs the Android SDK
# (scripts/install-android-sdk.sh) and Pillow. A push of docs/store-assets to the install branch
# uploads them to Play (.github/workflows/listing.yml).
#
# Usage: scripts/store-screenshots.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RAW="${TMPDIR:-/tmp}/pushup-store-shots"
rm -rf "$RAW"
cd "$ROOT"
./gradlew :app:testDebugUnitTest --tests 'com.pushuprpg.app.store.StoreScreenshots' \
    -Ppushup.storeShots="$RAW" --no-configuration-cache -q "$@"
python3 tools/store_screenshots.py "$RAW"
