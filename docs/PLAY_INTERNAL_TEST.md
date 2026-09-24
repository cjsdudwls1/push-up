# Play 내부 테스트 시작하기

내부 테스트 트랙에 올리면 폰의 Play 스토어가 새 버전을 알아서 받아 줍니다. 한 번 설정해 두면
`claude/pushup-rpg-app-2xnr4o` 브랜치에 올라가는 빌드마다 CI가 서명된 번들을 만들어 내부 테스트에
올립니다(`.github/workflows/release.yml`).

지금 쓰는 디버그 APK 링크는 그대로 둡니다. 두 앱은 이름이 달라서(`…pushuprpg`와
`…pushuprpg.debug`) 폰에 따로 설치되고, 레벨·기록은 서로 공유되지 않습니다. **동작 기록 보내기는
디버그 앱에만 있습니다.** 개인정보처리방침이 "Play 빌드는 자세 데이터를 저장하지 않는다"고
약속하고 있어서, Play 빌드에는 넣지 않았습니다.

## 1. 개발자 계정 만들기 (직접, 며칠 걸릴 수 있음)

https://play.google.com/console/signup

- **계정 유형: 개인.** 아래 "개인사업자로 가입하면?" 참고.
- 등록비 $25 (한 번).
- 신분증 인증, 전화번호 인증. 새 개인 계정은 Play Console 앱으로 실제 안드로이드 기기를 가지고
  있는지도 확인합니다.
- 계속 쓸 Google 계정으로 만듭니다. 나중에 옮기기 번거롭습니다.

## 2. 앱 만들기 (직접, 5분)

Play Console → 앱 만들기

- 기본 언어 한국어, 무료.
- 앱/게임은 나중에 바꿀 수 있습니다. 한국에서 "게임"으로 내면 게임물관리위원회 등급 분류가
  걸리므로(RELEASE.md), 정식 출시 전에 다시 정합니다.
- 내부 테스트는 스토어 등록정보·스크린샷·콘텐츠 등급·데이터 보안 양식이 **없어도** 시작할 수
  있습니다.

## 3. 업로드 키 만들기 (직접 또는 로컬 Claude Code, 5분)

키는 이 대화나 저장소를 거치지 않게, 본인 컴퓨터에서 만듭니다.

```bash
keytool -genkeypair -v -keystore upload.jks -alias upload \
  -keyalg RSA -keysize 2048 -validity 10000
```

`keytool`은 JDK에 들어 있습니다(Android Studio를 깔았다면 그 안에 있음). 비밀번호를 정하라고
나오면 정하고, 이름 등은 적당히 채웁니다.

**`upload.jks`와 비밀번호는 저장소 밖 두 곳 이상에 보관합니다.** 잃어버려도 Play 앱 서명
덕분에 복구 요청은 가능하지만 며칠 걸립니다.

base64로 바꿉니다:

