# 프로덕션 액세스 신청

개인 계정의 새 앱은 비공개 테스트에서 **테스터 12명 이상이 14일 연속 참여**해야 프로덕션 액세스를
신청할 수 있다. 조건이 채워지면 Play Console → 대시보드에 **프로덕션 액세스 신청** 버튼이 생긴다.
검토는 보통 7일 이내(Google 안내 기준)이고, 거절되면 테스트를 더 이어 간 뒤 다시 신청한다.

승인이 나면 프로덕션은 태그 하나로 나간다(`docs/RELEASE.md`):

```bash
git tag v0.1.0 && git push origin v0.1.0
```

## 현재 상태 (2026-11-07, Console에서 확인)

- 비공개 테스트 버전 게시 ✓, 테스터 12명 참여 ✓, **14일 조건: 13일째** — 2026-11-08에 채워진다.
  테스터가 정확히 12명이라 그 사이 한 명만 빠져도 기간이 다시 시작될 수 있다. 여유로 몇 명 더 넣어 둘 것.
- 앱 콘텐츠: 주의 필요 항목 없음(전부 완료).
- Android vitals: 지난 28일(10/10~11/7) 사용자 인식 비정상 종료·ANR **0건**.
- Play 테스트 의견: 1건(10/27, Galaxy S22 Ultra, 별 5개) — 잘 동작하고, 카메라를 기울여 흉내 낸
  푸시업은 모두 세지 않았다는 내용.
- 관리형 게시는 꺼져 있어서 API로 올린 비공개 테스트 릴리스는 빠른 검사 뒤 자동으로 검토에 넘어간다.

## 신청서 답안 (2026-11-07 작성)

양식은 Console 언어로 나오며 답은 어느 언어로 써도 된다. 아래 영어 답을 붙여 넣는다.
**`[확인 필요]`는 저장소에 없는 사실이라 소유자가 고쳐서 넣는다.** 모르는 숫자를 지어 넣으면
거절 사유가 되고, 그보다 계정 신뢰도에 나쁘다.

### 1. 비공개 테스트

**테스터 모집은 얼마나 쉬웠나요?** — [확인 필요: 매우 어려움 ~ 매우 쉬움 중 하나]

**테스터 참여는 어땠나요?**

> [확인 필요: testers and how they were recruited, e.g. "friends, family and colleagues"]. During
> the closed test we shipped a new build every time something changed. Testers used the app's main mode, a camera-based bodyweight workout (pushup,
> squat, plank, pull-up, dip) of ten sets with rests, and told us which movements and camera
> placements did not count reliably on their phones. Our debug build lets a tester record the
> pose landmarks of a set and send them to us; nine recordings of real sets are now part of our
> automated tests.

**받은 피드백 요약과 수집 방법** — 아래 (2)~(4)는 저장소에 "소유자의 요청"으로 기록된 것도 있다.
테스터에게서 온 것만 남긴다 [확인 필요].

> We collected feedback through Google Play's testing feedback and [확인 필요: e.g. direct
> messages, a group chat], and asked
> testers who saw a miscount for a recording of the set. The main points: (1) the release build
> crashed the moment the camera permission was granted, which our debug builds never showed;
> (2) some movements did not count from certain camera positions — pull-ups from behind the
> camera, dips filmed from the front, pushups filmed from the head; (3) filming a plank or a
> pushup from the side needed the phone too far away in portrait; (4) testers wanted to change
> sound and rest settings during a workout, and to see whether they were improving. One tester
> reported through Play that on a Galaxy S22 Ultra it worked well and that faking pushups by
> tilting the camera was never counted.

### 2. 앱

**대상 사용자** — Adults who want to exercise at home with no equipment, especially people who
find it hard to keep a bodyweight routine going on their own. The app is in Korean.

**사용자에게 주는 가치**

> The phone's camera counts bodyweight reps on the device, with no wearable and no video ever
> leaving the phone. Each set is a short game: a ceiling comes down on a cat, and every counted
> rep pushes it back up, which turns a set of pushups into something people want to finish.
> Progress is shown as things that only grow — calories burned, personal bests per movement, a
> per-movement graph of each session — and there are no ads or in-app purchases.

**첫 해 예상 설치 수** — 0 ~ 10,000

### 3. 출시 준비

**테스트로 알게 된 것 때문에 바꾼 점**

> - Fixed the release-build crash on camera permission (an R8 rule for the pose model's logging
>   library), and made the app say the model is unavailable instead of closing if it ever fails
>   to load.
> - Retuned rep detection for pull-ups, dips and pushups from the camera positions testers
>   used, and added their recordings as regression tests.
> - Plank and pushup can now be filmed with the phone on its side, so the whole body fits from
>   1.5–2 m.
> - Added settings reachable during a workout, and a per-movement graph of each session's total
>   on the home screen.
> - Rewrote the camera permission screen to explain what helps counting (whole body in frame,
>   light in front, one person in view), and redesigned the look of the app.

**프로덕션 준비가 됐다고 판단한 근거**

> Every tester-reported counting problem we could reproduce from a recording is fixed and covered
> by an automated test that replays the recording at its own frame rate and at half and a third
> of it. The release
> build is signed, minified and installed through Play on testers' phones, Android vitals shows no
> user-perceived crashes or ANRs over the last 28 days of the test, and every build goes through our automated
> tests before it reaches testers.

## 신청 전 확인

- 대시보드의 비공개 테스트 조건이 둘 다 체크됐는지 (테스터 12명 이상, 14일 연속)
- 정책 → 앱 콘텐츠의 항목이 모두 완료인지 (개인정보처리방침, 데이터 보안, 콘텐츠 등급, 타겟층,
  광고 없음, 건강 앱 신고)
- 남은 `[확인 필요]`(모집 난이도, 모집 방법, 피드백 채널, (2)~(4) 중 테스터 발 피드백)를 채웠는지
