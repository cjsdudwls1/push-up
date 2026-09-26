# Release checklist

What is genuinely missing before this can go on the Play Store, and what is already handled.

Getting builds onto a phone through Play's internal test track, step by step: see
[PLAY_INTERNAL_TEST.md](PLAY_INTERNAL_TEST.md). Once the upload key and a Play service account are
in the repository secrets, every push to the install branch is signed and uploaded there.

## Blocking — nothing ships without these

### Signing
- Generate a release keystore and keep it out of the repo (`.gitignore` already excludes `*.jks`,
  `*.keystore` and `keystore.properties`).
- Store it base64-encoded in the `RELEASE_KEYSTORE_BASE64` repository secret along with
  `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS` and `RELEASE_KEY_PASSWORD`.
  `.github/workflows/release.yml` reconstructs `keystore.properties` from them and deletes it after.
- Enrol in Play App Signing. Back the upload key up somewhere that is not this repository.

### Play Console products
`app/src/main/kotlin/com/pushuprpg/app/billing/BillingProducts.kt` names one subscription product
with two base plans and a 7-day trial offer on each. Those ids must be created in the Play Console
exactly as written, or `queryProductDetails` returns nothing and the paywall has no plan to sell:
it says it cannot load the subscription and offers 다시 시도, which cannot help until the products
exist.

Pricing is read from `ProductDetails` and never hardcoded, so set KRW prices in the Console. Set the
Korean price by hand rather than letting Play convert from USD — converted prices land on values
that read as foreign.

### Privacy policy and Data Safety
The policy is written and hosted from `site/privacy.html`, and the app's settings rows open
`R.string.privacy_policy_url` and `R.string.terms_url`. The contact address in section 11 of the
policy, and in the terms, is cjsdudwls1357@gmail.com.

- **Pages is on.** Repository Settings → Pages → Source was switched to "GitHub Actions" on
  2026-09-26, and `https://cjsdudwls1.github.io/push-up/privacy.html` (the URL the Play Console
  asks for) and `terms.html` beside it resolve. `.github/workflows/pages.yml` publishes `site/` on
  every push that touches it.
- **Pages deploys from one branch.** Both the workflow's `branches:` and the `github-pages`
  environment's allowed deployment branches name `claude/pushup-rpg-app-2xnr4o`, so merging
  `site/` into another branch, or renaming or deleting that one, stops the site updating until
  both are changed together.

The Data Safety form has to make one distinction carefully, because both halves are true:

- **Camera.** Used for on-device pose detection. No video, image, landmark or biometric data is
  collected, stored or transmitted. That is true of the code as written —
  `PoseLandmarkerSource` is the only consumer of camera frames and it produces coordinates, which
  never leave the process. Keep it true.
- **Crash logs and analytics.** Firebase Crashlytics and Analytics *do* send data: crash traces,
  the counts, durations and enum names listed in `telemetry/Telemetry.kt`, and the events and
  device values Analytics records on its own (listed under the table in section 4-2 of the
  policy). Declare them. Advertising ID collection is switched off in the manifest and the
  `AD_ID` permission removed, so the answer to the advertising ID question is no.

Do not let the second collapse the first in the privacy policy. "카메라 영상은 어디로도 전송되지
않아요" stays, and the crash/analytics collection is stated separately. Conflating them either
overstates what is collected or understates it, and both are worse than the truth.

Firebase is applied only when `app/google-services.json` exists, so the project builds without it.
Generate it in the Firebase console with the application id `io.github.cjsdudwls1.pushuprpg`; the
file is gitignored.

### Content rating and category
A fitness app with combat and a subscription. Complete the IARC questionnaire honestly; the game
framing means it is likely to be rated for a general audience but the questionnaire decides.

Korean distribution of anything rated as a game involves 게임물관리위원회 classification. Confirm
which category this falls into before submitting — the answer differs depending on whether the
store listing presents it as a game or as a fitness app, and getting it wrong delays the release.

### Korean subscription disclosure
전자상거래법 and Play's own policy both require the renewal period, the price and how to cancel to
be visible *before* purchase. `PaywallScreen` renders all three (`renewalSummary` plus the
`paywall_manage` string). Keep them there and keep them legible; do not move them behind a link.

### Health disclaimer
Done: the first onboarding screen says it is not a medical device, to stop if something hurts and to
ask a doctor first with an illness or injury (`onboarding_health`); settings opens the terms, which
carry the full notice; and the store listing ends with it. Keep the three saying the same thing.

## Already handled

- `targetSdk 36`, which is Play's requirement for new apps from 2026-08-31.
- Play Billing 9.1.0, above the v8 floor that took effect 2026-08-31.
- R8 rules for MediaPipe, Room and kotlinx.serialization (`app/proguard-rules.pro`).
- Backup rules that name the real DataStore paths and exclude the entitlement cache.
- CI builds a debug APK on every push, and the minified release bundle (unsigned) so a missing R8
  keep rule fails on the push that caused it. The release workflow signs a bundle from a tag or a
  push to the install branch, and uploads it to the internal track when `PLAY_SERVICE_ACCOUNT_JSON`
  is set.

## Known gaps, in the order they matter

1. **The Android module now builds, and has never run.** `:app` compiles in CI and produces a debug
   APK; what it has never done is start on a phone. Everything about the camera, the landmarker and
   the detector thresholds is still unverified against real hardware and a real body.

   Getting it to build took five rounds, and only the last was about the code:
   `platforms;android-37.0` carries a minor suffix that `platforms;android-37` does not; AGP 9
   compiles Kotlin itself and rejects `org.jetbrains.kotlin.android` outright; Firebase stopped
   publishing its `-ktx` artifacts, so the BOM resolved them with an empty version; AndroidX refuses
   to be consumed below compileSdk 37. Then the compiler saw all 46 files and found six errors —
   two undeclared DataStore keys, and `"$period마다"`, which parses as one identifier because Hangul
   is a valid Kotlin identifier character.

   The earlier 32-issue review still did its job: a missing theme resource that would have failed
   the resource link, a `drawText` import from the wrong package, three thread races around the
   camera and the landmarker, and a timestamp unit mismatch that would have made MediaPipe reject
   frames under load — none of which a compiler would have caught anyway.
2. **Character art is geometric, not illustrated.** Fighters and monsters are drawn from shapes on
   a Compose canvas, with a wind-up blend that moves the avatar in step with the user and a
   three-hit attack string that varies by depth and crit. It works and it is consistent, but it is
   not the commissioned sticker art the demo showed. Replacing it later means swapping
   `CharacterArt.kt` and `MonsterArt.kt`; nothing else reads them.
3. **Audio is synthesised.** Fifteen cues from `tools/generate_sfx.py`, deliberately split into a
   dry high band for form and a wet low band for impacts. Adequate and coherent; a sound designer
   would do better. There is no music and no voice.
4. **Every detection constant is reasoned, not measured.** They come from anthropometry and
   projection geometry, tested against synthetic traces. Before a public release, record real
   sessions on four or five different phones and re-tune. A trace recorder and a replay harness are
   the first thing to build after the first compile — `PoseTrace` and `TraceReplay` in `:core` are
   already there for it; what is missing is the Android side that writes one and a way to send it.
5. **No instrumentation tests.** `:core` has 140 unit tests; `:app` has none, and cannot be tested
   here at all. Compose UI tests and a Room migration test are the obvious first additions once the
   module builds.
