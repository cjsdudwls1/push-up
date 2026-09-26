# 작업서: 실제 고로롱 녹음과 AI 음성 파일 넣기

브라우저와 인터넷이 되는 로컬 Claude Code용입니다. 클라우드 작업 환경에서는 음원 사이트와 TTS
서비스, 음성 모델 다운로드가 막혀 있어서 이 두 가지만 남았습니다. **Kotlin 코드는 건드리지
않습니다** — 앱은 이미 파일이 있으면 그걸 틀고, 없으면 폰 TTS로 읽게 되어 있습니다.

## 0. 준비

```bash
git fetch origin
git checkout claude/job-switch-dark-mode-ip6vad
git pull --ff-only origin claude/job-switch-dark-mode-ip6vad
python3 --version            # 3.10+
ffmpeg -version              # 없으면: pip install imageio-ffmpeg 후 그 경로의 ffmpeg 사용
```

지켜야 할 것:
- **라이선스가 애매하면 넣지 않고 사용자에게 묻습니다.** 이 앱은 Google Play에서 유료 구독으로
  팔릴 예정이라, 상업적 이용이 허용되고 앱에 포함해 재배포할 수 있는 것만 씁니다.
- **로그인, 계정 생성, 결제 수단 등록이 필요하면 진행 전에 사용자에게 묻습니다.**
- 각 사이트의 약관은 지금 시점의 것을 브라우저로 직접 확인합니다. 이 문서의 서비스 이름은 예시일
  뿐, 약관이 바뀌었을 수 있습니다.
- 강제 푸시 금지.

## 1. 고로롱: 실제 녹음으로 교체

**교체할 파일:** `app/src/main/res/raw/sfx_cat_purr.ogg` (지금은 `tools/generate_sfx.py`로 합성한
소리)

**찾는 순서:**
1. Wikimedia Commons: "cat purring" 오디오. CC0 또는 퍼블릭 도메인이 먼저, 그다음 CC BY / CC BY-SA.
2. Freesound: 라이선스 필터 CC0. 원본 다운로드에는 로그인이 필요하니 사용자에게 묻습니다.
3. Pixabay 효과음: 현재 Content License가 앱 포함·상업 이용을 허용하는지 확인한 뒤.

**쓰면 안 되는 것:** NC(비영리), ND(변경 금지 — 자르고 음량 맞추는 것도 변경입니다), 출처를 알 수
없는 것, YouTube 등에서 추출한 것.

**고르는 기준:**
- 집고양이의 편안한 고로롱. 가르랑거리는 위협음이나 울음이 아닌 것.
- 가까이서 녹음돼 숨소리 질감이 들리는 것. 사람 목소리, TV, 음악, 에어컨 소음이 없는 것.
- 깨끗한 구간이 3초 이상.
- 가능하면 미리보기 mp3가 아니라 원본 파일.

**가공 (지금 파일과 같은 규격):**
- 길이 **정확히 2.5초.** 게임이 2.5초마다 다시 틀기 때문에(`CatCompanion.PURR_EVERY_MS`), 이어서
  재생해도 한 번의 고로롱처럼 들려야 합니다. 가능하면 날숨+들숨 한 호흡을 담고, 앞뒤 15 ms 페이드.
- 모노, 48 kHz, OGG Vorbis (`-c:a libvorbis -q:a 5`).
- 폰 스피커용: 80–120 Hz 하이패스. 고로롱의 기본음 25 Hz는 폰에서 안 들리고, 들리는 건 배음과
  숨소리입니다. 그 질감은 남깁니다.
- 음량: 평균 약 -20 dB, 최대 -2 dB 이하. 지금 파일과 맞춘 값입니다
  (`ffmpeg -i f.ogg -af volumedetect -f null -`).
- 확인: 4번 이어 붙인 10초짜리를 만들어 들어 보고, 이음새에서 툭 끊기거나 튀지 않는지 봅니다.

**합성기가 덮어쓰지 않게:** `tools/generate_sfx.py`의 `SOUNDS`에서 `"sfx_cat_purr"`를 빼고, 함수
`cat_purr()`는 남겨 둔 채 위에 "배포 파일은 녹음, docs/AUDIO_CREDITS.md 참고" 주석을 답니다.

**기록:** `docs/AUDIO_CREDITS.md`의 "녹음·생성 파일" 표에 파일, 출처 URL, 만든 사람, 라이선스, 가공
내용을 한 줄로 적습니다. CC BY 계열이면 앱 안에도 출처 표기가 필요할 수 있으니 사용자에게 알립니다.

## 2. AI 음성 파일

**목록 만들기:**

```bash
python3 tools/voice_lines.py --check
```

`tools/voice/lines.json`에 대사가 들어 있습니다(현재 102개). 항목마다 `file`(저장할 파일 이름),
`text`(읽을 문장), `style`, `key`(어느 문자열에서 왔는지)가 있습니다.

