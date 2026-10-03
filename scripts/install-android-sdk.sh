#!/usr/bin/env bash
#
# Install the Android SDK pieces :app needs, and point the build at them, so that
# ./gradlew :app:assembleDebug runs in an environment that has no SDK.
#
# It needs Google's servers (dl.google.com and Google's Maven). Under a network policy that blocks
# them, :app cannot be compiled here at all and CI is the only check. The SDK goes wherever you say,
# and local.properties (gitignored) is written to point at it. What AGP still lacks, build-tools
# among them, it fetches itself on the first build once the licences are accepted.
#
# Usage: scripts/install-android-sdk.sh [sdk dir]     (default: ${TMPDIR:-/tmp}/android-sdk)
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SDK="${1:-${TMPDIR:-/tmp}/android-sdk}"
mkdir -p "$SDK"
SDK="$(cd "$SDK" && pwd)"

# platforms;android-37.0, not -37: see compileSdkMinor in app/build.gradle.kts.
COMPILE_SDK="$(grep -E '^\s*compileSdk = ' "$ROOT/app/build.gradle.kts" | grep -oE '[0-9]+')"
COMPILE_MINOR="$(grep -E '^\s*compileSdkMinor = ' "$ROOT/app/build.gradle.kts" | grep -oE '[0-9]+' || echo 0)"
PLATFORM="platforms;android-$COMPILE_SDK.$COMPILE_MINOR"

if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
    ZIP="$(curl -fsS https://dl.google.com/android/repository/repository2-3.xml \
        | grep -oE 'commandlinetools-linux-[0-9]+_latest\.zip' | sort -uV | tail -1)"
    TMP="$(mktemp -d)"
    curl -fsS -o "$TMP/tools.zip" "https://dl.google.com/android/repository/$ZIP"
    unzip -q "$TMP/tools.zip" -d "$TMP"
    mkdir -p "$SDK/cmdline-tools"
    rm -rf "$SDK/cmdline-tools/latest"
    mv "$TMP/cmdline-tools" "$SDK/cmdline-tools/latest"
    rm -rf "$TMP"
fi

SDKMANAGER="$SDK/cmdline-tools/latest/bin/sdkmanager"
yes | "$SDKMANAGER" --sdk_root="$SDK" --licenses > /dev/null 2>&1 || true
"$SDKMANAGER" --sdk_root="$SDK" "platform-tools" "$PLATFORM" | tail -1

echo "sdk.dir=$SDK" > "$ROOT/local.properties"
echo "SDK in $SDK ($PLATFORM); local.properties points at it."
