# Release checklist

What is genuinely missing before this can go on the Play Store, and what is already handled.

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
A hosted privacy policy URL is required, and `SettingsScreen`'s `onOpenPrivacy` is currently an
empty lambda waiting for it.

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
- CI builds a debug APK on every push; the release workflow produces a signed AAB from a tag.

## Known gaps, in the order they matter

1. **The Android module has never been compiled.** Google's Maven was unreachable in the environment
   this was built in, so `:app` was written and then reviewed against the pinned library versions
   rather than built. That review found and fixed 32 issues, including a missing theme resource that
   would have failed the resource link outright, a `drawText` import from the wrong package, three
   thread races around the camera and the landmarker, and a timestamp unit mismatch that would have
   made MediaPipe reject frames under load. Expect a first real compile to surface more. `:core` is
   fully compiled and its 107 tests pass.
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
