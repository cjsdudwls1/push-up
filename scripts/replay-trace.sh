#!/usr/bin/env bash
#
# Replay a trace recorded on a phone (settings → 테스트 → 방금 한 운동 기록 보내기) through the
# detector, and print what it decided rep by rep: strikes, refusals and why, shallow reps, tracking.
#
# Same standalone, Maven-Central-only build as test-core.sh, so it runs without the Android SDK.
#
# Usage: scripts/replay-trace.sh path/to/pushup-trace-*.json [extra gradle args]
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TRACE="$(cd "$(dirname "$1")" && pwd)/$(basename "$1")"
shift
OUT="${PUSHUP_REPLAY_BUILD_DIR:-${TMPDIR:-/tmp}/pushup-replay-standalone}"

KOTLIN_VERSION="$(grep -E '^kotlin = ' "$ROOT/gradle/libs.versions.toml" | cut -d'"' -f2)"
COROUTINES_VERSION="$(grep -E '^coroutines = ' "$ROOT/gradle/libs.versions.toml" | cut -d'"' -f2)"
SERIALIZATION_VERSION="$(grep -E '^serialization = ' "$ROOT/gradle/libs.versions.toml" | cut -d'"' -f2)"

rm -rf "$OUT"
mkdir -p "$OUT"
ln -s "$ROOT/core/src" "$OUT/src"

cat > "$OUT/settings.gradle.kts" <<SETTINGS
rootProject.name = "pushup-replay-standalone"
SETTINGS

cat > "$OUT/build.gradle.kts" <<BUILD
plugins {
    kotlin("jvm") version "$KOTLIN_VERSION"
    kotlin("plugin.serialization") version "$KOTLIN_VERSION"
}

repositories { mavenCentral() }

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$COROUTINES_VERSION")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$SERIALIZATION_VERSION")
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$COROUTINES_VERSION")
}

tasks.register<JavaExec>("replay") {
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.pushuprpg.core.tools.ReplayTraceKt")
    args("$TRACE")
}
BUILD

cd "$OUT"
exec "${GRADLE_CMD:-gradle}" -q replay "$@"
