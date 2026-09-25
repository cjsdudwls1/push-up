#!/usr/bin/env bash
#
# Catch the string-resource mistakes that otherwise cost a CI round.
#
# `:app` cannot be compiled in this environment — Google's Maven is unreachable — so a duplicated
# or missing <string> is not found until CI runs aapt, four minutes later. These checks need
# nothing but the repo and python3, and take under a second:
#
#   1. duplicate names        — aapt fails the build with "Found item String/x more than one time"
#   2. referenced but missing — R.string.x with no <string name="x">, a Kotlin compile error
#   3. format-arg mismatch    — a string with %1$s used via a stringResource() call passing none,
#                               which throws at runtime rather than at build time
#   4. Korean in Kotlin       — a Hangul string literal under app/src/main/kotlin: copy that
#                               strings.xml cannot change (comments and KDoc are fine)
#   5. never referenced       — a <string> no Kotlin or XML reads; a warning, not a failure, since
#                               an edit to it changes nothing on screen
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

# --- 4. Korean string literals in Kotlin -------------------------------------------------------
# Every user-visible string lives in strings.xml. A Hangul literal in a composable is text that an
# edit to strings.xml cannot reach, which is how the two drifted apart before. Comments and KDoc are
# skipped by a small lexer rather than a grep, because Korean is all through them on purpose.
hangul="$(python3 - "$SRC" <<'PY'
import os, re, sys

HANGUL = re.compile(r"[\u1100-\u11ff\u3130-\u318f\uac00-\ud7a3]")

def literals(s):
    """(line, text) of every string and char literal in Kotlin source [s], templates included."""
    out, i, n = [], 0, len(s)

    def code(i, depth):
        # Scans code until the brace that closes a ${...} template (depth 1), or the end.
        while i < n:
            if s.startswith("//", i):
                j = s.find("\n", i)
                i = n if j < 0 else j
            elif s.startswith("/*", i):
                nest, i = 1, i + 2
                while i < n and nest:
                    if s.startswith("/*", i): nest, i = nest + 1, i + 2
                    elif s.startswith("*/", i): nest, i = nest - 1, i + 2
                    else: i += 1
            elif s.startswith('"""', i):
                i = string(i + 3, raw=True)
            elif s[i] == '"':
                i = string(i + 1, raw=False)
            elif s[i] == "'":
                m = re.match(r"'(\\.[^']*|[^'\\])'", s[i:i + 10])
                if m:
                    out.append((i, m.group(0)))
                    i += len(m.group(0))
                else:
                    i += 1
            elif s[i] == "{" and depth:
                depth, i = depth + 1, i + 1
            elif s[i] == "}" and depth:
                depth, i = depth - 1, i + 1
                if depth == 0:
                    return i
            else:
                i += 1
        return i

    def string(i, raw):
        start, text = i, []
        while i < n:
            if raw and s.startswith('"""', i):
                # A raw string may end in extra quotes: """a"""" is a" .
                while s.startswith('""""', i): text.append('"'); i += 1
                out.append((start, "".join(text))); return i + 3
            if not raw and s[i] == '"':
                out.append((start, "".join(text))); return i + 1
            if not raw and s[i] == "\\":
                text.append(s[i:i + 2]); i += 2; continue
            if s.startswith("${", i):
                i = code(i + 2, 1); continue
            text.append(s[i]); i += 1
        out.append((start, "".join(text))); return i

    code(0, 0)
    return [(s.count("\n", 0, at) + 1, text) for at, text in out]

root = sys.argv[1]
for dirpath, _, files in os.walk(root):
    for name in sorted(files):
        if name.endswith(".kt"):
            path = os.path.join(dirpath, name)
            src = open(path, encoding="utf-8").read()
            for line, text in literals(src):
                if HANGUL.search(text):
                    print(f"{os.path.relpath(path, root)}:{line}: {text}")
PY
)"
if [ -n "$hangul" ]; then
    echo "KOREAN string literals in Kotlin (move them to strings.xml):"
    echo "$hangul" | sed 's/^/  /'
    fail=1
fi

# --- 5. defined but never referenced (warning) --------------------------------------------------
# Not a failure: a string can be added a commit before the screen that reads it. But one that stays
# unread looks like the source of what the app says and is not.
xml_refs="$(grep -rho '@string/[A-Za-z0-9_]*' "$ROOT/app/src/main" --include='*.xml' | sed 's/@string\///' | sort -u || true)"
unused="$(comm -23 "$defined" <(sort -u "$referenced" <(echo "$xml_refs")))"
if [ -n "$unused" ]; then
    echo "warning: never referenced from Kotlin or XML (delete them, or use them):"
    echo "$unused" | sed 's/^/  /'
fi

if [ "$fail" -eq 0 ]; then
    echo "resources OK — $(wc -l < "$defined") strings, none duplicated, none missing"
fi
exit "$fail"