```bash
base64 -w0 upload.jks > upload.b64              # Linux
base64 -i upload.jks -o upload.b64              # macOS
```

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("upload.jks")) | Out-File upload.b64   # Windows
```

GitHub 저장소 → Settings → Secrets and variables → Actions → New repository secret:

| 이름 | 값 |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | `upload.b64` 내용 전체 |
| `RELEASE_STORE_PASSWORD` | 키스토어 비밀번호 |
| `RELEASE_KEY_ALIAS` | `upload` |
| `RELEASE_KEY_PASSWORD` | 키 비밀번호 (따로 안 정했으면 키스토어와 같음) |

## 4. 첫 번들은 손으로 올리기 (직접, 10분)

Play는 앱의 첫 번들을 API로 받지 않습니다. 딱 한 번만 손으로 올립니다.

1. GitHub → Actions → **Release AAB** → Run workflow (브랜치 `claude/pushup-rpg-app-2xnr4o`).
2. 끝나면 그 실행 페이지 아래 Artifacts의 `app-release-aab`를 받아 압축을 풉니다(`.aab` 파일).
3. Play Console → 테스트 → **내부 테스트** → 새 버전 만들기.
   - Play 앱 서명: 기본값(Google이 앱 서명 키 관리) 그대로.
   - `.aab` 업로드, 출시 노트는 한 줄이면 됩니다.
   - 저장 → 검토 → 내부 테스트로 출시 시작.
4. 같은 페이지의 **테스터** 탭 → 이메일 목록 만들기 → 본인 Gmail(최대 100명) → 저장.
5. "웹에서 참여" 링크를 폰에서 열고 → 테스터 되기 → Play 스토어에서 설치.

## 5. 그다음부터 자동으로 (직접, 15분)

서비스 계정을 만들어 두면, 이후로는 푸시할 때마다 CI가 내부 테스트에 올립니다.

1. https://console.cloud.google.com → 프로젝트 만들기(아무 이름).
2. API 및 서비스 → 라이브러리 → **Google Play Android Developer API** → 사용.
3. IAM 및 관리자 → 서비스 계정 → 만들기(역할은 비워 둠) → 만든 계정 → 키 → 키 추가 → JSON.
   받은 파일이 비밀 정보입니다.
4. Play Console → 사용자 및 권한 → 새 사용자 초대 → 서비스 계정 이메일(`…@….iam.gserviceaccount.com`)
   → 앱 권한에 이 앱 추가 → "테스트 트랙으로 출시"와 "앱 정보 보기" 권한 → 초대.
5. GitHub Secrets에 `PLAY_SERVICE_ACCOUNT_JSON` = JSON 파일 내용 전체.

이후 CI 흐름:
- 설치 브랜치에 푸시할 때마다 버전 코드를 올려 서명하고 내부 테스트에 업로드합니다.
- 폰의 Play 스토어가 보통 알아서 업데이트합니다. 급하면 Play 스토어에서 앱을 열고 업데이트를
  누릅니다.
- 업로드가 "draft app" 오류로 실패하면, 앱이 아직 초안 상태라는 뜻입니다. 저장소 Variables에
  `PLAY_RELEASE_STATUS` = `draft`를 넣으면 초안으로 올라가고, Console에서 출시를 누르면 됩니다.

## 개인사업자로 가입하면 12명 × 14일이 면제되나?

**사실상 안 됩니다.**

- 비공개 테스트 12명 × 14일은 2023-11-13 이후에 만든 **개인** 계정에만 적용됩니다. 조직
  계정(D-U-N-S 번호 필요)은 면제입니다.
- 국내 개인사업자도 D-U-N-S를 받아 조직 계정을 만들 수는 있습니다. 그런데 여러 국내 개발자가
  같은 문제를 보고합니다: 조직 계정으로는 판매자 결제 프로필을 만들 수 없어서 인앱 결제·구독
  수익을 정산받지 못하고, 개인 계정으로 옮겨야 했다고요(옮기는 데 1주일 걸렸다는 보고도
  있음).
- 이 앱은 구독으로 벌 계획이라, 개인사업자로 가도 결국 개인 계정이 되고 12명 × 14일을 거칩니다.
  면제를 받으면서 수익도 받으려면 법인이 필요합니다.
- 이건 커뮤니티 보고와 Google 도움말 검색 결과를 바탕으로 한 판단입니다. 가입 화면의 안내가 다르면
  그쪽이 맞습니다.

따라서 할 일은 **비공개 테스트를 빨리 시작하는 것**입니다. 14일은 12명이 계속 참여한 상태로 채워야
하므로, 친구·동기·지인 12명 이상(여유 있게 15명)의 Gmail을 미리 모아 둡니다. 비공개 테스트에는
내부 테스트와 달리 스토어 등록정보, 스크린샷, 콘텐츠 등급, 데이터 보안, 개인정보처리방침이
필요합니다. 준비물은 RELEASE.md에 정리되어 있습니다.
