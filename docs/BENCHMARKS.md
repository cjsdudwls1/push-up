# 벤치마킹: 성장을 보여주는 앱들

2026-09-27 조사. 고냥이 지켜줘를 중심에 두면서 목숨·휴식, 홈과 복귀 환영, 누적 성장, 개인 기록, 꾸미기,
알림, 주간 리포트를 어떻게 만들지 정하려고 봤어요. 결정은 [DECISIONS.md](DECISIONS.md)의
"고냥이를 중심으로"에 있어요. 아래 [n]은 맨 아래 출처 번호이고, (2°)는 2차 자료예요.

## 앱별로 본 것

| 주제 | 앱 | 한 일 |
|---|---|---|
| 목숨 | 듀오링고 | 틀리면 하트가 줄던 방식을, 맞힐 때 채워 주는 에너지로 바꿨어요. "당근이지 채찍이 아니다". DAU·학습 시간·유료 전환이 같이 올랐어요 [1][2] |
| 목숨 | Finch | 처음엔 새가 죽을 수 있었는데, 팀과 사용자가 '죽지 않는 새'로 바꿨어요 [8] |
| 휴식 | 링핏·애플 | 쉬는 시간을 게임이 챙기고, 링을 90일까지 멈춰도 연속이 안 끊겨요 [4][6] |
| 복귀 | 듀오링고 | 복귀자를 7–29일과 30일+로 나눠요. 30일+ 복귀자는 새 사용자보다 20% 덜 남아요. 자기 쪽 장애로 끊긴 연속은 자동으로 지켜 줘요 [9][13] |
| 복귀 | 헬스장 6만 명 실험 | 빠진 뒤 돌아오면 작은 보상을 준 프로그램이 방문을 27% 늘렸어요 [18] |
| 홈 | Finch·말해보카 | 캐릭터가 홈이고, 버튼 하나로 오늘 할 일을 시작해요. 쉬어도 격려만 보내요 [14][15] |
| 누적 성장 | 나이키 런클럽 | 누적 거리로 레벨: 50·250·1,000km… [19] |
| 누적 성장 | 말해보카 | '나의 어휘력'을 추정 단어 수와 상위 N%로 보여줘요 (2°) [21][22] |
| 개인 기록 | 스트라바·나이키 | 기록을 자동으로 찾아 결과 화면에서 축하해요. 스트라바는 '올해 최고'도 따로 둬요 [26][27][28] |
| 꾸미기 | Finch | 목표를 하면 에너지 → 새가 모험 → 무지개돌로 옷과 가구를 사요 [30] |
| 꾸미기 | 애플·포켓몬GO | 배지와 한정 스티커, 메달로 옷을 열어요 [33][32] |
| 알림 | 듀오링고 | 하루 1번 이하, 오늘 했으면 안 보내고, 7일 쉬면 멈춰요. 알림 수는 절대 늘리지 않아요 [10][11][34] |
| 주간 리포트 | 애플 | 월요일마다 지난주 합계와 평균 [29] |
| 주간 리포트 | 포켓몬 슬립 | 이번 주를 지난주 옆에 흐리게 겹쳐 보여줘요 (2°) [37] |

## 연구가 말하는 것

- 연속이 끊긴 걸 짚으면 다시 시작할 의욕이 떨어지고, 되살릴 길이 있으면 덜해요 [40].
- '하면 준다'고 미리 약속한 보상은 내적 동기를 깎고(d −0.28 ~ −0.40), 칭찬은 키워요(+0.33) [46].
- 새 주·새 달 같은 '새 출발' 시점에 시작 의욕이 올라가요 [42].
- 일반적인 푸시 알림을 늘리면 앱 삭제가 늘고 열람은 줄었어요(17,500명 현장 실험) [47].
- 초보자의 근력운동은 주 2–3회, 지구력 세트 사이 휴식은 90초 이하가 권장이에요 (ACSM 2009) [39].

## 연속·재방문과 매출

- 듀오링고는 '지금 쓰는 사람이 계속 쓰는 비율(CURR)'이 DAU에 다음 지표보다 5배 큰 영향을 줬고,
  4년 동안 DAU가 4.5배가 됐어요. 리그는 학습 시간을 17% 늘렸어요 [11].
