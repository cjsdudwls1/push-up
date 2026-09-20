#!/usr/bin/env bash
#
# Catch the string-resource mistakes that otherwise cost a CI round.
#
# `:app` cannot be compiled in this environment — Google's Maven is unreachable — so a duplicated
# or missing <string> is not found until CI runs aapt, four minutes later. These three checks need
# nothing but the repo and take under a second:
#
#   1. duplicate names        — aapt fails the build with "Found item String/x more than one time"
#   2. referenced but missing — R.string.x with no <string name="x">, a Kotlin compile error
#   3. format-arg mismatch    — a string with %1$s used via a stringResource() call passing none,
#                               which throws at runtime rather than at build time
#
# Usage: scripts/check-resources.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
STRINGS="$ROOT/app/src/main/res/values/strings.xml"
SRC="$ROOT/app/src/main/kotlin"
fail=0

# --- 1. duplicate names -------------------------------------------------------------------------
dupes="$(grep -o 'name="[^"]*"' "$STRINGS" | sort | uniq -d || true)"
if [ -n "$dupes" ]; then
    echo "DUPLICATE string resources (aapt will fail the build):"
    echo "$dupes" | sed 's/^/  /'
    fail=1
fi

# --- 2. referenced but never defined ------------------------------------------------------------
defined="$(mktemp)"; referenced="$(mktemp)"
trap 'rm -f "$defined" "$referenced"' EXIT
grep -o 'name="[^"]*"' "$STRINGS" | sed 's/name="//;s/"//' | sort -u > "$defined"
grep -rho 'R\.string\.[A-Za-z0-9_]*' "$SRC" | sed 's/R\.string\.//' | sort -u > "$referenced"
missing="$(comm -13 "$defined" "$referenced")"
if [ -n "$missing" ]; then
    echo "REFERENCED but not defined in strings.xml:"
    echo "$missing" | sed 's/^/  /'
    fail=1
fi

# --- 3. format strings used with no arguments ---------------------------------------------------
# A formatted string reached through a bare stringResource(R.string.x) renders the raw %1$s to the
# user. Only flags the single-argument call shape, which is the one that silently looks fine.
while read -r name; do
    if grep -q "stringResource(R\.string\.$name)" -r "$SRC"; then
        echo "FORMAT string used with no arguments: $name"
        fail=1
    fi
done < <(grep -E '<string name="[^"]*">[^<]*%[0-9]+\$' "$STRINGS" | sed 's/.*name="//;s/".*//')

if [ "$fail" -eq 0 ]; then
    echo "resources OK — $(wc -l < "$defined") strings, none duplicated, none missing"
fi
exit "$fail"
