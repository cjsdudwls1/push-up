#!/usr/bin/env bash
#
# Build and unit-test :core WITHOUT the Android SDK.
#
# :core is pure Kotlin/JVM, but the root Gradle build declares the Android Gradle Plugin, so a
# plain `./gradlew :core:test` still needs the Android toolchain to configure the :app project.
# This script mirrors core/src into a throwaway standalone Gradle build (Maven Central only) so the
# game logic can be compiled and tested on any machine — CI, a server, a container without the SDK.
#
# Usage: scripts/test-core.sh [extra gradle args]
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="${PUSHUP_CORE_BUILD_DIR:-${TMPDIR:-/tmp}/pushup-core-standalone}"

KOTLIN_VERSION="$(grep -E '^kotlin = ' "$ROOT/gradle/libs.versions.toml" | cut -d'"' -f2)"
COROUTINES_VERSION="$(grep -E '^coroutines = ' "$ROOT/gradle/libs.versions.toml" | cut -d'"' -f2)"
SERIALIZATION_VERSION="$(grep -E '^serialization = ' "$ROOT/gradle/libs.versions.toml" | cut -d'"' -f2)"

rm -rf "$OUT"
mkdir -p "$OUT"
# Symlink rather than copy so failures point at the real source files.
ln -s "$ROOT/core/src" "$OUT/src"

cat > "$OUT/settings.gradle.kts" <<SETTINGS
rootProject.name = "pushup-core-standalone"
SETTINGS

cat > "$OUT/build.gradle.kts" <<BUILD
plugins {
    kotlin("jvm") version "$KOTLIN_VERSION"
    kotlin("plugin.serialization") version "$KOTLIN_VERSION"
}

repositories { mavenCentral() }

kotlin { jvmToolchain(17) }

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$COROUTINES_VERSION")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$SERIALIZATION_VERSION")
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$COROUTINES_VERSION")
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("passed", "skipped", "failed") }
}
BUILD

cd "$OUT"
exec "${GRADLE_CMD:-gradle}" test "$@"
