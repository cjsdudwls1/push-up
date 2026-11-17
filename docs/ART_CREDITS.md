# 그림 출처

고양이 지켜줘 화면의 픽셀 효과(타격, 깊게, 콤보, 30초, 신기록, 꽃가루, 하트 잃음)는
[patch-gg/pixelfx](https://github.com/patch-gg/pixelfx)(커밋 `044e12a`)로 만든 스프라이트 시트예요.
MIT 라이선스이고, 전문은 `docs/pixelfx-LICENSE.txt`에 있어요(© 2026 Patch, © 2026 Hugo Duprez).
글자는 저장소 코드 안의 도트 글꼴(`src/core/font.js`, MIT)로 그려졌어요. 갤러리 전용 글꼴
Patch Pixel(MIT 아님)은 쓰지 않았어요.

| 파일 | 가공 |
|---|---|
| `res/drawable-nodpi/fx_*.webp` | 30fps 프레임을 하나 걸러 남기고(타격은 그대로), 모든 프레임에 공통인 빈 여백을 잘라 무손실 WebP로 다시 묶음 |

시트 크기·프레임 수·기준점은 `ui/components/PixelFx.kt`의 `Sprite`에 있어요. 신기록·꽃가루는 커서
(펼치면 11 MB·4.5 MB) 나올 때만 읽고 끝나면 놓아요.
