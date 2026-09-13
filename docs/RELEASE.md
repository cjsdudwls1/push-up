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

The Data Safety form should say: camera access is used for on-device pose detection; no video,
image or biometric data is collected, stored or transmitted; no data leaves the device. That is
true of the code as written — `PoseLandmarkerSource` is the only consumer of camera frames and it
produces coordinates, which never leave the process. Keep it true.

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
2. **No character art or animation.** The launcher icon is a placeholder vector, and the player and
   enemy are not drawn at all yet — the battle screen shows the HUD over the camera. The design work
   for a cutout puppet rig is sketched in the notes but none of it is built. This is the largest
   visible gap against the demo.
3. **No audio.** The design calls for audio to be the primary feedback channel — the user often
   cannot look at the screen mid-rep — and there is currently none. `AlertKey` and the `RepEvent`
   stream are the hooks it would attach to.
4. **Squat is configured but unproven.** `DetectorConfig.squat()` exists and the state machine is
   shared, but the squat depth signal still needs its own derivation — reusing the pushup ratio for
   a standing movement is a guess. The plank is fully implemented and tested, and is what the ward
   and the boss-ultimate fallback depend on. Consider shipping pushup and plank only.
5. **Every detection constant is reasoned, not measured.** They come from anthropometry and
   projection geometry, tested against synthetic traces. Before a public release, record real
   sessions on four or five different phones and re-tune. A trace recorder and a replay harness are
   the first thing to build after the first compile.
6. **No crash reporting or analytics.** Add before any real user sees this.
