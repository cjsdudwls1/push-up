# 수익화 검토: 혼합형 추천안

2026-09-24 기록. **결정이 아니라 검토 결과입니다.** 현재 확정된 모델은 여전히
[DECISIONS.md](DECISIONS.md)의 "구독 + 무료 체험 7일, 광고 없음"이고, 코드와 개인정보처리방침도
그 기준입니다. 이 안을 채택하면 DECISIONS.md를 고치고 아래 "바꿔야 할 것"을 함께 처리합니다.

## 결론

**무료 + 보상형 광고 + 광고 제거 구독(혼합형)을 추천합니다.** 안드로이드 전용, 한국 먼저라는
조건에서 구독만 하는 안이 셋 중 가장 적게 벌 가능성이 높습니다. 광고만 하는 안과 혼합형은
초기 매출이 비슷하지만, 혼합형은 결제할 사람을 놓치지 않고 전면광고를 적게 써서 재방문율 손실도
작습니다.

## 근거

### 구독

- 건강·피트니스 앱의 35일 다운로드→결제 전환율은 중앙값 2.7%입니다. 첫 콘텐츠를 무료로 주는
  구조는 2.1%, 하드 페이월은 10.7%입니다.
- 건강·피트니스 전환율은 iOS가 안드로이드의 약 2배입니다. 안드로이드는 사용자의 약 70%지만 구독
  매출은 약 15%입니다.
- 체험→결제 전환율 중앙값 39.9%, 거래의 68%가 연간 구독, 연간 RLTV 중앙값 $35.64.
- **한국 변수가 가장 큽니다.**
  - 2025-02-14부터 무료체험·할인이 유료로 넘어가기 전에 사용자 동의가 없으면 구독이 자동
    해지됩니다. 시행 직후 RevenueCat은 Play의 한국 체험 전환율이 "거의 0"까지 떨어졌다고
    관측했습니다.
  - 2026-02-03부터는 앱 자체 동의 화면이 아니라 Play의 동의 절차만 쓸 수 있습니다.
  - 그래서 현재 계획(7일 체험, LAUNCH.md의 체험→결제 25% 목표)이 한국에서는 가장 불리한
    구조입니다.
- 수수료는 구독 15%. 한국은 2026-12-31 전후로 서비스 10% + Play 결제 5%로 나뉘지만 합계는 같습니다.

### 광고

- 한국 안드로이드 eCPM은 세계 최상위권입니다. 보상형 $11.23(Appodeal, 2024 Q4)~$20.94(Tenjin,
  2024 Q2), 전면 $17.8(Tenjin, 2024 Q2), 배너 $1 미만.
- 대가: 방해가 되는 광고 경험은 이탈을 6-7% 올리고, 보상 화면 위에 뜨면 이탈이 3배가 됩니다
  (Deloitte·AdMob, 게이머 7,000명). 광고 SDK는 메모리를 평균 25%, CPU를 중앙값 7.4% 더 쓰는데,
  카메라 위에서 MediaPipe가 도는 이 앱에는 바로 경쟁하는 자원입니다.

### 비슷한 앱

| 모델 | 앱 |
|---|---|
| 광고 + 광고 제거 구독 | Leap Fitness Home Workout |
| 광고 + 24시간/월/연 VIP | Just Dance Now |
| 구독 | Zombies, Run! $6.99/월·$49.99/년 · Seven $9.99/월·$79.99/년 · 플랜핏 ₩11,900-13,900/월·₩69,900/년 |
| 완전 무료 | Nike Training Club(마케팅 예산), Active Arcade(투자금) — 인디가 따라 할 수 없는 구조 |
| 보상형 광고 | 캐시워크: 2023년 매출 약 1,056억 원, 영업이익 125억 원 |

## MAU 1만 명당 월 매출 추정

가정: DAU/MAU 20%(DAU 2,000명, 하루 한 판), ₩1,340/$, eCPM은 보고서보다 낮게(보상형 $8, 전면 $5,
배너 $0.4), 구독 월 ₩4,900 / 연 ₩33,000, 연간 68% → 구독자 1명당 월 순매출 약 ₩2,900.

| 안 | 가정 | 기준 | 범위 |
|---|---|---|---|
| 구독만 | MAU의 1% 결제 (100명) | 약 ₩29만 | ₩15만-58만 |
| 광고만 | DAU당 보상형 0.7 + 전면 0.7 + 배너 4회 → 약 ₩14 | 약 ₩86만 | ₩40만-170만 |
| 혼합 | 구독 ₩29만 + 무료 DAU 1,900 × ₩9.5 (보상형 0.7, 전면 0.3) | 약 ₩83만 (결제 1.3%면 ₩92만) | ₩45만-190만 |

구독만으로 광고를 이기려면 MAU의 약 3% 이상이 결제해야 하는데, 한국 안드로이드에서는 상위권이어야
가능한 수준입니다. 방향을 보는 추정이고, 가장 크게 흔들리는 변수는 DAU/MAU와 결제율입니다.

## 추천 구성

**무료**
- 고냥이 지켜줘 + 첫 던전 영구 무료(현행 유지). 운동 자체는 잠그지 않습니다.
- 보상형 광고 세 곳:
  1. 고냥이 게임이 끝난 뒤 "30초 쉬고 한 번 더" (한 판에 1회)
  2. 잠긴 던전 **1일 이용권** (하루 2회). 한국에서 막힌 무료 체험을 대신합니다.
  3. 판이 끝난 뒤 보너스 상자 2배. XP는 렙마다 지급하는 원칙을 건드리지 않습니다.
