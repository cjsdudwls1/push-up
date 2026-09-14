# Share card preview

Renders `ShareCardRenderer` to PNG on a plain JVM, so the card can be reviewed by looking at it
rather than by reading coordinates.

```bash
scripts/preview-share-card.sh [output dir]   # default: build/share-cards
```

`:app` cannot be compiled in every environment this repo is worked on (Google's Maven is
unreachable from some of them), and the share card is the one surface whose entire job is to look
right to someone who has never opened the app. So the script mirrors the real renderer — the same
file that ships, not a copy — into a standalone Gradle build next to a Java2D-backed
`android.graphics` shim.

**This is a proof of composition, not a pixel-exact emulator.** Java2D and Skia agree on the model
(device pixels, ARGB, fill/stroke, gradients, baselined text) but not on hinting, gradient
interpolation, or font selection: Android picks Noto Sans CJK KR and this picks whatever the machine
has. Set `PUSHUP_PREVIEW_FONT` to a Hangul-capable family to get closer.

Layout bugs, clipping, colour and hierarchy all show up here. Do not trust it for letter-exact
metrics.

`R.kt` and the string table are generated from the real `strings.xml` on every run, so a renamed
string breaks this build instead of silently rendering stale copy.

Adding a card variant means adding it to `Preview.kt`. Prefer values that break the layout — the
longest dungeon name in the content table, a six-digit score — over ones that flatter it.
