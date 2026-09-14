#!/usr/bin/env bash
#
# Render the share cards to PNG without an Android device.
#
# :app cannot be compiled in every environment this repo is worked on, and the share card is the
# one surface whose only job is to look right to someone who has never used the app. This mirrors
# the real ShareCardRenderer into a standalone Gradle build alongside a Java2D-backed
# android.graphics shim, so the composition can be reviewed by looking at it.
#
# The output is a proof of layout, not a pixel-exact render: fonts differ from a Korean device.
#
# Usage: scripts/preview-share-card.sh [output dir]
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="${PUSHUP_CARD_BUILD_DIR:-${TMPDIR:-/tmp}/pushup-card-preview}"
CARDS="${1:-$ROOT/build/share-cards}"

KOTLIN_VERSION="$(grep -E '^kotlin = ' "$ROOT/gradle/libs.versions.toml" | cut -d'"' -f2)"

rm -rf "$OUT"
mkdir -p "$OUT/src/main/kotlin/generated"

cp -r "$ROOT/tools/sharecard-preview/src/main/kotlin/." "$OUT/src/main/kotlin/"
mkdir -p "$OUT/src/main/kotlin/share"
cp "$ROOT/app/src/main/kotlin/com/pushuprpg/app/share/ShareCardData.kt" "$OUT/src/main/kotlin/share/"
cp "$ROOT/app/src/main/kotlin/com/pushuprpg/app/share/ShareCardRenderer.kt" "$OUT/src/main/kotlin/share/"

python3 "$ROOT/tools/sharecard-preview/generate_resources.py" \
    "$ROOT/app/src/main/res/values/strings.xml" \
    "$OUT/src/main/kotlin/generated"

cat > "$OUT/settings.gradle.kts" <<SETTINGS
rootProject.name = "pushup-card-preview"
SETTINGS

cat > "$OUT/build.gradle.kts" <<BUILD
plugins {
    kotlin("jvm") version "$KOTLIN_VERSION"
    application
}

repositories { mavenCentral() }

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

application { mainClass.set("preview.PreviewKt") }
BUILD

mkdir -p "$CARDS"
cd "$OUT"
"${GRADLE_CMD:-gradle}" --quiet run --args "$CARDS"