- 전면광고: 결과를 다 본 뒤 "홈으로"를 누를 때만, 하루 1회, 설치 후 3일간은 없음.

**유료** (기존 `pushup_rpg_pro`)
- 전체 던전·보스, 난이도 선택, 광고 전부 제거.
- 월 ₩4,900 / 연 ₩33,000(월 환산 ₩2,750), 연간 기본 선택.
- **한국은 무료 체험과 할인 인트로 오퍼를 빼고**, 해외만 체험 유지(오퍼 지역 설정).
- 나중에 실험: 평생 이용권 ₩39,000 정도. 한국·일본은 일회성 결제 선호가 큽니다.

**광고를 절대 넣으면 안 되는 곳**
- 카메라가 켜진 모든 화면: 자세 안내, 보정, 세트·런 진행 중, 세트 사이 휴식. 사용자가 1-2m
  떨어져 있어 광고를 닫을 수 없고, 땀 난 손의 오클릭은 AdMob 무효 클릭 위험입니다.
- 런 시작 직전(Play Better Ads 정책이 레벨 시작 시 전면광고를 금지), 결과·보상이 처음 뜨는 순간,
  "다음엔 잡아요" 직후, 첫 튜토리얼 판, 페이월 화면, 운동 화면의 배너.

**기술**
- 광고 SDK 초기화와 로드는 백그라운드 스레드에서, 카메라를 해제한 뒤에만.
- 런 중에는 광고를 미리 불러오지 않습니다.

**도입 순서**
1. 첫 1,000명까지는 광고 없이 재방문율을 잽니다. 광고가 섞이면 측정이 오염됩니다.
2. 보상형만 붙이고 D7 재방문율을 비교합니다.
3. 전면광고는 A/B로 붙이고, D7이 2%p 이상 떨어지면 뺍니다.

## 채택하면 바꿔야 할 것

- `site/privacy.html` — 지금은 "광고 없음, 광고 식별자 미사용"이라고 적혀 있습니다.
- `docs/STORE_LISTING.md` — "Contains ads: No".
- `docs/DECISIONS.md` — "광고: 없음", "무료 체험: 7일".
- Play Console 데이터 보안 양식, `AD_ID` 권한, 해외 출시 때 동의 화면(UMP).

## 한계

- 조사 당시 원문 페이지를 직접 열 수 없어서, 수치는 아래 출처의 검색 결과 요약으로 확인한
  것입니다. 특히 eCPM은 원문으로 다시 확인하세요.
- eCPM 보고서는 게임 위주이고 미디에이션 기준입니다. AdMob만 쓰는 피트니스 앱은 더 낮을 수
  있어서 보수적으로 잡았습니다.

## 출처

- RevenueCat State of Subscription Apps 2025 · 2026: https://www.revenuecat.com/state-of-subscription-apps-2025 · https://www.revenuecat.com/state-of-subscription-apps
- RevenueCat 하이브리드 수익화: https://www.revenuecat.com/blog/growth/ai-hybrid-monetization/
- RevenueCat 한국 구독 규제: https://www.revenuecat.com/blog/growth/south-korea-subscription-rules-2025/
- Play 한국 구독 변경: https://support.google.com/googleplay/android-developer/answer/15722617 · https://support.google.com/googleplay/android-developer/answer/16514827
- 한국 수수료 변경: https://www.digitaltoday.co.kr/en/view/91887/google-cuts-google-play-fees-in-south-korea-to-as-low-as-10-percent-expands-in-app-and-external-payments
- Adapty 구독 리포트 2026: https://adapty.io/state-of-in-app-subscriptions/
- Tenjin 광고 벤치마크: https://tenjin.com/blog/ad-monetization-benchmark-report-2025-ecpm-ad-revenue/
- Appodeal eCPM (Maf.ad 요약): https://maf.ad/en/blog/mobile-ads-ecpm/
- Play Better Ads 정책: https://support.google.com/googleplay/android-developer/answer/12271244
- AdMob 전면광고 가이드: https://support.google.com/admob/answer/6066980
- 전면광고와 이탈 (Deloitte·AdMob): https://blog.playio.co/interstitial-ads-mobile-games
- 광고 SDK 성능 비용 (arXiv): https://arxiv.org/pdf/2010.16063
- Sensor Tower 건강·피트니스 2025: https://sensortower.com/blog/state-of-mobile-health-and-fitness-in-2025
- DAU/MAU 벤치마크: https://vmobify.com/blog/dau-mau-stickiness-benchmarks
- 캐시워크 매출: https://www.news1.kr/industry/sb-founded/5347235 · https://www.mt.co.kr/future/2024/03/12/2024031209585491978
- Zombies, Run!: https://support.zombiesrungame.com/hc/en-us/articles/4421009133201
- Seven: https://apps.apple.com/us/app/seven-7-minute-workout/id650276551
- Just Dance Now VIP: https://ubisoft-mobile.helpshift.com/hc/en/10-just-dance-now/section/35-vip-subscription-questions/
- 플랜핏: https://apps.apple.com/kr/app/id1511876936
