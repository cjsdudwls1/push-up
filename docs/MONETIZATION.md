# 수익화 검토: 혼합형 추천안

> **2026-09-26부터 앱은 테스트 기간 내내 전부 무료입니다** — 구독, 무료 체험, 결제, 광고가 모두
> 없습니다([DECISIONS.md](DECISIONS.md)). 아래 어느 안도 아직 채택되지 않았고, 수익 모델은 테스트
> 기간 중에 정합니다. 분석은 그때 쓰려고 그대로 둡니다.

2026-09-24 기록. **결정이 아니라 검토 결과입니다.** 이 안을 채택하면 DECISIONS.md를 고치고 아래
"바꿔야 할 것"을 함께 처리합니다. 아래의 "현재 계획", "현행", "기존"은 그때 확정돼 있던 모델(첫 던전과
고냥이 모드 무료, 나머지는 구독 + 무료 체험 7일, 광고 없음)을 말합니다.

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

## 2026-09-26 검토: 배너만

"전부 무료 + 메뉴 화면 아래 배너 하나"를 따로 검토했습니다. 소유자는 그 대신 광고 없이 전부 무료로
가기로 했고, 아래는 나중에 광고를 다시 볼 때를 위한 요약입니다.

**배너를 붙일 수 있는 곳은 메뉴 네 화면뿐입니다:** 홈, 던전 선택, 기록, 설정. 스크롤 밖 화면 맨 아래에
고정하고, 높이를 로드 전에 확보하고, 맨 아래 버튼과 간격을 두고, 한 화면에 하나만. 허용 목록은
`Routes`에 한 번만 정의해서, 새로 만드는 카메라 화면에는 기본으로 광고가 붙지 않게 합니다.

**붙이면 안 되는 곳:**
- 카메라가 켜진 화면 전부(전투, 고냥이와 튜토리얼, 그 끝 카드). 사용자는 1-2m 밖 바닥에 있어서 광고를
  보지 않고, 게임 플레이 화면의 배너는 AdMob도 비권장합니다. 광고 WebView는 숨겨도 돌면서 MediaPipe와
  자원을 다투니, 카메라 화면에서는 AdView를 멈추거나 없애고 로드도 하지 않습니다.
- 결과 화면. 판이 끝난 순간 사용자는 아직 바닥에 있고, 자동 진행 휴식은 멀리서 듣는 화면이며, 아래쪽에
  버튼 다섯 개가 몰려 있습니다.
- 운동 고르기. 가장 많이 누르는 "시작" 버튼이 스크롤하면 화면 아래에 옵니다. 탐색 버튼 바로 옆 배너는
  AdMob이 오클릭 사례로 명시한 구현입니다.

**매출: MAU 1만 명당 월 약 ₩5만 (범위 ₩2만-27만).** 위 세 안의 1/6-1/17입니다.
- 가정: DAU/MAU 20%, 하루 한 판으로 월 6만 세션(위와 같음). 배너를 붙일 수 있는 화면에 머무는 시간이
  세션당 20-40초라 새로고침(60초)으로는 거의 늘지 않고, 노출은 화면에 들어갈 때 나옵니다: 세션당 1.5회
  (1-3회), 채움률 90%.
- 한국 안드로이드 배너 eCPM은 $0.5 (범위 $0.25-1.17). 보고서는 게임·미디에이션 기준이라 AdMob 단독
  피트니스 앱에 맞게 낮췄고, `AD_ID`를 막은 채 비개인 광고로 가면 더 낮습니다.
- 기준 6만 × 1.5 × 0.9 × $0.5/1000 ≈ $40 ≈ ₩5.4만. 낮게 6만 × 1.0 × 0.8 × $0.25/1000 ≈ ₩1.6만.
  높게(결과 화면까지 넣고 eCPM $1.17) 6만 × 3 × 0.95 × $1.17/1000 ≈ ₩27만.
- AdMob 지급 기준이 $100라서, 기준 추정대로면 두세 달에 한 번 들어옵니다.

**그래도 광고를 넣는다면:**
- 비공개 테스트 빌드에는 실광고를 넣지 않습니다. 테스터는 가족·친구라 도와주려고 광고를 누르기 쉽고,
  그건 AdMob 계정 정지 사유입니다. 계정은 사실상 한 사람에 하나라 되찾기 어렵습니다. SDK를 미리
  시험하려면 샘플 광고 단위나 테스트 기기로 합니다.
- 수익이 필요해지면 구독으로 돌아가기보다 옵트인 보상형 한 곳(고냥이가 끝난 뒤 "한 판 더", 보상 상자
  2배 — XP는 렙마다 주는 원칙 그대로)이나 일회성 "광고 제거·후원" 상품(₩3,900-5,900)을 먼저 봅니다.
  둘 다 한국의 체험·자동 갱신 동의 규제와 무관하고, 한국은 일회성 결제 선호가 큽니다.
- `app-ads.txt`는 도메인 루트(`https://cjsdudwls1.github.io/app-ads.txt`)에 있어야 크롤러가 찾습니다.
  사이트가 프로젝트 페이지(`/push-up/`)라서, 사용자 사이트 저장소 `cjsdudwls1/cjsdudwls1.github.io`를
  따로 만들어야 합니다.
- 개인정보처리방침 12항은 불리한 변경을 시행 7일 전부터 공지한다고 약속합니다. 광고가 들어간 버전을
  내기 7일 전에 방침(광고 수집 항목, 처리위탁, 6항의 "광고 없음")을 먼저 게시합니다.

eCPM과 AdMob 정책은 2026-09-26에 검색 결과 요약으로만 확인했습니다(원문을 열지 못함). 검증되지 않은
수치이니 원문으로 다시 확인하세요.

## 채택하면 바꿔야 할 것

- `site/privacy.html` — 지금은 "광고 없음, 광고 식별자 미사용"이라고 적혀 있습니다.
- `docs/STORE_LISTING.md` — "Contains ads: No".
- `docs/DECISIONS.md` — "구독: 없음", "무료 체험: 없음", "광고: 없음".
- Play Console 데이터 보안 양식, `AD_ID` 권한, 해외 출시 때 동의 화면(UMP).
- 결제가 들어가는 안이면: Play Billing은 2026-09-26에 앱에서 뺐으므로 다시 넣고, Play Console 상품,
  구매 전 고지(갱신 주기·가격·해지 방법), `site/terms.html`의 결제·환불 조항, `site/privacy.html`의
  결제 항목을 함께 되살립니다. 약관 3항은 유료 기능이나 광고를 시행 전에 알린다고 약속합니다.

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

2026-09-26 배너 검토 (전부 검색 결과 요약으로만 확인):

- Tenjin Ad Monetization Benchmark Report 2026 (한국 안드로이드 배너 $1.17, 2024 Q1 데이터): https://tenjin.com/blog/ad-mon-gaming-2026/
- Appodeal eCPM Report 2025 (2024 Q4, 배너는 두 플랫폼 모두 $1 미만): https://appodeal.com/wp-content/uploads/2025/03/Appodeal-The-Latest-eCPM-Report-2025.pdf
- 클리앙 '애드몹 이야기' (개발자 경험담, 날짜 미확인): https://www.clien.net/service/board/cm_app/18252714
- AdMob 비권장 배너 구현 (탐색 버튼 옆, 게임 플레이 화면, 스크롤과 함께 움직이는 배너): https://support.google.com/admob/answer/6275345
- AdMob 구현 가이드 (광고 공간 미리 확보): https://support.google.com/admob/answer/2936217
- AdMob 배너 새로고침: https://support.google.com/admob/answer/3245199
