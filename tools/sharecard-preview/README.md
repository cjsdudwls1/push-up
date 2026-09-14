# Art preview

Renders `ShareCardRenderer` and the Play feature graphic to PNG on a plain JVM, so they can be
reviewed by looking at them rather than by reading coordinates.

```bash
scripts/render-art.sh [output dir]   # default: build/share-cards
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

`FeatureGraphic.kt` is the 1024x500 Play banner. It lives here rather than in `:app` because
nothing in the app draws it — it exists only to be uploaded to the Console. The committed copy is
`docs/store-assets/feature-graphic.png`.

Adding a card variant means adding it to `Preview.kt`. Prefer values that break the layout — the
longest dungeon name in the content table, a six-digit score — over ones that flatter it.
