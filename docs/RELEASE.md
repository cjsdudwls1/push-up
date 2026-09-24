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
exactly as written, or `queryProductDetails` returns nothing and the paywall renders empty.

Pricing is read from `ProductDetails` and never hardcoded, so set KRW prices in the Console. Set the
Korean price by hand rather than letting Play convert from USD — converted prices land on values
that read as foreign.

### Privacy policy and Data Safety
The policy is written and hosted from `site/privacy.html`, and the app's settings row now opens
`R.string.privacy_policy_url`. Two things still have to happen by hand:

1. **Enable Pages.** Repository Settings → Pages → Source → "GitHub Actions".
   `.github/workflows/pages.yml` publishes `site/` on every push that touches it. Until this is
   switched on, `https://cjsdudwls1.github.io/push-up/privacy.html` 404s and the Play listing
   cannot be submitted.
2. **Fill in the contact address.** Section 11 of the policy carries a
   `[출시 전 문의 이메일 기재 필요]` placeholder. Play review checks that the policy names a
   contact, and a published policy with a bracketed placeholder in it is worse than no policy.
   Search the repo for that string; it appears exactly once.

The Data Safety form has to make one distinction carefully, because both halves are true:

- **Camera.** Used for on-device pose detection. No video, image, landmark or biometric data is
  collected, stored or transmitted. That is true of the code as written —
  `PoseLandmarkerSource` is the only consumer of camera frames and it produces coordinates, which
  never leave the process. Keep it true.
- **Crash logs and analytics.** Firebase Crashlytics and Analytics *do* send data: crash traces,
  and the counts, durations and enum names listed in `telemetry/Telemetry.kt`. Declare them.

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
A fitness app that instructs exercise should carry a short disclaimer — that this is not medical
advice, and to stop if something hurts. Not currently in the app. Add it to onboarding and to the
store listing.

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
