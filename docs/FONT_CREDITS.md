# 글꼴 출처

앱에 들어간 도트 글꼴은 모두 SIL Open Font License 1.1이에요. 라이선스 전문은 `docs/fonts/`에 있어요.
OFL은 앱에 넣어 배포해도 되고, 글꼴 파일만 따로 팔지 않으면 돼요.

| 파일 | 글꼴 | 출처 | 가공 |
|---|---|---|---|
| `res/font/galmuri11_regular.ttf` | Galmuri11 (© 2019–2025 Lee Minseo) | [quiple/galmuri v2.40.4](https://github.com/quiple/galmuri/releases/tag/v2.40.4) | 아래처럼 글자를 줄임 |
| `res/font/galmuri11_bold.ttf` | Galmuri11 Bold (같음) | 같음 | 아래처럼 글자를 줄임 |
| `res/font/silkscreen_regular.ttf` | Silkscreen Regular (© 2001 The Silkscreen Project Authors) | [google/fonts `ofl/silkscreen`](https://github.com/google/fonts/tree/main/ofl/silkscreen) | 그대로 |

글자는 거의 다 Galmuri11(숫자·제목은 굵게)이고, 운동 화면의 라틴 글자만 쓰는 표시(콤보 `x12` 같은 것)는 Silkscreen이에요(`Type.kt`). Galmuri11은
11픽셀 격자로 그려진 글꼴이라 11의 배수 크기에서 가장 또렷해요.

**줄인 방법.** fontTools `pyftsubset`으로 기본 라틴 글자, 한글 자모, KS X 1001 한글 2,350자, 몇 가지
기호, 그리고 `strings.xml`에 나오는 모든 글자만 남겼어요(`--layout-features='*'`). 여기에 없는
글자(고양이 이름에 쓴 드문 음절 같은 것)는 폰 기본 글꼴로 보여요. `strings.xml`에 새 글자가 들어가면
같은 방법으로 다시 줄여야 해요.
