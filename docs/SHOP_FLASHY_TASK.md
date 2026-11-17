# 작업서: 꾸미기를 "갖고 싶게" — 화려한 효과와 춤추는 고양이

> **이 작업이 끝나면(머지까지) 이 파일 `docs/SHOP_FLASHY_TASK.md` 를 지우고, `CLAUDE.md` 3번째 줄 아래의
> "진행 중 작업서" 줄과 `docs/STATUS.md` 맨 위의 같은 줄도 지운 뒤 STATUS.md 에 결과를 적는다.** 지우는 것까지가
> 이 작업의 일부다.

## 왜 (소유자 말, 2026-11-17)

세션 끝에 "광고 보고 츄르 1개 더 받기"가 있는데, 츄르를 더 받고 싶은 마음이 안 든다. 츄르로 살 수 있는
꾸미기들이 매력적이지 않아서다. 멋있는 효과가 나고, 고양이가 춤을 추고, 화려하고 재미있어야 사고 싶어진다.

즉 목표는 **츄르의 가치를 올리는 것** — 상점 물건이 눈에 띄게 화려해지고, 사는 순간과 입은 뒤가 재미있어야
한다. 광고 횟수·보상량·가격 같은 숫자는 이번 작업에서 건드리지 않는다(바꾸고 싶으면 소유자에게 먼저 묻기).

## 지금 상태 (코드 위치)

- 상점 물건 20개와 가격: `core/.../progression/Shop.kt` (`ShopItem`, 4~50 츄르). 물건 종류는
  `core/.../progression/Gifts.kt` 의 `CatItem` + `WearSlot`(HEAD/FACE/NECK/TOY/SCENE).
- 꾸미기 화면: `app/.../ui/screens/WardrobeScreen.kt` — `ShopTile` 은 64dp 정지 그림 + 이름 + 가격 알약.
  살 때는 평범한 `AlertDialog` 확인뿐, 산 순간 연출 없음.
- 고양이 그림: `ui/components/CatArt.kt`(`drawCat`, `pixelThing`), 머리/얼굴/목/장난감/배경은
  `CatHeadItems.kt`·`CatFaceItems.kt`·`CatNeckItems.kt`·`CatToys.kt`·`CatScenes.kt`. 홈의 고양이는
  `CatHero.kt`(`CatPortrait`). 모두 정지 도트에 가깝다.
- 이미 있는 효과 도구: `ui/components/PixelFx.kt`(도트 파티클), `drawHearts`(CatArt.kt).
- 2배 광고 버튼: `SurvivalScreen.kt` 엔딩 카드, `PushupRpgApp.kt` 의 `onDoubleChuru`. 상점 보상형 광고는
  `WardrobeScreen` 의 `onWatchAd`. 둘 다 2026-11-17 실기기(샘플 광고)에서 정상 동작 확인됨 — 광고 흐름은 고치지 말 것.
- 그림을 PNG로 뽑아 보는 법: `scripts/render-cat.sh out/ [ITEM...]` (SDK 없이 됨). 결과 PNG를 직접 열어 보고 판단.

## 할 일 (우선순위 순)

1. **고양이 춤.** 홈(`CatPortrait`)과 꾸미기 화면 미리보기에서 고양이가 춤춘다 — 좌우 흔들기, 점프,
   꼬리 흔들기 같은 몇 가지 동작을 도트 프레임으로. 상점 물건을 입고 있으면 그 물건도 같이 움직여야 한다
   (머리·얼굴·목 아이템이 몸에 붙어서 따라감).
   - 어떤 물건(예: `STAGE`, `GYM`, `HEADPHONES`)은 입으면 **전용 춤/동작**이 생기게 해서, 비싼 물건일수록
     "이걸 사면 고양이가 이걸 한다"가 보이게.
2. **아이템별 효과.** 비싼 물건부터 상시 효과를 붙인다 — 반짝임, 음표, 별, 무대 조명, 배경의 움직임
   (`PARK` 나뭇잎, `BEACH` 파도, `STAGE` 스포트라이트 등). `PixelFx` 재사용. 싼 물건은 작은 효과, 비싼 물건은 큰 효과.
3. **상점 타일을 살아 있게.** `ShopTile` 그림이 정지가 아니라 효과와 함께 움직이게(미리보기). 아직 못 사는
   물건도 "이걸 사면 이렇게 된다"가 보여야 한다. 타일을 누르면 큰 미리보기(고양이가 그 물건을 입고 춤추는
   모습)를 띄우고, 거기서 사기 — 지금의 맨 `AlertDialog` 대신.
4. **사는 순간 연출.** 구매하면 폭죽/츄르 터짐 + 고양이 신남 반응 + 효과음(기존 칩튠 효과음 계열, `res/raw`).
5. **엔딩 카드의 2배 버튼이 츄르로 뭘 살 수 있는지 상기**시키게: 예를 들어 "다음 물건까지 츄르 N개" 같은
   한 줄(계산은 core 에, 문구는 strings.xml). 과장된 문구나 강요는 금지 — 사용자가 고르는 광고라는 원칙 유지.

## 지켜야 할 것

- `CLAUDE.md` 의 "Rules that are load-bearing" 전부와 `docs/STATUS.md` 의 소유자 고정 원칙(해요체, 문구는 전부
  strings.xml, "실패" 금지, 모양은 `PixelShape`/`PixelCircle` 만, Firebase·로그 수집 금지, 개인정보 커밋 금지).
- 도트 그림체 유지(안티에일리어싱 없는 픽셀). 외부 이미지·음원을 넣는다면 상업적 이용·재배포 가능한 라이선스만,
  애매하면 소유자에게 묻는다. 직접 그리는(코드 도트) 쪽을 우선.
- 성능: 홈·꾸미기 화면 애니메이션이 운동 화면(카메라+포즈 추정)에 영향을 주면 안 된다. 운동 중에는 춤 연출을
  멈추거나 가볍게.
- 접근성: 설정의 "움직임 줄이기"(시스템 animator duration scale 0 포함) 시 춤·효과를 끈다.
- 규칙으로 표현되는 것(어떤 물건이 어떤 춤을 여는지, 다음 물건까지 츄르 수)은 `core/` 에 두고 테스트를 붙인다.

## 확인

- `scripts/test-core.sh`, `scripts/check-resources.sh` 통과.
- `scripts/render-cat.sh` 로 물건별 PNG를 뽑아 직접 확인(가능하면 춤 프레임 몇 장).
- `:app` 빌드는 SDK 가 되면 로컬에서, 안 되면 PR CI 로 확인.
- 실기기 판정은 소유자 몫: 머지하면 내부·비공개 트랙으로 배포되니, 무엇을 보면 되는지(꾸미기 화면, 홈 고양이,
  구매 연출) 짧게 적어 소유자에게 남긴다. `distribution/whatsnew/whatsnew-ko-KR` 도 고친다.

## 끝내는 법

1. 작업 브랜치 → `claude/pushup-rpg-app-2xnr4o` 로 PR, CI 통과 후 머지.
2. **같은 PR 안에서** 이 파일 삭제 + `CLAUDE.md`·`docs/STATUS.md` 의 작업서 안내 줄 삭제 + STATUS.md 에 결과 기록.