**파일 이름은 절대 바꾸지 않습니다.** 앱은 문장의 SHA-1으로 파일을 찾습니다. 발음이 어색하면(예:
"런지", "냥", "딥스") TTS에 넣는 입력만 발음대로 고치거나 SSML로 조정하고, 저장 이름은 `lines.json`의
`file` 그대로 씁니다.

**TTS 고르기.** 조건은 세 가지입니다: 무료, 만든 음성을 유료 앱에 넣어 팔 수 있음, 자연스러운
한국어.
1. **로컬에서 돌리는 오픈소스 모델**, 모델 가중치까지 상업 이용이 허용된 것. 예: MeloTTS 한국어
   (코드 MIT — 가중치 라이선스도 확인). 계정이 필요 없어서 먼저 시도합니다.
2. **Google Cloud Text-to-Speech**의 한국어 음성. 월 무료 한도 안이면 무료이고, 생성 음성의 상업
   이용이 허용됩니다. 대신 사용자의 Google Cloud 계정과 결제 수단 등록이 필요하니 먼저 묻습니다.
3. 쓰지 않는 것: 무료 요금제가 상업 이용을 막거나 출처 표기·워터마크를 요구하는 서비스, 약관이
   불분명한 비공식 엔드포인트(예: 브라우저 "소리 내어 읽기"를 흉내 내는 라이브러리). 헷갈리면 묻습니다.

**목소리 (style별):**

| style | 무엇 | 목소리 |
|---|---|---|
| `URGENT` | 필살기 예고, 남은 막기 횟수, 막았다/맞았다 | 게임 목소리와 같은 화자. 빠르게(+20–25%), 음을 살짝 높게(+1–2반음), 긴박하고 힘 있게. 비명이나 겁먹은 톤이 아니라 "지금 해!"라는 경고. 2–3 m 떨어져 운동 중인 사람이 바로 알아들어야 합니다. |
| `COACH` | 자세 안내, 콤보, 쉬는 시간 | 따뜻하고 격려하는 트레이닝 파트너, 해요체. 보통~조금 빠르게. 교관처럼 명령하지 않습니다. |
| `CAT` | 고냥이 대사 | 다른 화자. 높고 귀여운 목소리, 조금 빠르게. `scared`, `panic`은 겁먹게, `saved`는 안도하게, `calm`, `hello`는 편안하게. |

`URGENT`와 `COACH`는 한 화자로 통일해서 게임 목소리가 하나로 들리게 합니다.

**파일 규격:**
- `app/src/main/assets/voice/<file>` (폴더가 없으면 만듭니다).
- OGG Vorbis, 모노, 24 kHz 또는 48 kHz, `-q:a 4`.
- 앞뒤 무음은 50 ms 이하로 자릅니다.
- 음량은 전부 같게: `loudnorm=I=-16:TP=-1.5`.
- 길이: `URGENT`는 되도록 2.5초 이하(끼어드는 말이라), 나머지는 4초 이하.
- 폴더 전체가 3 MB 안팎이면 충분합니다.

**확인:**
- `python3 tools/voice_lines.py --check`에서 `missing`이 `796f104f1460` 하나뿐이고 `stale`이 없어야
  합니다. 그 하나는 일부러 뺀 것입니다(AUDIO_CREDITS.md 참고).
- style별로 몇 개씩 직접 들어 봅니다. 특히 숫자("세 번", "두 번 더!", "30초")와 "냥"의 발음.

**기록:** `docs/AUDIO_CREDITS.md`의 "음성" 표에 도구·모델·화자 이름·라이선스를 적습니다.

## 3. 확인하고 올리기

```bash
scripts/check-resources.sh
scripts/test-core.sh        # :core는 바뀌지 않지만 돌려서 확인
python3 tools/voice_lines.py --check
```

- 커밋 메시지는 저장소의 다른 커밋처럼 영어로, 무엇을 어디서 가져왔는지 적습니다.
- `git pull --rebase origin claude/job-switch-dark-mode-ip6vad` 후
  `git push origin claude/job-switch-dark-mode-ip6vad`.
- 설치용 APK는 `claude/pushup-rpg-app-2xnr4o`에 푸시될 때 CI가 만듭니다. 같은 커밋을 fast-forward로만
  올립니다(`git push origin claude/job-switch-dark-mode-ip6vad:claude/pushup-rpg-app-2xnr4o`,
  거절되면 강제로 밀지 말고 사용자에게 알립니다).
- CI: https://github.com/cjsdudwls1/push-up/actions — 초록이 되면 APK:
  https://github.com/cjsdudwls1/push-up/releases/download/debug/pushup-rpg-debug.apk

**보고할 것:** 고로롱 출처와 라이선스, 쓴 TTS와 화자, 발음이 어색해서 손본 대사, 결제·계정이 필요해서
멈춘 곳이 있으면 그 지점.
