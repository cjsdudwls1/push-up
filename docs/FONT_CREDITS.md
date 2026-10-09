# 글꼴 출처

앱에 들어간 글꼴 두 개 모두 SIL Open Font License 1.1이에요. 라이선스 전문은 `docs/fonts/`에 있어요.
OFL은 앱에 넣어 배포해도 되고, 글꼴 파일만 따로 팔지 않으면 돼요.

| 파일 | 글꼴 | 출처 | 가공 |
|---|---|---|---|
| `res/font/orbit_regular.ttf` | Orbit Regular (© 2022 The Orbit Project Authors) | [google/fonts `ofl/orbit`](https://github.com/google/fonts/tree/main/ofl/orbit) | 아래처럼 글자를 줄임 (784 KB → 598 KB) |
| `res/font/gowun_dodum_regular.ttf` | Gowun Dodum Regular (© 2021 The Gowun Dodum Project Authors) | [google/fonts `ofl/gowundodum`](https://github.com/google/fonts/tree/main/ofl/gowundodum) | 아래처럼 글자를 줄임 (7.2 MB → 1.2 MB) |

**줄인 방법.** fontTools `pyftsubset`으로 기본 라틴 글자, 한글 자모, KS X 1001 한글 2,350자, 몇 가지
기호, 그리고 `strings.xml`에 나오는 모든 글자만 남겼어요(`--layout-features='*'`). 여기에 없는
글자(고양이 이름에 쓴 드문 음절 같은 것)는 폰 기본 글꼴로 보여요. `strings.xml`에 새 글자가 들어가면
같은 방법으로 다시 줄여야 해요.
