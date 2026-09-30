# 소리 출처

앱의 효과음과 배경음악은 아래 표에 적은 녹음을 빼고 전부 이 저장소의 코드로 합성했습니다 —
`tools/generate_sfx.py`, `tools/generate_music.py`. 외부 녹음이나 생성 음성을 넣으면 여기에 한 줄씩 적습니다: 파일, 출처
URL, 만든 사람, 라이선스, 가공 내용. CC0(퍼블릭 도메인)이 아니면 앱 안에도 표기가 필요한지 먼저
확인하세요.

## 녹음·생성 파일

| 파일 | 출처 | 만든 사람 | 라이선스 | 가공 |
|---|---|---|---|---|
| `res/raw/sfx_cat_purr.ogg` | [Wikimedia Commons: Whiskers' purr.ogg](https://commons.wikimedia.org/wiki/File:Whiskers%27_purr.ogg) (2009-07-22, 원본 파일) | Adam Cuerden | 퍼블릭 도메인 (PD-self: 저작권자가 조건 없이 모든 용도로 사용 허락, 표기 의무 없음) | 원본 5.122–7.622초 한 호흡(날숨+들숨)만 잘라 정확히 2.5초, 이음새는 들숨 끝 조용한 곳; 48 kHz 모노; 100 Hz 하이패스(24 dB/oct); +24.5 dB, 컴프레서(-20 dB, 2:1), 리미터; 앞뒤 15 ms 페이드; Vorbis q5. volumedetect 평균 -20.6 dB, 최대 -2.5 dB |

## 음성 (`app/src/main/assets/voice/`)

게임 음성은 기본적으로 폰의 텍스트 음성 변환(TTS)이 읽습니다. 미리 만든 음성 파일이 있으면 그것을
대신 틉니다 — 목록과 파일 이름은 `tools/voice_lines.py`가 만드는 `tools/voice/lines.json`에
있습니다.

| 음성 | 도구·모델 | 라이선스 | 비고 |
|---|---|---|---|
| 게임 목소리: `COACH` 18개 (자세 안내) | [Qwen3-TTS](https://github.com/QwenLM/Qwen3-TTS) [`Qwen/Qwen3-TTS-12Hz-1.7B-CustomVoice`](https://huggingface.co/Qwen/Qwen3-TTS-12Hz-1.7B-CustomVoice) (리비전 `0c0e3051`), 내장 화자 `Sohee`(한국어 여성). `qwen-tts` 0.1.1로 CPU에서 직접 생성 | Apache-2.0 (코드·가중치). 생성 음성의 상업 이용 제한, 출처 표기·워터마크 의무 없음. 계정·결제 없이 받음 | 던전의 `URGENT` 7개와 `COACH` 18개(필살기, 콤보, 직업별 안내)는 던전을 앱에서 지울 때(2026-09-30) 함께 지웠습니다. 아래 "만든 방법" |
| 고냥이 목소리: `CAT` 57개 (58줄 중 한 줄은 뺌, 아래 "한계") | 같은 Qwen3-TTS의 [`Qwen/Qwen3-TTS-12Hz-1.7B-VoiceDesign`](https://huggingface.co/Qwen/Qwen3-TTS-12Hz-1.7B-VoiceDesign) (리비전 `5ecdb673`). 내장 화자가 아니라 글로 설명한 목소리(아래 "말투"). `qwen-tts` 0.1.1로 [Modal](https://modal.com) 클라우드 GPU(NVIDIA L4)에서 fp32로 생성. `53191fdb44e0` 한 줄만 2026-09-30에 CPU에서 다시 만듦(아래 "다시 만든 대사") | Apache-2.0 (코드·가중치). 조건은 위 줄과 같음. 계정·결제 없이 받음 | 게임 목소리와 다른 화자, 감정은 대사 종류마다 instruct로. 아래 "만든 방법" |

### 만든 방법

**라이선스.** 두 모델 모두 Hugging Face 모델 카드와 GitHub 저장소에 Apache-2.0으로 적혀 있고, 받을 때
로그인이나 약관 동의 절차가 없습니다. 모델 카드와 README에는 생성한 음성의 용도 제한, 출처 표기,
워터마크 조건이 없습니다.

**어디서 돌렸나.** `COACH`와 (지금은 없는) `URGENT`는 이 컴퓨터의 CPU에서 만들었고, 대사를 외부 서비스로 보내지
않았습니다. `CAT`은 CPU로는 느리고 메모리를 많이 써서, 사용자의 Modal 계정 크레딧으로 클라우드
GPU에서 만들었습니다. 대사 글과 말투 설명을 Modal로 보내 음성을 만들었고, 만든 음성을 다시 Modal로
보내 Whisper로 받아쓰게 했습니다. 모델은 Hugging Face에서 로그인 없이 받아 Modal 이미지에 넣었고,
컨테이너는 네트워크를 막고 돌렸습니다. [Modal 이용약관](https://modal.com/legal/terms)(2026년 5월
시행본, 2026-09-25 확인)에는 고객이 올린 데이터와 AI 도구의 입력·출력이 고객 데이터이고 그 권리는
고객에게 있으며, 처리가 끝나면 곧 지우고, Modal이 고객 데이터로 AI 모델을 학습하지 않는다고 적혀
있습니다. 결과물의 용도를 제한하거나 출처 표기를 요구하는 조항은 없습니다.

**읽힐 글.** `lines.json`의 `text`(파일 이름이 여기서 나옵니다)는 그대로 두고, TTS에 넣는 글에서만 숫자를
한국어로 풀어 썼습니다. 규칙에서 벗어난 것은 한 줄입니다. `50번 연속! 최고예요, 냥!`(`fe42de3de27e`)은
`쉰 번` 대신 `오십 번`으로 읽혔습니다. 고냥이 목소리가 `쉰`을 열다섯 번 모두 `신`이나 `심`으로 읽었기
때문입니다. 숫자 말고 발음 때문에 바꾼 대사는 없습니다.

| 원문 | TTS에 넣은 글 | 규칙 |
|---|---|---|
| `10번 연속! 최고예요, 냥!` … | `열 번 연속!`, `스무 번`, `서른 번` … `아흔 번`, `백 번` (`50번`만 `오십 번`) | 번: 고유어 수 |
| `10연속! 계속 가요!` … | `십 연속!` … `백 연속!` | 연속: 한자어 수 |
| `30초나 버텼어요, 냥!`, `벌써 120초! 멋져요!` … | `삼십 초나`, `백이십 초!` … | 초: 한자어 수 |

**말투(instruct, 원문 그대로).**
- `COACH`: "Speak warmly and encouragingly, like a friendly workout partner cheering someone on. Natural,
  slightly brisk pace; kind and supportive, never bossy."
- `COACH` 중 4초를 넘기 쉬운 긴 안내 6개는 "Natural, slightly brisk pace" 대신 "Quick, brisk pace with
  only a short breath between sentences"로도 만들어 함께 골랐습니다(4개가 이쪽에서 뽑힘).
- `CAT`: 목소리 설명 "A tiny, adorable cartoon kitten character voiced by a young girl: high-pitched,
  bright, sweet and soft, speaking a little fast in a playful, cute way, with clear pronunciation so every
  word is easy to understand." 뒤에 대사 종류(`key`의 `cat_line_<종류>_`)마다 한 문장을 붙였습니다.
  - `calm`: "Relaxed and content, cozy and gentle."
  - `hello`: "Cheerful, relaxed and friendly, gently encouraging."
  - `combo`, `milestone`: "Happy and proud, cheering brightly."
  - `near_miss`: "Eager and encouraging, cheering the player on."
  - `uneasy`: "A little worried and unsure, slightly hesitant."
  - `waiting`: "Timid and a little anxious, softly asking for help." (`53191fdb44e0`은 아래 "다시 만든 대사")
  - `scared`: "Scared and nervous."
  - `panic`: "Frightened, urgently pleading for help."
  - `saved`: "Relieved and happy, grateful."

  첫 시험에서 "trembling", "breathless", "desperately"를 넣은 말투는 발음을 뭉개서 뺐습니다.

**고르기.** 대사마다 시드를 바꿔 여러 번 만들었습니다(`COACH` 3번, 긴 안내 6개는 빠른 말투로 4번 더).
그중 Whisper large-v3-turbo가 받아쓴 글이 대사와 가장 가까운 것들 가운데서 길이 초과, 문장 안의 긴 쉼,
평소 음높이와의 차이, 목소리가 다른 것들과 얼마나 같은지(화자 임베딩)를 따져 하나씩 골랐습니다. 완성한
파일은 인코딩한 뒤 한 개씩 다시 받아쓰게 했습니다.

`CAT`은 대사마다 시드 1–5로 다섯 번 만들었습니다. 다섯 번 모두 받아쓰기가 틀린 8줄은 시드 6–15로 열 번
더 만들었고, 그중 `50번 연속`은 `오십 번`으로 바꾼 뒤 시드 101–110으로 열 번 더 만들었습니다. 고르는
기준은 위와 같고, 음높이와 화자 임베딩은 `CAT` 테이크끼리 비교했습니다. 완성한 파일을 받아쓰게 했을 때
14줄이 틀렸습니다. 그 줄들은 다른 테이크들을 같은 가공과 인코딩을 거쳐 한 개씩 받아쓰게 한 뒤, 바르게
들린 것 가운데서 같은 기준으로 다시 골랐습니다. 바르게 들린 것이 하나도 없던 4줄은 시드 16–30으로
열다섯 번 더 만들었고, 그중 3줄이 이것으로 풀렸습니다. 남은 한 줄은 사용자가 들어 보고 뺐습니다(아래 "한계").

**가공.**
- 문장 안의 쉼은 `COACH` 0.3초, `CAT` 0.4초까지로 줄였습니다(쉼의 가운데를 잘라냄).
- 4초를 넘던 `COACH` 두 줄(`f47043e2f327`, `f88fde8a37e2`)은 1.13배, `CAT` 한 줄(`fe42de3de27e` 1.05배)도
  모자란 만큼 빠르게 했습니다.
- 앞뒤 무음은 50 ms 이하로 잘랐습니다. `CAT` 두 줄(`16d070a30f07`, `f8b447f31e4b`)은 끝의 작은 숨소리가
  음량을 맞춘 뒤 -45 dBFS 아래로 떨어져 무음으로 재졌기 때문에, 인코딩한 파일의 -45 dBFS 기준으로 한 번
  더 잘랐습니다.
- 음량은 모든 파일 -16 LUFS(±0.3 LU), 트루 피크 -1.5 dBTP 이하입니다. `loudnorm` 2-pass는 이렇게 짧은
  클립에서 동적 모드로 바뀌어 목표를 최대 3 LU 빗나가서, 고정 게인과 4배 오버샘플링 리미터로 인코딩한
  뒤 인코딩된 파일을 EBU R128로 다시 재서 맞을 때까지 반복했습니다.
- OGG Vorbis `-q:a 4`, 24 kHz 모노.

**다시 만든 대사.** `53191fdb44e0` "천장이 무서워요… 지켜 줄 거죠?"는 묻는 말인데 끝이 1.7반음 내려가
혼잣말처럼 들렸습니다. 연기 지시에 물어보듯 끝을 올리라는 말이 없었고, 고를 때 끝 억양을 보지 않았기
때문입니다. 2026-09-30에 네 가지 방법으로 11개를 만들어 사용자가 듣고 골랐습니다.

- 고른 것: 같은 모델·리비전·목소리 설명에, 이 대사의 문장만 "Timid and a little anxious, softly and
  hopefully asking for help, and ending on a clearly rising, questioning pitch."로 바꿔 시드 7로 네 번 만든 것의
  네 번째. 이 환경의 CPU에서 fp32로 만들었고, 대사를 외부 서비스로 보내지 않았습니다. 그날 Modal을 못 쓴 것은
  네트워크 탓이 아니었습니다. Modal 클라이언트를 프록시 지원(`python-socks`) 없이 설치해 이 환경의 프록시를
  거쳐 붙지 못했습니다. `pip install 'modal[api-proxy-support]'`로 설치하면 붙습니다.
- 마지막 음절이 10.2반음 올라갑니다. Whisper large-v3-turbo는 첫 단어를 "현장이"로 받아썼지만, 사용자가 듣고
  이것으로 정했습니다.
- 가공은 위와 같습니다(쉼 0.4초 이하, 앞뒤 무음 50 ms 이하, -15.9 LUFS, 트루 피크 -2.7 dBTP). 인코딩은 이
  환경에 ffmpeg가 없어 libsndfile의 Vorbis로 했습니다(24 kHz 모노, 약 62 kbps, 다른 클립은 56–59 kbps).
- 후보 중에는 지금 녹음의 끝만 Praat로 올린 것, Qwen3-TTS Base로 고냥이 목소리를 복제한 것,
  [Chatterbox](https://github.com/resemble-ai/chatterbox) 다국어 모델(MIT)로 만든 것도 있었습니다. 앱에는
  들어가지 않았습니다.

**한계.**
- `8d65e466cb5d` "천천히 한 개씩 세어 볼게요"는 세 시드 모두 인코딩한 뒤 "세워 볼게요"로 받아써졌습니다.
  그중 인코딩 전에는 "세어"로 들린 것을 넣었고, 사용자가 들어 보고 이상 없다고 했습니다.
- `796f104f1460` "70번 연속! 최고예요, 냥!"은 뺐습니다. 서른 테이크 가운데 받아쓰기가 완전히 맞은 것이
  없었습니다. `일흔 번 연속`은 소리대로 하면 [이른 번 년속]이라, Whisper가 "이른번 연속", "이른바 녀석"
  등으로 적었습니다. 가장 나았던 시드 8을 넣었다가 사용자가 들어 보고 빼기로 했습니다(2026-09-25).
  이 줄은 폰의 TTS가 읽습니다. `tools/voice_lines.py --check`가 이 파일을 `missing`으로 보여 주는 것은
  이 때문입니다.
- 검수는 Whisper 받아쓰기와 음향 측정으로 했습니다. 2026-09-25에 사용자가 직접 들어 본 뒤
  `796f104f1460`만 빼고 나머지는 전부 이상 없다고 했습니다.
- 쉬는 시간과 다음 목숨의 대사 다섯 줄(`cat_line_rest_1`, `cat_line_rest_2`, `cat_line_rest_ten`,
  `cat_line_again_1`, `cat_line_again_2`)은 위 파일들을 만든 뒤에 생긴 대사라 아직 클립이 없고, 폰의 TTS가
  읽습니다. 그래서 `--check`는 `clips: 75 of 81 present`를 출력하고 이 다섯 줄과 `796f104f1460`을
  `missing`으로 보여 줍니다. 만들면 `CAT`으로 만듭니다.