- 한 레슨으로 연속을 지키게 하자 7일+ 연속 사용자가 40% 늘었어요 [50].
- 에너지 도입 분기에 DAU·학습 시간·유료 전환이 같이 올랐다고 주주서한에 적었어요 [2].

## 여기서 정한 것

| 주제 | 결정 | 근거 |
|---|---|---|
| 목숨 | 10목숨 = 10세트, 휴식 1분 (2026-10-02부터 건너뛰기 가능) | 소유자 결정. 여러 세트 > 1세트 |
| 잃은 목숨 | '놀랐어요', 고양이는 다치지 않음. 카메라 탓이면 한 번 돌려줌 | Finch의 '죽지 않는 새', 듀오링고의 장애 보호 [8][13] |
| 홈 | 고양이 + 버튼 하나 + 'N일 만이에요' 환영 | Finch·말해보카 [14][15] |
| 누적 성장 | 높이로. 가까운 첫 목표(캣타워 2m)부터, 줄지 않음 | 나이키 레벨 [19], 첫 목표를 빨리 [44] |
| 연속 | 매일 그대로, 끊겼다는 말 없음 | [40] |
| 꾸미기 | 되돌릴 수 없는 기록 달성 → 깜짝 선물 | [46] |
| 알림 | 없음 | 소유자 결정 |
| 주간 | '이번 주' 카드 + 새 주엔 '지난주 결과' | 애플 [29] |

확인하지 못한 것: Finch 성장 단계 수, 포켓몬 GO·슬립 주간 리포트 세부(위키만 봄), Seven 규칙(도움말
차단), 듀오링고 월간 리포트, 나이키 런클럽의 연속 기능.

## 출처

1. https://blog.duolingo.com/duolingo-energy/
2. https://www.sec.gov/Archives/edgar/data/1562088/000156208825000165/q2fy25duolingo6-30x25share.htm
4. https://www.nintendo.com/jp/ichikara/al3pa/02_en.html
6. https://support.apple.com/en-sg/guide/watch/apd29b30023c/watchos
8. https://medium.com/@skuni/a-look-back-on-finchs-first-year-599ba68d06f2
9. https://blog.duolingo.com/back-from-the-brink-what-duolingo-learned-about-its-resurrected-users
10. https://subclub.com/episode/how-to-time-reactivation-campaigns-for-maximum-impact-jackson-shuttleworth-duolingo
11. https://www.lennysnewsletter.com/p/how-duolingo-reignited-user-growth
13. https://blog.duolingo.com/protecting-streaks-from-site-issues
14. https://slate.com/technology/2026/09/finch-app-self-care-wellness-review.html
15. https://v.daum.net/v/20260123133305193
18. https://pubmed.ncbi.nlm.nih.gov/34880497/
19. https://www.nike.com/help/a/nrc-run-level
21. https://namu.wiki/w/%EB%A7%90%ED%95%B4%EB%B3%B4%EC%B9%B4 (2°)
22. https://epop.ai/newsroom/46
26. https://support.strava.com/en-us/articles/15401661-best-efforts-running
27. https://stories.strava.com/articles/whats-new-on-strava-new-languages-annual-best-efforts-and-weekly-streak-stickers
28. https://9to5mac.com/2019/03/22/feature-request-apple-watch-workout-personal-records/
29. https://support.apple.com/guide/watch/track-daily-activity-apd3bf6d85a6/watchos
30. https://finchcare.com/about-finch
32. https://pokemongo.fandom.com/wiki/Medals (2°)
33. https://www.apple.com/newsroom/2025/04/get-active-with-apple-watch/
34. https://research.duolingo.com/papers/yancey.kdd20.pdf
37. https://bulbapedia.bulbagarden.net/wiki/Pok%C3%A9mon_Sleep (2°)
39. https://pubmed.ncbi.nlm.nih.gov/19204579/
40. https://academic.oup.com/jcr/article-abstract/49/6/1095/6623414
42. https://doi.org/10.1287/mnsc.2014.1901
44. https://blog.duolingo.com/how-duolingo-streak-builds-habit
46. https://doi.org/10.1037/0033-2909.125.6.627
47. https://doi.org/10.21511/im.17(2).2021.10
50. https://blog.duolingo.com/improving-the-streak
