#!/usr/bin/env bash
#
# Render the cat with each of its things to PNG, without an Android device.
#
# The cat is Compose drawing (ui/components/Cat*.kt), and Compose draws through Skia on the desktop
# just as it does on a phone. This mirrors those files — with :core, which they read, and the coat
# enum lifted out of the app's contracts — into a standalone Gradle build on Compose Multiplatform's
# desktop UI graphics, and renders one sheet per thing: the wardrobe tile on two coats, the hub's
# portrait, and the run's cat calm and in a panic. Unlike render-art.sh's Java2D shim this is the
# phone's own renderer; only fonts differ, and the cat draws no text.
#
# A new file of cat drawing has to be added to ART below. A composable cannot be: the build has the
# UI graphics and nothing above them, which is why the pictures the screens frame live in
# CatPictures.kt rather than in the composables that frame them.
#
# Usage: scripts/render-cat.sh [output dir] [ITEM ...]     e.g. scripts/render-cat.sh /tmp/cat CROWN YARN
# With no items it renders every one, and all.png with every tile together.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# One build per checkout, kept between runs so that only what changed is compiled again.
OUT="${PUSHUP_CAT_BUILD_DIR:-${TMPDIR:-/tmp}/pushup-cat-preview-$(printf %s "$ROOT" | cksum | cut -d' ' -f1)}"
SHEETS="${1:-$ROOT/build/cat-preview}"
shift || true
case "$SHEETS" in /*) ;; *) SHEETS="$PWD/$SHEETS" ;; esac

COMPONENTS="$ROOT/app/src/main/kotlin/com/pushuprpg/app/ui/components"
ART=(CatArt CatHeadItems CatFaceItems CatNeckItems CatToys CatScenes CatPictures)

KOTLIN_VERSION="$(grep -E '^kotlin = ' "$ROOT/gradle/libs.versions.toml" | cut -d'"' -f2)"
COROUTINES_VERSION="$(grep -E '^coroutines = ' "$ROOT/gradle/libs.versions.toml" | cut -d'"' -f2)"
SERIALIZATION_VERSION="$(grep -E '^serialization = ' "$ROOT/gradle/libs.versions.toml" | cut -d'"' -f2)"
# Compose Multiplatform's desktop build of the UI graphics the app draws with, and Skia's natives.
COMPOSE_DESKTOP_VERSION="1.12.1"
SKIKO_VERSION="0.150.1"
case "$(uname -s)-$(uname -m)" in
    Linux-x86_64) SKIKO_TARGET=linux-x64 ;;
    Linux-aarch64) SKIKO_TARGET=linux-arm64 ;;
    Darwin-arm64) SKIKO_TARGET=macos-arm64 ;;
    Darwin-x86_64) SKIKO_TARGET=macos-x64 ;;
    *) echo "render-cat.sh: no Skia build for $(uname -s)-$(uname -m)" >&2; exit 1 ;;
esac

SRC="$OUT/src/main/kotlin"
rm -rf "$SRC"
mkdir -p "$SRC/art"
# Linked rather than copied, so a compile error points at the real file.
ln -s "$ROOT/core/src/main/kotlin" "$SRC/core"
ln -s "$ROOT/tools/cat-preview/src/main/kotlin/catpreview" "$SRC/catpreview"
for name in "${ART[@]}"; do
    ln -s "$COMPONENTS/$name.kt" "$SRC/art/$name.kt"
done
{
    echo "package com.pushuprpg.app.domain"
    echo
    awk '/^enum class CatCoat/ { p = 1 } p { print } p && /^}/ { exit }' \
        "$ROOT/app/src/main/kotlin/com/pushuprpg/app/domain/Contracts.kt"
} > "$SRC/art/CatCoat.kt"

cat > "$OUT/settings.gradle.kts" <<SETTINGS
rootProject.name = "pushup-cat-preview"
SETTINGS

# Compiled inside the Gradle daemon, and small: several of these may run at once.
cat > "$OUT/gradle.properties" <<PROPERTIES
org.gradle.jvmargs=-Xmx1536m
kotlin.compiler.execution.strategy=in-process
org.gradle.daemon.idletimeout=900000
PROPERTIES

cat > "$OUT/build.gradle.kts" <<BUILD
plugins {
    kotlin("jvm") version "$KOTLIN_VERSION"
    kotlin("plugin.serialization") version "$KOTLIN_VERSION"
    application
}

repositories {
    google()
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$COROUTINES_VERSION")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$SERIALIZATION_VERSION")
    implementation("org.jetbrains.compose.ui:ui-graphics-desktop:$COMPOSE_DESKTOP_VERSION")
    implementation("org.jetbrains.skiko:skiko-awt-runtime-$SKIKO_TARGET:$SKIKO_VERSION")
}

application { mainClass.set("catpreview.MainKt") }
BUILD

mkdir -p "$SHEETS"
cd "$OUT"
"${GRADLE_CMD:-gradle}" --quiet run --args="$SHEETS $*"
