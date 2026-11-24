# CHANGELOG

> 단계 번호: 아래 "1단계 — 통합 스키마…" 는 로드맵의 00(foundation) 이다. 로드맵 01(ingestion)은 이 문서 맨 위 항목이다.

## 급경사지 경사도 이식 — 원본 FlatMap 값 그대로 (2026-11-24)

v2 개발 DB 의 급경사지 78건은 경사도가 전부 NULL 이었다(수집원인 행안부 CSV 에 경사도 열이 없다). 원본 FlatMap 이 쓰던 값을 그대로 옮겼다.

- **출처 확인**: 원본 FlatMap DB 덤프(2026-11-07)의 MOIS_CSV 84건 `grade` 가 `성남시_급경사지_경사도_산경사도.csv` 의 `최종경사도(도)` 와 84건 모두 같다. 이 CSV 는 Copernicus DEM 30m 에서 읽은 지점 경사를 도(°)로 적은 것이다(지점 값 81건, 주변 3x3·5x5 최대로 보정한 행 3건). `최종2.csv` 처럼 같은 값에 헤더만 `(%)` 로 잘못 붙은 파일이 있었으나 단위는 °가 맞다. `sigungu`·`eupmyeon`·`jibun`·`region_code`·산 지번 여부도 이 CSV 에서 만든 값이 덤프와 84건 모두 같다.
- **추가**: `SteepSlopeGradeImportService`(번들 CSV `data/성남시_급경사지_경사도.csv` 읽기·검증·적재), 관리용 `POST /api/steep-slope/import/seongnam-grades`(`X-Admin-Api-Key` 필요).
  - `MOIS_CSV` 출처만 한 트랜잭션에서 통째로 교체한다. 몇 번 실행해도 84건이다. 다른 출처 행은 건드리지 않는다.
  - 파일을 먼저 전부 검증하므로 한 행이라도 잘못되면(경사도 0° 이상 90° 미만이 아님, 숫자가 아님, 주소·시군구 불일치, 필수 열 없음) DB 는 바뀌지 않는다. 0° 는 측정값으로 받는다.
- **결과(개발 DB)**: MOIS_CSV 78건(경사도 전부 NULL) → 84건(경사도 0.95~27.02°, 전부 값 있음). 이름이 같은 `소각장` 2건은 좌표가 달라 별개 행이다.
- **옮기지 않은 것**: 덤프의 `급경사 내리막`(DISASTER) 4건. 붕괴위험 지정지가 아니고 번들 CSV 에도 없다.
- **쓰지 않음**: `frontend/public/data/team_slopes.csv` 는 옛 팀 자료로 값이 원본 DB 와 다르고(70건 중 42건 불일치) 헤더도 `(%)` 다. 이식에 쓰지 않았고 코드도 읽지 않는다.
- **붕괴위험 표본의 성격은 그대로다**: 이 경사도는 경고 레이어 표시용이며 보행난이도 임계값(3.18°/4.76°)을 적용하지 않는다.
- **테스트**: 파서 단위 테스트 13개(필드 변환, BOM·CRLF·따옴표, 0°, 범위·형식 오류, 한 행 오류 시 전체 실패, 번들 CSV 84건·원본 값) + 통합 테스트 4개(PostGIS: 84건 적재·geom, 두 번 실행해도 84건, 옛 MOIS 행 교체·다른 출처 유지, 키 없으면 401) + 관리용 엔드포인트 보안 테스트에 새 경로 추가. 백엔드 전체 469개 통과.
- **테스트 설정**: 테스트 프로필의 DB 연결 풀을 3개로 줄였다(`application-test.properties`). 캐시된 Spring 컨텍스트마다 풀(기본 10)이 열려 있어 컨텍스트가 늘자 PostgreSQL 기본 한도(100)를 넘어 마지막 클래스(`WalkDifficultyEndpointTest`) 5개가 연결을 못 얻고 실패했다(HEAD 에서도 재현). 코드 문제가 아니었다.
- **검증 방법**: 8081 포트에 검증용 서버를 띄워 개발 DB 에 적재하고 API(`/api/steep-slope`)와 DB 값을 확인한 뒤 종료했다. 사용자 서버(8080 등)는 건드리지 않았다.

## 정리 — 안 쓰는 코드·설정 제거와 문서 최신화 (2026-11-20)

동작·API·화면은 바꾸지 않았다. 호출하는 곳이 없는 것만 지웠다.

- **안 쓰는 코드 삭제**: `SafetyFacilityRepository.countByDataSource`(호출 0), 미사용 import(`AuthController`, `PublicDataService`, `AuthContext`).
- **일부러 남긴 것**: 쓰지 않는 설정 키(`GYEONGGI_*` 예약 키 5개, `region.*`)는 사용자 결정으로 그대로 둔다(`docs/ENVIRONMENT.md` 에 "현재 코드에서 사용하지 않음"으로 표시됨). 시설 반경 조회(`findWithinMeters`·`countWithinMeters`)는 테스트만 쓰지만 반경이 미터 단위임을 검증하는 테스트가 있어 유지. 관리용 엔드포인트와 그 서비스(`ElevationService`, `CsvImportService` 등)는 API 계약이라 유지.
- **문서**: README "알려진 한계"에 경기데이터드림 차단·보호 동작을 추가했다. 이전 단계 기록은 현행 코드와 일치함을 확인했다.
- 확인: 프론트 테스트·빌드 통과, 백엔드 `./mvnw test` 449개 중 444개 통과, 5개(`WalkDifficultyEndpointTest`)는 PostgreSQL 연결 슬롯 부족으로 컨텍스트를 못 띄워 실패했고(코드 원인 아님), 그 클래스만 다시 돌려 5개 모두 통과했다. 같은 5개가 재현되어 원인(연결 풀)을 고쳤고, 이후 전체 469개가 통과했다(위 "급경사지 경사도 이식" 참조).

## UI 색 톤 조정 (fix/ui-tone-down) (2026-11-17)

사용자 요청에 따라 색과 홈 화면 잘림만 고쳤다. 기능·문구·레이아웃·백엔드는 바꾸지 않았다.

- **홈 왼쪽 위 로고 아이콘**: 파랑-보라 그라데이션 → 초록 그라데이션(핀 안쪽 점도 초록). 지도 화면 헤더의 로고는 요청 범위 밖이라 그대로(파랑)다.
- **"길 찾기 시작" 버튼과 1~4 단계 박스**: 초록을 한 단계 덜 진하고 덜 선명하게(`#3f7655`·`#417657` → `#4f7f62`), 그림자도 약하게. 흰 글자 대비는 5.3 → 4.6(기준 4.5 이상)이다.
- **홈 화면 아래 잘림**: 데스크톱 폭에서 홈이 `height: 100dvh; overflow: hidden` 으로 고정되어 화면이 낮으면 아랫부분("개발 중" 문구 포함)이 잘렸다. 화면에 들어가면 그대로 채우고, 넘치면 스크롤되도록 바꿨다(모바일 폭 규칙은 그대로).
- **지도 화면 오른쪽 위 "카카오 로그인"·"긴급 신고"**: 노랑 `#fee500` → `#f7e978`, 빨강 `#b84242` → `#c05252` 과 약한 그림자. 긴급 신고 흰 글자 대비는 5.4 → 4.6으로 4.5 기준을 지켰다.
- **왼쪽 사이드바 이동 기준 4개**: 안전 우선=초록, 균형=파랑, 빠른 길=연한 노랑, 오르막 최소=연한 보라의 은은한 색. 선택 여부는 색뿐 아니라 ✓ 와 굵은 테두리로도 구분되고, 글자·설명 대비는 모두 4.5 이상이다.
- 확인: 프론트 테스트 105개·빌드 통과, 개발 서버(3001)에서 홈·지도 화면을 직접 확인.

### 2차 수정 (같은 날, 사용자 피드백 반영)

- **홈 스크롤 정정**: 1차에서 홈에 `overflow-y: auto` 를 둔 것이 잘못이었다. 이 요소가 스크롤 컨테이너가 되면서 절대 위치 장식 원이 스크롤 영역을 180px 늘려, 내용은 819px 인데 안쪽 스크롤 영역은 999px 이 되고 문서 스크롤과 이중으로 움직였다. `overflow: hidden` + `min-height: 100dvh` 로 바꿔 문서가 한 번만 스크롤된다(1440×765 에서 45px, 더 큰 화면에서는 스크롤 없음). 안쪽 스크롤 0, 아래쪽 문구까지 보임을 측정으로 확인했다.
- **이름**: 화면 이름을 `[이름 미정]` → **FlatMap**(`config/branding.js` 한 곳). README 제목·DEPLOY 문구·`DECISIONS.md`(로컬)·홈 화면 테스트를 함께 고쳤다. 저장소 폴더 이름 `FlatMap_V2` 는 그대로다.
- **지도 화면 로고**: 파랑-보라 그라데이션 → 홈과 같은 초록 그라데이션(핀 안쪽 점 포함). 1차에서 범위 밖이라 남겨 둔 것을 이번에 맞췄다.
- **전체 채도 낮춤**: 화면 UI 색(CSS 3개 파일, 이동 기준 카드, 경로 카드)의 채도를 35% 낮췄다(`desat` 방식: 선형 RGB 에서 같은 밝기의 회색 쪽으로 섞음 → **밝기가 같아 글자 대비 비율은 소수점까지 그대로**). 시작 버튼·1~4 박스는 추가로 더 낮춰 `#627b6b`(흰 글자 대비 4.62). **일부러 제외한 것**: 지도 위 마커·경로선·시설 종류 색, 점수 단계 색(`scoreLevel`), 출발/도착 점 색, 경로 점수 축 색, 카카오 로그인 노랑(브랜드 색). 이 색들은 지도 표시와 짝을 이루거나 의미를 담고 있어 낮추면 구분이 어려워진다.
- **즐겨찾기 노랑**: 사이드바 "★ 즐겨찾기" 탭이 선택되면 노랑(배경 `#f6ecd0`, 글자 `#7a5a14`, 대비 5.4). 별 버튼·지도 마커는 원래 노랑이다. "최근 검색" 탭은 기존 색.
- 한계: 3001 포트는 카카오 지도 허용 도메인이 아니라(401) 지도 타일은 3000 에서만 뜬다. 지도 위 색 변화는 3000 에서 사용자가 직접 확인해야 한다.

## 긴급 수정 — 경기데이터드림 수집 보호 (fix/gyeonggi-api-guard) (2026-11-17)

**배경.** 5개 레이어 수집을 돌리다가 경기데이터드림(`openapi.gg.go.kr`)이 이 PC 의 IP 를 "보안 정책에 의해 차단"했다(HTTP 200 의 HTML 페이지). FlatMap 원본 수집기에는 호출 간 지연도, 차단 감지도, 실패 처리도 없었다. 차단 페이지를 받으면 조용히 멈추고 "수집 완료"(200)로 응답했으며, 그때까지 읽은 **부분 결과로 기존 데이터를 지우고 덮어썼다**(노인복지관+경로당이 6건으로 줄어든 이유). 이번 수정은 같은 사고를 막는 방어이며, 차단이 풀리는 것을 앞당기지는 않는다.

### 바뀜

- **차단 감지기**(`external.GyeonggiHostGuard`): 차단 페이지(HTML)를 한 번 받으면 기본 60분간(`gyeonggi.block-cooldown-minutes`) 이 호스트로 나가는 호출을 전부 멈춘다. FlatMap 원본 수집기와 01단계 수집 모듈(`JdkIngestionHttp`)이 같은 감지기를 쓰므로 어느 쪽에서 차단을 만나도 다른 쪽도 멈춘다. 상태는 메모리에만 있어 재시작하면 초기화되고, 이후 첫 호출이 다시 감지한다.
- **호출 간 지연·순차 실행**: 원본 수집기의 모든 경기데이터드림 호출 사이에 최소 500ms(`gyeonggi.page-delay-ms`)를 두고, 호출을 `synchronized` 로 직렬화해 관리자 수집이 겹쳐도 동시에 두드리지 않는다. 서비스가 바뀌는 지점에서도 간격을 지킨다.
- **실패는 전체 중단**: 호출 실패·차단·해석 실패·"데이터 없음"(INFO-200)이 아닌 결과 코드(키 오류, 호출 한도 초과 등)는 예외로 수집 전체를 멈추고 **DB 를 건드리지 않는다**. 예전에는 이런 경우가 "끝"으로 처리되어 0건 또는 부분 결과가 성공으로 저장됐다.
- **불필요한 마지막 호출 제거**: `list_total_count` 에 도달하면 다음 페이지를 부르지 않는다(최대 페이지 200 안전장치 추가). 호출 제한 시간은 5초에서 01단계 클라이언트와 같은 연결 10초·읽기 30초로 맞췄다(1000건 페이지가 5초 안에 끝나지 않아 실패하던 문제 완화).
- **응답**: 차단 상태에서 사고다발지·노인복지관·오르막차로 수집 엔드포인트는 500 대신 **503** 과 안내 문구(`code: UPSTREAM_BLOCKED`)를 돌려준다. `fetch/all` 은 기존대로 실패한 소스 이름만 `failedSources` 에 담는다. 응답·로그 어디에도 URL·키는 없다.

### 하지 않은 것

- 성남시만 서버에서 거르는 조건(`SIGUN_CD` 등 요청 파라미터)은 넣지 않았다. 경기데이터드림이 해당 서비스에서 그 파라미터를 지원하는지 확인되지 않았고, 차단 중이라 확인할 수도 없다. 지금도 경기도 전체를 페이지로 받아 앞단에서 성남만 거른다.
- 차단 해제는 코드로 할 수 없다. 해제될 때까지 이 PC 에서 이 호스트로 호출하지 않는다(문의: 경기도 사이버침해대응센터 031-8008-4114/4115).

### 테스트 (백엔드 435 → 449개)

- `GyeonggiHostGuardTest`(5): 실제 차단 응답 모양 감지, 정상 JSON·XML 은 차단 아님, 대기 시간 경과 전후, 호스트 판별, 차단 중에는 네트워크로 나가지 않으며 메시지에 URL·키 없음.
- `GyeonggiDataServiceTest`(9): 성남만 저장하고 전체 건수에서 멈춤, 중간 차단 시 DB 무변경과 이후 호출 차단, 첫 호출부터 차단, 두 번째 서비스 실패 시 첫 서비스 결과도 저장 안 함, 결과 코드 오류는 실패·INFO-200 은 정상 종료, 해석 불가 응답, 호출 간격, 메시지에 키·URL 없음.
- 방어를 하나씩 제거하는 변이로 테스트가 실제로 실패함을 확인했다(감지 제거 → 2건 실패, 대기 확인 제거 → 1건 실패).

### 알려진 한계

- 이 수정으로 실제 경기데이터드림 호출을 다시 검증하지 못했다(차단 중). 정상 응답 처리는 가짜 응답으로만 검증했다.
- 개발 PC 에서 백엔드(8080)가 떠 있는 채로 `./mvnw verify` 를 돌리면 PostgreSQL 연결 슬롯(기본 100개)이 모자라 일부 통합 테스트가 `remaining connection slots are reserved` 로 실패한다. 테스트 컨텍스트마다 커넥션 풀이 따로 생기기 때문이다. `-Dspring.datasource.hikari.maximum-pool-size=3 -Dspring.datasource.hikari.minimum-idle=1` 을 주면 449개 모두 통과한다. CI 와 새 clone 은 영향이 없다.

## 09 단계 — RAG 연동 지점 (rag-hook, 선택 단계) (2026-11-17) — 구현·로컬 검증 완료

**RAG 는 개발 중이며 이 저장소에는 RAG 서버도, 그 구현체도 없다. 이번 단계는 나중에 붙일 수 있는 연동 지점만 만들었고, 기본 설정에서는 백엔드 엔드포인트도 화면의 진입점도 보이지 않는다.** 점수는 검증되지 않은 설계 파라미터이며 위험도·안전도가 아니다.

### 추가됨

- **백엔드 연동 지점**(`com.flatmap.navigation.rag`): \`RagClient\` 인터페이스(\`isConnected()\`, \`ask(question)\`)와 "미연결" 기본 구현체(\`NotConnectedRagClient\`, 외부 호출 없음). 나중에 RAG 서버를 붙일 때는 \`RagClient\` 구현 빈을 \`@Primary\` 로 등록한다(구현체가 지킬 규칙 — 타임아웃, 내부 정보 없는 \`RagUnavailableException\`, 로그에 URL·키·본문 금지 — 를 인터페이스 문서에 적었다).
- **\`rag.enabled=false\` 가 기본값**(환경변수 \`RAG_ENABLED\`, 빈 값도 꺼짐). 꺼져 있으면 이 패키지의 빈과 \`/api/rag/*\` 가 **등록되지 않아 404** 다(로그인한 사용자도 마찬가지). 켜면 기본 "미연결" 빈이 등록되고, \`GET /api/rag/status\` 는 \`{enabled:true, connected:false}\`, \`POST /api/rag/ask\` 는 로그인이 필요하며(401) 질문이 1~500자가 아니면 400, 미연결이면 **503 \`RAG_NOT_CONNECTED\`(메시지: "RAG 기반 안내는 개발 중이며 아직 연결되지 않았습니다.")** 다. 구현체 실패는 내부 메시지를 싣지 않는 일반화된 502 \`RAG_UNAVAILABLE\`, 그 밖의 예외는 일반화된 500.
- **프론트 진입점**: 서버가 \`enabled && connected\` 라고 알릴 때만 "질문하기(Q&A)" 접이식 섹션과 질문·답변 패널을 그린다. 상태를 모르거나(응답 전), 꺼짐(404), 오류, **켜짐+미연결**이면 아무것도 렌더하지 않는다. 즉 이 저장소의 상태에서는 \`RAG_ENABLED=true\` 로 켜도 화면에 나타나지 않는다. 판단은 순수 함수(\`shouldShowRagEntry\`, 엄격한 \`true\` 만 인정)로 분리해 테스트한다.
- \`RAG_ENABLED\` 를 \`.env.example\`(주석 처리, 값 없음)·\`docs/ENVIRONMENT.md\` 에 추가했다. README 의 "개발 중" 항목 아래에 연동 지점이 준비됐을 뿐 서버·구현은 저장소에 없다는 설명을 붙였다(\`구현된 기능\` 절에는 RAG 가 없다).
- **문구 가드 테스트**(\`RagWordingGuardTest\`): README·환경변수 문서·\`.env.example\`·화면(컴포넌트·페이지)·설정 파일에서 "RAG" 를 언급하는 모든 줄은 "개발 중/미연결/저장소에 없" 을 함께 말해야 하고, README 의 "구현된 기능" 절에는 RAG 가 없어야 한다. README 에 RAG 를 기능처럼 쓴 문장을 임시로 넣어 두 검사가 모두 실패하는 것을 확인한 뒤 원복했다.
- **테스트**(백엔드 423 → 435개, 프론트 91 → 105개): 기본 꺼짐(빈 없음, 엔드포인트 404, 로그인해도 404), 켜면 미연결 빈 등록·status·401·400·503, 가짜 구현체를 \`@Primary\` 로 붙이면 기본 빈 대신 쓰임·성공 응답·실패의 일반화(내부 메시지 비노출), 프론트 진입점 표시 여부·질문 검증·오류 문구·렌더.

### 검증

- 패키징한 JAR 로 확인: 기본(설정 없음)은 \`/api/rag/status\`·\`/api/rag/ask\` 가 404, 다른 공개 API 는 정상. \`RAG_ENABLED=true\` 는 status \`{"enabled":true,"connected":false}\`, 로그인 없는 질문 401. \`RAG_ENABLED\` 가 빈 값이면 404(꺼짐).
- 새 clone(\`.env\` 없음)에서 CI 와 같은 명령 통과: \`./mvnw -B -ntp verify\` 435개(1개 건너뜀: DEM 파일이 있을 때만 도는 대조 테스트), \`npm ci\`·\`npm test\` 105개·\`npm run build\`, 위생 점검 0건.

### 설계 판단 (사용자 승인 전)

- **켜짐+연결됨일 때만** 화면에 보인다는 규칙을 문서의 "비활성일 때 숨김"보다 한 단계 더 엄격하게 잡았다. 서버 구현이 이 저장소에 들어오기 전에는 켜도 보이지 않아야 "있는 기능으로 표시하지 않는다"(규칙 6)를 지킨다.
- 기본 "미연결" 빈은 \`@ConditionalOnMissingBean\` 이 아니라 **항상 등록하고 실제 구현체를 \`@Primary\`** 로 두는 방식이다. 일반 설정 클래스에서는 \`@ConditionalOnMissingBean\` 이 등록 순서에 따라 두 빈을 동시에 만들 수 있어서다.
- 질문은 로그인한 사용자만 할 수 있게 해 두었다(실제 호출이 생기면 남용·비용을 막기 위해). 길이 상한 500자와 필드 이름(\`question\`)은 서버가 정한 값이며 RAG 서버가 붙을 때 바뀔 수 있다.

### 정정 (08 단계 기록에 대해)

- 08 단계 CHANGELOG 를 커밋한 뒤 마지막 테스트를 돌리기 전에 그 항목의 "ngrok" 이라는 단어가 08 에서 만든 ngrok 재유입 방지 검사(\`DeployFilesTest\`)에 걸리는 상태였다(변경 기록이 과거 의존을 언급하기 때문). 이번 단계 테스트에서 처음 드러났고, 그대로 push 했다면 CI 의 backend 잡이 실패했을 것이다. 변경 기록(\`CHANGELOG.md\`)은 검사에서 제외하고 나머지 파일의 검사는 유지했다(다른 문서에 넣으면 여전히 실패함을 확인).

### 한계 / 후속

- RAG 서버 연동은 **구현되지 않았다.** \`RagClient\` 구현체, 실제 외부 호출, 타임아웃·오류 처리의 실제 코드는 RAG 서버를 붙이는 단계에서 만들어야 한다(그때 이 연동 지점의 요청·응답 형식(\`question\`, \`answer\`, \`sources\`)을 RAG 서버에 맞게 정한다).
- 켜짐+연결됨일 때의 화면(질문 패널)은 렌더 테스트만 있고 실제 서버와 붙여 눈으로 확인하지 못했다(연결된 서버가 없다).
- GitHub Actions 는 여전히 한 번도 실행되지 않았고, 08 의 Docker 이미지 빌드·compose 실행도 미확인이다(08 단계 기록 참고).

## 08 단계 — 배포 준비 (deploy) (2026-11-17) — 문서·이미지 정의 준비 완료, 이미지 빌드는 Docker 가 없어 실행하지 못함, 실제 배포는 하지 않음

클라우드에 종속되지 않는 배포 산출물(Dockerfile 2개, 운영/개발 compose, 환경변수 문서, 배포 가이드)을 준비했다. **실제 배포는 하지 않았고 배포 대상 클라우드는 정해지지 않았다.** 플랫폼 이름은 아직 `[이름 미정]`이다. **점수는 검증되지 않은 설계 파라미터이며 위험도·안전도가 아니다.** RAG 는 개발 중이며 아직 연동되지 않았다.

### 추가됨

- **`backend/Dockerfile`**: 멀티스테이지(Maven 3.9 + JDK 17 빌드 → `eclipse-temurin:17-jre-alpine` 실행), 비루트 사용자, 운영 프로필 기본, readiness 헬스체크. 비밀값은 이미지에 없고 실행할 때 환경변수로 주입한다.
- **`frontend/Dockerfile` + `frontend/nginx/default.conf.template`**: 멀티스테이지(Node 22 + Vite 빌드 → `nginx-unprivileged`, 8080), `/api` 는 같은 출처로 백엔드에 프록시(요청 때마다 DNS 재해석 — 백엔드 컨테이너만 다시 만들어져도 502 가 나지 않게). **관리용 경로(`/api/data/*`, `/api/walk-difficulty/*`, `/api/safety`·`/api/steep-slope`·`/api/density-grid` 의 쓰기)는 공개 주소에서 막는다.** 보안 헤더(nosniff, Referrer-Policy, X-Frame-Options).
- **`docker-compose.prod.yml`(운영)**: DB(PostGIS 16) 포트를 호스트에 열지 않고, 백엔드는 호스트의 `127.0.0.1` 에만 열며(서버 안 관리용 호출), 공개 진입점은 프론트 하나. 필수 변수가 없으면 시작하지 않고, 환경변수는 전부 `.env` 참조(compose 에 비밀값 리터럴 없음), `cap_drop: ALL`, `no-new-privileges`, 로그 회전. 데이터 파일(DEM·CSV)은 이미지가 아니라 읽기 전용 볼륨(`DATA_DIR`).
- **`docker-compose.yml`(개발)**: 기본은 DB 만, `--profile stack` 이면 전체 스택을 개발 설정(HTTP, localhost)으로.
- **`.dockerignore`**: `.env`·`application-local.*`·DEM·`target/` 가 이미지 빌드 컨텍스트에 들어가지 않는다.
- **`docs/ENVIRONMENT.md`**: 키 이름·용도·필수 여부(값 없음). **`docs/DEPLOY.md`**: 이미지 빌드, 시크릿 생성 방법, 카카오 콘솔 설정, DB 초기화(Flyway 자동), 헬스체크, 첫 데이터 채우기, 운영(백업·업데이트·롤백), 문제 해결, 사용자가 채울 값. README 에 배포 섹션.
- **CI**: Docker 이미지 빌드, 운영 compose 검증(더미 값), 비루트 확인 잡(`docker`). 배포 파일을 작성한 PC 에는 Docker 가 없어 **첫 push 에서 처음 실행된다.**
- **테스트**(백엔드 391 → 423개, 프론트 88 → 91개): 배포 파일 구조 검사(`DeployFilesTest` — DB 포트 비공개, 비루트, 시크릿 리터럴 없음, 필수 변수 강제, 관리 경로 차단, Dockerfile 명령어 문법, ngrok·하드코딩 호스트 없음), 환경변수 문서가 `.env.example`·compose·`application*.properties` 가 읽는 변수와 일치하는지(`EnvDocsConsistencyTest`), 운영 설정 검사, 헬스체크, 쿠키 속성, 로그인 주소. 위험한 변경(DB 포트 공개, `USER root`, 시크릿 리터럴, 필수 변수 제거, 깨진 Dockerfile 줄)을 일부러 넣어 테스트가 실패하는 것을 확인한 뒤 원복했다.

### 변경됨 (운영 배포를 위해 제가 판단해서 넣은 것 — 사용자 승인 전)

- **ngrok**: 추적 파일에는 이미 없었다(05 단계에서 정리됨). 이번에 저장소 전체에 ngrok 참조가 다시 들어오지 못하게 테스트로 고정했다.
- **로그인 주소 기본값**: 프론트의 카카오 로그인 시작 주소가 `http://localhost:8080` 으로 하드코딩돼 있어 환경변수를 안 주면 운영에서도 localhost 를 가리켰다. 기본을 같은 출처(`/api/auth/kakao/login`)로 바꿨다(운영은 nginx, 개발은 Vite 프록시가 처리). `VITE_BACKEND_URL` 은 백엔드가 다른 출처일 때만 쓰는 선택값이다.
- **헬스체크**: `spring-boot-starter-actuator` 를 추가하고 `health` 만 연다(DB 포함, 세부 정보 없음, liveness/readiness). 다른 actuator 경로는 404.
- **운영 프로필(`prod`)**: `FRONTEND_URL`·`KAKAO_OAUTH_REDIRECT_URI` 의 개발용 기본값(localhost)을 없애고, 기동 시 설정을 검사한다(localhost 주소, 32바이트 미만 JWT 비밀, 16자 미만 관리 키면 기동 실패, 메시지에는 설정 이름만). 파일 로그를 끄고(표준 출력) 앱 로그 수준을 INFO 로 둔다(기본 설정은 DEBUG).
- **로그인 쿠키**: `SameSite=Lax` 를 붙이고, 운영(HTTPS)에서는 `Secure` 를 붙인다(`app.cookie-secure`, 운영 기본 true). 개발(HTTP)은 그대로 동작.

### 검증 (Docker 없이 할 수 있는 만큼)

- **패키징한 JAR 를 `.env` 없는 빈 디렉터리에서 환경변수만으로 운영 프로필로 기동**: 빈 DB 에 Flyway 가 V1~V5 를 적용하고 기동 성공. `/actuator/health`·liveness·readiness 가 UP, 다른 actuator 경로 404, 로그아웃 쿠키가 실제 Tomcat 응답에서 `Secure; HttpOnly; SameSite=Lax`, 관리 API 는 키 없음·틀린 키 401, 맞는 키 200, CORS 는 `FRONTEND_URL` 출처만 허용. 파일 로그가 생기지 않았고 로그에 DEBUG 줄 0건, JWT 비밀·관리 키·DB 비밀번호가 로그에 0건.
- **잘못된 운영 설정은 기동하지 않음**: `FRONTEND_URL` 누락 → 실패(플레이스홀더 오류), localhost 주소 + 짧은 JWT 비밀 + 빈 관리 키 → "운영 설정이 올바르지 않습니다: …" 로 실패하고 로그에 값이 없음.
- 새로 clone 한 작업 트리(`.env` 없음)에서 CI 와 같은 명령 통과: `./mvnw -B -ntp verify` 423개(1개 건너뜀: DEM 파일이 있을 때만 도는 대조 테스트), `npm ci`·`npm test` 91개·`npm run build`, 위생 점검. Dockerfile 의 의존성 선받기(`dependency:go-offline`)와 `package -DskipTests` 도 같은 명령으로 확인.
- 이 검증을 위해 테스트 DB(`flatmapv2_test`)의 앱 소유 테이블을 지웠다(PostGIS 확장은 그대로). 테스트 DB 는 테스트 실행마다 비워지는 용도다.
- **저장소 위생 점검**(새 배포 파일 포함): 비밀값 정확 일치(알려진 값 18개)·패턴·올라가면 안 되는 파일명·대용량(허용 목록 1건 외) 모두 0건.

### 확인하지 못한 것 / 한계

- **이미지 빌드, `docker compose up`(개발·운영 모두), 컨테이너 헬스체크, nginx 컨테이너의 실제 프록시·SPA 동작은 한 번도 실행하지 못했다**(Docker 없음). 위 검증은 파일 구조·문법과 JAR 기동까지다. nginx 설정 문법(`nginx -t`)도 확인하지 못했다. CI 의 `docker` 잡이 첫 push 에서 이미지 빌드와 일부를 확인한다.
- GitHub Actions 는 여전히 한 번도 실행되지 않았다(07 단계와 같음).
- 운영 compose 는 `POSTGRES_USER`(슈퍼유저) 하나를 앱 계정으로 쓴다. 최소 권한(비슈퍼유저 + 사전 생성된 PostGIS 확장)은 가이드에 권고만 적었다.
- TLS 는 이 스택에 없다(앞단에서 처리). 로그인 쿠키가 `Secure` 라 HTTPS 가 아니면 로그인이 되지 않는다.
- `GYEONGGI_SECURITY_LIGHT_API_KEY`·`GYEONGGI_ACCIDENT_ICY_API_KEY`·`GYEONGGI_ACCIDENT_NORMAL_API_KEY`·`GYEONGGI_SENIOR_WELFARE_API_KEY`·`GYEONGGI_SENIOR_CENTER_API_KEY` 는 설정으로 읽기만 하고 호출하는 코드가 없다. 문서에 "현재 코드에서 사용하지 않음"으로 표시했고 삭제는 하지 않았다(사용자 판단).
- 관리용 호출은 공개 주소에서 막혀 서버 안(`127.0.0.1`)에서만 가능하다. 클라우드에서 서버에 접속할 수 없는 구조면 일회성 작업 수단이 필요하다(가이드 12절).

## 07 단계 — 품질과 문서 (quality-docs) (2026-11-17) — 구현·로컬 검증 완료, GitHub Actions 실행은 push 후 확인 필요

CI, 핵심 로직 테스트 보강, README, 저장소 위생 점검을 마쳤다. **점수는 검증되지 않은 설계 파라미터이며 위험도·안전도가 아니다.** RAG 는 개발 중이며 아직 연동되지 않았다.

### 추가됨

- **GitHub Actions CI**(`.github/workflows/ci.yml`, 모든 push·PR): ① 백엔드 — PostGIS 서비스 컨테이너(`postgis/postgis:16-3.4`, DB 이름 `flatmapv2_test`)와 JDK 17 에서 `./mvnw verify`, `TEST_DB_*` 는 CI 안에서만 쓰는 가짜 값 ② 프론트 — Node 22 에서 `npm ci` → `npm test`(Vitest) → `npm run build`(Vite) ③ 저장소 위생 점검. 비밀값은 저장소에 없고 Secrets 도 쓰지 않는다.
- **저장소 위생 점검 스크립트**(`scripts/check-repo-hygiene.sh`, CI 에서도 실행): 대용량 추적 파일(1MiB 초과, 이유를 적은 허용 목록 1건), 올라가면 안 되는 이름의 추적 파일(`.env`·키·덤프·DEM·`application-local.*`), 비밀값 패턴, 그리고 로컬에 `.env`/`application-local.properties` 가 있을 때 이름이 KEY/SECRET/TOKEN/PASSWORD 인 값(8자 이상)의 **정확 일치**를 추적 파일과 전체 git 이력에서 검색한다. 값은 어디에도 출력하지 않고 키 이름·파일 경로·커밋 해시만 알린다. 임시 저장소에 일부러 새는 상황(큰 파일, 키 파일명, AWS 키 형태, 비밀번호 대입문, 값이 든 추적 파일, 삭제된 파일 이력)을 만들어 모두 잡고 종료 코드 1 을 내는지 확인했다.
- **README.md**: 아키텍처 다이어그램(Mermaid, `mermaid.parse` 로 문법 검증), 구현된 기능만의 목록, 점수 모델·DEM 30m 평활화·급경사지 한계, RAG 개발 중 표기, 로컬 실행 방법(`.env` 키 이름, Docker Compose 또는 로컬 PostgreSQL, 테스트 DB), 스크린샷 자리 표시(파일 이름만 정해 둠, 이미지는 사용자가 추가), `[이름 미정]` 표시, 알려진 한계.
- **테스트 보강**(백엔드 358 → 391개, 프론트 78 → 88개): JWT 발급·검증(위조·만료·alg=none·약한 키), 로그인 쿠키 인증과 **사용자 간 즐겨찾기 격리**(다른 사용자의 것을 보거나 지울 수 없음, 404), 카카오 로그인 흐름(실패해도 내부 메시지가 새지 않음), 거리 계산(haversine)과 기상청 격자 변환(공개 격자표 값), 성남 범위 필터 경계, 최근 검색 기록, 경로 오류 문구. 격리 테스트는 컨트롤러를 일부러 깨뜨려(소유자 조건 제거) 실패하는 것을 확인한 뒤 원복했다.

### 변경됨

- **Maven wrapper 를 `only-script` 방식으로 교체**: `.gitignore` 로 jar 를 제외해 둔 상태에서 구형 `mvnw` 는 새 clone 에서 `ClassNotFoundException: MavenWrapperMain` 으로 실행되지 않았다(README 와 CI 의 `./mvnw` 가 깨지는 문제). jar 없이 스크립트가 Maven 을 내려받는 방식으로 바꿨고, `.gitattributes` 로 `mvnw` 는 LF, `mvnw.cmd` 는 CRLF 로 고정했다.

### 저장소 위생 점검 결과 (2026-11-17)

| 항목 | 결과 |
|---|---|
| 추적 파일 / 커밋 / 브랜치 | 262개 / 28개 / 9개(전 브랜치 이력 검색) |
| 비밀값 정확 일치(`.env`·`application-local.properties` 의 KEY/SECRET/TOKEN/PASSWORD 이름 값 18개) | 추적 파일 0건, git 이력 0건 |
| 비밀값 패턴(개인 키 블록, AWS·GitHub·Slack 토큰, 20자 이상 리터럴 대입) | 0건 (테스트용 가짜 값 `test-only…`·`…should-never-leak` 는 이름으로 구분해 제외) |
| 올라가면 안 되는 이름의 추적 파일(`.env`, 키·인증서, 덤프, `.tif`, `application-local.*`) | 0건 |
| 1MiB 초과 추적 파일 | 허용 목록 1건: 행정안전부 급경사지 현황 CSV(2.4MB, 공개 데이터이며 `CsvImportService` 가 리소스로 읽음). 그 밖 0건 |

### 검증 (로컬에서 CI 와 같은 명령)

- `.env` 없이 **새로 clone 한 작업 트리**(`git worktree`)에서 `TEST_DB_*` 환경변수만 주고 `./mvnw -B -ntp verify` → 391개 통과(1개 건너뜀: 실제 DEM 파일이 있을 때만 도는 대조 테스트), jar 생성. `npm ci` → `npm test` 88개 통과 → `npm run build` 성공, 위생 점검 0건.
- 워크플로 YAML 은 파서로 읽어 잡(backend/frontend/hygiene), 서비스 이미지, 테스트 DB 이름이 `_test` 로 끝남을 확인했다.

### 한계 / 후속

- **GitHub Actions 는 아직 실제로 돌려 보지 못했다**(원격 push 전). 위의 로컬 재현으로 대신했고, 원격에서 실패하면 다음 보고에 포함한다.
- CI 는 JDK 17 · PostgreSQL 16(PostGIS 3.4)을 쓰지만 이 PC 는 JDK 25 · PostgreSQL 18 이라 그 조합은 로컬에서 돌려 보지 못했다(프로젝트는 `--release 17` 로 컴파일되어 API 범위는 17 로 제한된다). Docker 가 없어 `docker-compose.yml` 도 실행해 보지 못했다.
- 위생 점검의 패턴 검색은 정규식 기반이라 모든 비밀값 형태를 잡지는 못한다. 정확 일치 검색은 로컬에 값이 있는 키(이름에 KEY/SECRET/TOKEN/PASSWORD 가 든 것)만 대상이다. DB 사용자 이름·URL 은 비밀값으로 취급하지 않았다.
- 프론트 테스트는 순수 함수와 정적 렌더(문구) 중심이며 지도 오버레이와 훅은 자동 테스트가 없다. README 의 Mermaid 다이어그램이 GitHub 에서 그려지는 모습과 스크린샷은 확인하지 못했다(스크린샷은 사용자가 추가).
- 01 단계의 미해결 2건(성남시 CCTV 활용 신청, 보안등 전체 수집)은 그대로이며 README 의 알려진 한계에 적었다.

## 06b 단계 — FlatMap 정렬 (우회 점수 규칙 삭제 · 오르막 최소 옵션 · 안전정보 레이어 복원) (2026-11-17) — 구현·자동 테스트 완료, 지도 화면은 사용자 확인 필요

기반은 FlatMap 이고 SafetyRoad 는 UI 뼈대라는 방향에 맞춰, 앞 단계에서 제가 판단해 바꾼 부분 중 FlatMap 과 어긋난 것을 정리했다.
**점수는 검증되지 않은 설계 파라미터이며 위험도·안전도가 아니다.** RAG 는 개발 중이며 아직 연동되지 않았다.

### 제거됨

- **우회 채택의 점수 하락 규칙(`detour.max-score-drop`, 10점)**: 04 단계에서 추가했던 규칙이다. 점수 차이를 키우지 못하고 FlatMap 에 없던 임의 기준이라 삭제했다. 우회 채택은 FlatMap 원래 기준(급경사지 경고 구간이 줄었는가)만 본다. 응답은 계속 `originalTotalScore` 와 `totalScore` 를 함께 주고, 화면은 우회 경로 점수가 원경로보다 낮으면 그 폭을 알린다(점수는 채택 기준이 아님).

### 추가됨

- **"오르막 최소" 경로 옵션 복원**: FlatMap 의 경로 옵션은 안전 우선 / 최단 거리 / 오르막 최소(`FLAT`, T Map searchOption 30)였는데, SafetyRoad 후보 3종(0/4/10)을 도입하면서 화면에서 사라져 있었다. 후보 경로에 searchOption 30("계단 제외")을 더해 4종으로 늘리고, 이동 기준에 "오르막 최소"를 추가했다. 선택 기준은 경로의 DEM 경사(보행난이도 B축) 점수가 가장 높은 경로이며, B축 데이터가 없으면 계단 제외 경로, 그것도 없으면 서버 추천 경로를 고른다. 우회 요청 옵션에도 30 → `FLAT` 을 연결했다.
- **FlatMap 안전정보 레이어 5종**: 사고다발지·교통약자 보호구역(법정 명칭)·장애인복지시설·그늘막·노인복지관. 색·이모지·API 는 FlatMap 원본 그대로이고, 디자인은 현재 레이어 타일에 맞췄다. 지도 영역 안의 항목만 줌 레벨별 상한(원본과 같은 구간)까지 그리고, 정보 창은 `textContent` 로만 채운다. 데이터가 없거나 불러오지 못하면 타일에 "등록된 데이터 없음 / 불러오지 못했습니다"로 표시한다. 위치 안내용이며 경로 점수에는 반영되지 않는다.
- **오르막길 구간 목록**(FlatMap 오르막길 탭 복원): 지역 필터와 경사 큰 순/이름 순 정렬, 항목을 누르면 지도가 그 위치로 이동하고 급경사지 경고 레이어가 켜진다. 02 단계에서 등급(`riskLevel`)을 없앴으므로 원본의 위험도 필터·배지는 되살리지 않고 경사도(°)·미측정·산지형만 보여 준다.
- 테스트: 백엔드 358개(레이어 조회 계약 4개 추가), 프론트 78개(레이어 설정·줌 상한·영역 필터, 오르막 최소 선택, 오르막길 목록·렌더).

### 유지한 것 (판단 기록)

- **03 단계의 SafetyRoad 로직(후보 경로 API, A축 점수, 시설 밀도 격자)은 유지**했다. FlatMap 에는 시설 점수·밀도 격자가 없어 겹치지 않고, FlatMap 의 경로 조회·날씨·우회·급경사지 경고는 그대로 남아 있다(`/api/route/walk` 단일 경로 API 포함). 겹침으로 발견한 것은 FlatMap 의 "오르막 최소" 옵션 하나였고 위에서 복원했다.
- 경찰시설은 가중해서 반영한다(×5, 한 번만 셈). 홈 화면 문구는 현재 방향을 유지하고, 두 플랫폼 병합이 끝난 뒤 최종 점검 때 다시 정한다.

### 한계 / 후속

- **지도 위 마커·정보 창은 이 환경에서 눈으로 확인하지 못했다**(카카오 도메인 미등록으로 지도가 뜨지 않음). 패널 화면(타일·개수 표시·목록 정렬)은 임시 데이터를 넣어 브라우저로 확인했고 데이터는 지웠다. 사용자 포트(3000)에서 마커와 정보 창 확인이 필요하다.
- 개발 DB 에는 이 레이어들의 데이터가 아직 없다(0건). 기존 관리용 수집 엔드포인트(`POST /api/safety/fetch/*`, `/api/steep-slope/fetch/*`)로 채워야 보인다.
- 후보가 4종이 되어 T Map 순차 호출의 최악 지연이 호출당 읽기 타임아웃 × 4 다(프론트 요청 타임아웃 30초 안). 병렬화는 하지 않았다.
- T Map searchOption 30 은 "최단+계단 제외"라 경사를 최소화하는 옵션이 아니다. FlatMap 이 붙였던 "오르막 최소" 이름은 유지하되 선택 기준은 B축 점수로 바꿨고, 경로 카드의 후보 이름은 "계단 제외"로 보인다.
- 이번에도 옮기지 않은 것: 모바일 전용 프로필 패널, 위험도 통계(위험/주의/안전 구간 개수). FlatMap 의 5개 지도 테이블은 아직 JPA(좌표 컬럼) 조회이며 PostGIS 쿼리로 옮기는 것은 후속이다(거리 계산이 없는 전체 조회라 규칙 4 에는 걸리지 않는다).

## 06 단계 — 보행난이도(B축) (walk-difficulty) (2026-11-17) — 구현·자동 테스트 완료

DEM(Copernicus GLO-30)에서 미리 계산한 경사 격자를 DB 에 두고, 경로를 따라 경사를 읽어 **B축(보행난이도)** 을 점수에 합류시켰다. 보행 유형(일반 / 보행약자)에 따라 임계값이 달라진다.
**점수는 검증되지 않은 설계 파라미터이며 위험도·안전도가 아니다.** 이 축의 임계값은 **급경사지(붕괴위험 지정지) 데이터에 적용하지 않는다** — 두 축은 별개다. RAG 는 개발 중이며 아직 연동되지 않았다.

### 추가됨

- **경사 격자 파이프라인**: DEM 읽기 → 3×3 Horn 경사(각도 °) → `walk_slope_cell`(V5) 전체 교체. 셀 하나 = DEM 픽셀 하나(1 arc-second ≈ 30m), 성남 범위(위도 37.33~37.53, 경도 127.04~127.20) 416,017셀. `POST /api/walk-difficulty/grid/rebuild`(관리용, `X-Admin-Api-Key`) 가 적재하며 DEM 위치는 요청이 아니라 설정(`walk.dem.path`, 환경변수 `WALK_DEM_PATH`)에서만 읽는다. **멱등**(두 번 실행해도 행 수·값 동일)이고 한 트랜잭션이라 실패하면 기존 격자가 남는다. 동시에 둘이 돌지 않는다(409).
- **GeoTIFF 리더**(`GeoTiffDemReader`): JDK 의 TIFF 플러그인이 지원하지 않는 부동소수점 예측기를 포함해 Copernicus COG 형식(타일·Deflate·32비트 float)을 직접 읽는다. 지원하지 않는 형식은 조용히 잘못 읽지 않고 거부한다. 실제 DEM 의 고도가 기존 추출 도구(Node geotiff) 결과와 일치함을 테스트로 대조한다(DEM 파일이 있을 때만 실행).
- **샘플링 쿼리**: 경로 전체를 한 번의 SQL 로 처리(경로를 EPSG:5186 으로 옮겨 30m 간격 지점을 만들고 셀 인덱스(PK)로 조인). 경로당 1회, 정점·샘플 수와 무관(N+1 없음).
- **임계값**(경사 각도 °, `walk.threshold.*` 설정): 일반 4.76 / 7.13, 보행약자 3.18 / 4.76 (이하 양호, 이하 주의, 초과 위험 — 경계값은 낮은 등급에 속한다). 구간별 양호/주의/위험 **비율**(합 정확히 100%)과 점수(= 100 × Σ 비율 × 구간 가중, 양호 1.0 / 주의 0.5 / 위험 0.0)를 `breakdown` 의 `axis: "B"` 항목(`detail`: 모드, 임계값, 비율, 평균·최대 경사, 커버리지, 한계 문구)으로 내려 준다.
- **보행 유형 선택**: `GET /api/routes/safe?mode=GENERAL|WEAK`, `POST /api/route/walk[/detour]` 본문의 `walkMode`. 없으면 `walk.default-mode`(GENERAL), 알 수 없는 값은 400 `INVALID_WALK_MODE`. `scoreModel` 에 `walkMode` 와 유형별 `walkThresholds` 가 들어가 화면 안내가 서버 설정과 어긋나지 않는다. 우회는 원경로와 우회 경로를 같은 유형으로 비교한다.
- **UI**: 보행약자/일반 선택 활성화(선택 기억은 `localStorage`, 실패해도 기본값으로 동작), 적용 조건 칩, 점수 내역에 B축 구간 비율·기준 각도·평균/최대 경사, 점수 가이드와 홈 화면 문구 갱신("개발 중"은 RAG 만 남음).
- 설정: `walk.grid.*`, `walk.dem.path`, `walk.sample.*`, `walk.min-coverage`, `walk.default-mode`, `walk.threshold.*`, `walk.score.weight.*`, `score.route.axis-weight.b`(1.0).
- **테스트**: 백엔드 357개(기준선 272 + 85), 프론트 Vitest 55개(기준선 48 + 7). 임계값 경계(바로 아래/정확히/바로 위, DB 왕복 포함), 모드별 차이(WEAK ≤ GENERAL), 비율 합 100%, 점수 단조성, 경로당 쿼리 1회, 파이프라인 멱등·실패 시 보존, 급경사지 코드와 보행 임계값의 분리(소스 검사).

### 변경됨

- 총점의 축 구성이 A(야간 시설 노출) + **B(보행난이도)** + C(날씨 보정)이 되었다. **B 는 경로마다 달라서 추천 경로 선택에 영향을 준다**(C 는 후보끼리 같아 영향이 없었다). B 가중치(1.0)는 A 와 같게 둔 설계 값이다.
- `SafeRouteService.evaluate(coordinates, mode)`: 경로마다 시설 집계 1회 + 경사 샘플링 1회(+ 급경사지 경고 1회).

### 제거됨

- `util/SlopeRisk`: 급경사지 경사도에 보행 임계값(3.18°/4.76°)으로 등급을 매기던 도우미. 호출하는 곳이 없었고, 두 축을 섞지 않는다는 원칙과 맞지 않아 삭제했다. 임계값은 이제 설정 한 곳에만 있다.

### 한계 / 후속

- **경사가 실제보다 낮게 나오는 경향이 있다.** 약 30m 격자 DEM 에서 이웃 3×3 평균으로 구하므로 평활화된다. 급경사지 81건(성남 범위, 기존 추출 결과 있음)에서 이번 방식의 평균은 17.5°, 기존 도구(인접 픽셀 단순 차분의 최대)는 22.4° 였다 — 방법이 달라 정답 비교는 아니지만 평활화 방향과 맞는다.
- DEM 은 지표면(건물·수목 포함) 모델이라 짧은 급경사·계단·옹벽은 반영하지 못하고 건물·수목 가장자리 값은 어긋날 수 있다. 격자는 성남 범위만 덮으며 **범위 밖이거나 샘플의 50% 미만에만 값이 있는 경로는 B축을 점수에 넣지 않는다**(0점·100점으로 채우지 않음).
- 임계값·구간 가중·B 가중치는 검증되지 않은 설계 파라미터다. 실제 보행 난이도나 보행약자의 이동 가능 여부를 보증하지 않는다.
- 성남 범위 전체 셀의 경사 중앙값은 약 10°다(산지 포함이라 도로 경로와 다르다). 시가지 직선 경로 3개(실제 도로가 아님)로 본 양호 구간 비율은 일반 기준 54~73%, 보행약자 기준 30~54% 였고 세 경로 모두 보행약자 쪽이 낮았다. 남한산성 방향 직선 1개는 평균 17.7° 로 두 기준 모두 위험 구간이 80% 이상이었다.
- 개발 DB(`flatmapv2`)에는 격자를 적재해 두었다(416,017셀, 약 4초). 다른 DB 에서는 마이그레이션 후 `POST /api/walk-difficulty/grid/rebuild` 를 한 번 실행해야 B축이 나타난다(테스트 DB 는 매번 비워지므로 테스트가 직접 픽스처를 넣는다).
- T Map 실호출로 만든 경로의 B축은 확인하지 않았다(자동 테스트는 T Map 을 모킹하고 PostGIS 는 실제를 쓴다). 지도 화면에서 보행 유형 선택과 점수 내역은 사용자 확인이 필요하다.
- README 는 07 단계에서 만든다. 위 한계 문구는 그때 README 에도 옮겨야 한다.

## 05 단계 — 프론트엔드 (Vite 전환 + SafetyRoad UI 포팅 + FlatMap 기능 이식) (2026-11-10) — 구현·자동 테스트 완료, 지도 화면은 사용자 확인 필요

프론트를 CRA에서 Vite로 옮기고, SafetyRoad UI(홈·우선순위·SOS·실시간 위치)를 뼈대로 삼아 FlatMap 기능(로그인·즐겨찾기·날씨·우회·급경사지)을 새 API 계약에 맞게 이식했다.
**경로 점수는 검증되지 않은 설계 파라미터이며 위험도·안전도가 아니다.** 화면 문구도 그렇게 쓴다. 보행난이도(B)와 보행약자/일반 모드, RAG 는 개발 중이며 아직 연동되지 않았다(→ B축과 모드는 06 단계에서 구현).

### 추가됨

- **Vite 전환**(별도 커밋): `react-scripts` 제거, `vite` + `@vitejs/plugin-react`. dev 프록시 `/api` → `localhost:8080`(백엔드를 다른 포트로 띄우면 `VITE_DEV_PROXY_TARGET`). 환경변수는 `VITE_*`(`VITE_KAKAO_API_KEY`, `VITE_API_BASE_URL`, `VITE_BACKEND_URL`). 빌드 출력은 그대로 `frontend/build`.
- **SafetyRoad UI 포팅**: 홈 화면, 우선순위(안전 우선 / 균형 / 빠른 길), 실시간 위치(`watchPosition`), SOS(10초 카운트다운 후 `tel:112`), 경로 결과 카드, 점수 가이드.
- **점수 표시**: 총점(`totalScore`)과 축별 `breakdown`(축 이름·점수·가중치), CCTV·보안등·경찰시설 개수. 등급 라벨은 "높은 편 / 보통 / 낮은 편"(표시용 구간, "안전·위험" 단정 표현 없음).
- **레이어**: CCTV·보안등·경찰시설(지도 영역 조회), 시설 밀도 격자, 급경사지 "붕괴위험 경고". 내 위치가 "시설 밀도 낮음" 격자에 들어오면 "시설이 적은 구역 안내"(위험하다는 뜻이 아니라 수집된 시설 데이터가 적다는 안내).
- **FlatMap 기능**: 카카오 로그인/로그아웃, 즐겨찾기(입력칸 ★·지도 마커), 최근 검색, 날씨(경로별 카드 + 지도 위 현재 위치 칩), 경로 주변 붕괴위험 지정 급경사지 목록, 우회 경로(경고 구간·점수 변화 표시).
- **보행약자 / 일반 선택**은 자리만 있고 비활성("준비 중"). 경로 점수에 영향을 주지 않는다.
- **백엔드 선행 계약**(별도 커밋): `GET /api/safety/facilities`(지도 영역 + 종류 + `limit`, 넘치면 `truncated`), `GET /api/routes/safe` 경로마다 `riskSections`(급경사지 경고, 점수에 섞지 않음), 우회용 옵션 `MAIN_ROAD`(T Map 4).
- **프론트 테스트(Vitest 48개)**: 우선순위 선택, 점수 등급 표기, 경고 구간 계산, 우회 요청, 격자 셀, 장소 변환, 날씨 표시, 컴포넌트 렌더·문구 규칙(미구현 기능·"위험" 단정 표현 금지).

### 변경됨

- **장소 검색은 카카오 JS SDK 직접 호출**(SafetyRoad 의 `/api/search/pois` 는 만들지 않았다).
- **홈 화면 문구**를 구현된 기능만으로 다시 썼다. SafetyRoad 의 엘리베이터·화장실·쉼터·야간 사고 이력·걷는 속도 안내와 "60%/40%" 가중치 설명은 구현·설정과 맞지 않아 뺐다. 통합 플랫폼 이름은 아직 정해지지 않아 `[이름 미정]` 으로 표시한다.
- 위험구역 진입 알림을 "시설이 적은 구역 안내"로 바꿨다(밀도 격자 기준). 지도 위 시설 이름은 외부 데이터이므로 HTML 문자열이 아닌 `textContent` 로만 넣는다.
- 로그인 후 백엔드가 `/` 로 되돌려 보내도 홈 화면으로 돌아가지 않도록, 지도에 들어온 상태를 탭의 `sessionStorage` 에 기억한다.

### 제거됨

- `riskLevel`·`overallRisk`·`/steep-slope/risk-stats` 의존(회색 마커, 위험도 필터, 통계 패널). 프론트에 `REACT_APP_`·`react-scripts` 참조도 없다.
- **이번에 옮기지 않은 FlatMap 기능**: 사고다발지·교통약자 보호구역·장애인복지시설·그늘막·노인복지관 레이어, "오르막길" 목록 탭(지역·위험도 필터), 모바일 전용 프로필 패널, 미사용이던 `pedestrianMode.js`. 해당 백엔드 조회 API 는 그대로 남아 있다.

### 한계 / 후속

- **지도 위 오버레이(경로선·시설·격자·급경사지·즐겨찾기)는 이 환경에서 눈으로 확인하지 못했다.** 카카오 JS 키에 `localhost:3001` 이 허용 도메인으로 등록되어 있지 않아 지도가 뜨지 않았다(오류 안내 화면은 확인). API 계약은 실제 백엔드(8081)로 확인: 경로 3종 점수·날씨·시설 개수, 밀도 격자 재계산(1,426셀). 사용자 포트(3000)에서 한 번 확인이 필요하다.
- 개발 DB(`flatmapv2`)에는 급경사지 행이 아직 없어(0건) 급경사지 레이어와 우회 경로는 실데이터로 확인하지 못했다. 우회 로직은 04 단계 백엔드 테스트가 다룬다.
- 점수 가이드의 등급 구간(71/41)은 화면 표시용 상수이며 서버 설정이 아니다. 카카오 로그인 흐름은 로컬 데모 환경에서 확인하지 않았다.
- 보안등(`GG_SECURITY_LIGHT`) 전체 수집은 01 단계의 미해결 항목이라, 보안등 레이어와 A축 점수가 수집 상태에 따라 달라진다.

## 04 단계 — 날씨 보정(C축)과 우회 경로 통합 (weather-detour) (2026-11-10) — 구현·자동 테스트 완료

기상 정보를 점수의 **C축(날씨 보정)** 으로 연결하고, 우회 경로가 새 점수와 PostGIS 급경사지 조회를 쓰도록 맞췄다.
**점수는 검증되지 않은 설계 파라미터이며 위험도·안전도가 아니다.** 지금은 A(야간 시설 노출)·C(날씨 보정)를 반영하고, 보행난이도(B)는 아직 반영하지 않는다.

### 추가됨

- **C축(날씨 보정)**: 날씨마다 보정 계수를 정하고 C 점수 = 100 ÷ 계수(맑음 100점). 눈 1.8 / 비·눈 1.6 / 비 1.4 / 영하 1.6 / 강풍 1.2 이며, 조건이 겹치면 눈 > 비/눈 > 비 > 영하 > 강풍 순으로 **하나만** 적용한다. 계수와 경계(기온 ≤ 0℃, 풍속 ≥ 10m/s, 경계 포함)·축 가중치(`score.route.axis-weight.c`)는 모두 `application.properties` 설정값이다. `breakdown` 에 별도 항목(`axis: "C"`)으로 나타난다. 총점은 반영된 축의 가중 평균.
- **기상 정보를 얻지 못하면 C축은 빠진다**(0점·100점으로 채우지 않음). 그때 `weather.fallback=true` 이고 `breakdown` 은 A만 담는다. 응답의 `scoreModel.note` 에 이 규칙을 적었다.
- **우회 경로 응답에 점수**: `POST /api/route/walk/detour` 와 `/api/route/walk` 응답에 `totalScore`, `breakdown[]` 가 들어간다. 우회 요청에는 비교 기준인 `originalTotalScore` 도 들어간다. 후보 경로(`GET /api/routes/safe`)에는 경로별 `weather` 가 들어간다.
- `DetourRouteService`: 컨트롤러에 있던 우회 판단을 서비스로 옮겼다. 후보 경로·단일 경로·우회가 같은 점수 함수(`SafeRouteService.evaluate`: 시설 집계 쿼리 1회 + 경로 중간 지점 날씨)를 쓴다.
- 설정값: `weather.correction.*`, `weather.connect-timeout-ms / read-timeout-ms / cache-ttl-minutes / failure-backoff-seconds / cache-max-entries`, `detour.max-score-drop`.

### 변경됨

- **우회 채택 규칙**: 급경사지(붕괴위험 경고) 구간이 **줄어야** 하고, 추가로 총점이 원경로보다 `detour.max-score-drop`(기본 10점)을 넘게 떨어지지 않아야 한다. 아니면 **원경로**를 돌려준다(`detourApplied=false`, `detourMessage`). 급경사지는 여전히 점수에 섞지 않는다.
- **외부 호출 실패**: 우회용 T Map 호출이 실패하면 원경로를 돌려준다(전엔 400). 원경로 조회가 실패하면 일반화된 502(`ROUTE_PROVIDER_UNAVAILABLE`, 전엔 본문 없는 400). 기상청 호출 실패는 fallback 으로 진행한다. 응답에는 외부 메시지가 실리지 않는다.
- **기상청 호출**: 전용 `RestTemplate` 에 연결 2s/읽기 3s 타임아웃(전엔 무제한). 로그에서 요청 URL(서비스키 포함)·응답 본문을 없앴다 — 이전엔 실패 시 예외 메시지에 서비스키가 든 URL 이 로그에 남을 수 있었다. 실패한 격자는 60초간 다시 부르지 않는다(후보 3개가 연달아 타임아웃을 기다리지 않도록). 캐시 크기에 상한(500격자)을 뒀다. 기준 시각을 서버 시간대와 무관하게 **KST** 로 계산한다.
- 날씨 대체값 `description` 이 "맑음 (기본값)" → "날씨 정보 없음", `icon` 은 `unknown`, `fallback: true` 로 표시한다(실제 관측처럼 보이지 않게).
- 경로 제공자 실패는 `RouteProviderException`(일반화된 메시지)로 구분한다. 이전엔 컨트롤러가 모든 `RuntimeException` 을 400 으로 바꿔 DB 오류까지 가렸다. 이제 그런 오류는 일반화된 500 이다.

### 제거됨 (호환성 영향)

- `WeatherInfo.riskMultiplier`(JSON 필드): 하드코딩된 계수였다. 계수는 이제 설정값이며 C축 `breakdown[].detail.multiplier` 로 나간다. 현재 프론트는 이 필드를 읽지 않는다.

### 한계 / 후속

- 날씨는 한 경로 안에서 한 지점(경로 중간)만 조회한다. 후보 경로끼리 같은 기상 격자에 있으면 C 점수가 같아 **추천 순위는 바뀌지 않고 총점의 절대값만 바뀐다**. 날씨가 경로 선택에 영향을 주려면 보행난이도(B축, 06)와 결합해야 한다(비·결빙이 경사에 미치는 영향).
- 보정 계수와 `detour.max-score-drop` 은 근거가 검증되지 않은 값이다. `WeatherService.applyWarning` 의 경고 문구 경계(0℃·10m/s)는 아직 설정값과 별개로 코드에 있다.
- 기상청·T Map 실제 호출은 이번 단계에서 모킹으로만 검증했다(키·쿼터 사용 없음).
- 프론트(CRA)는 `/api/route/walk` 의 실패 응답이 400 → 502 로 바뀐 것과 새 필드(`breakdown` 등)를 아직 쓰지 않는다. 05 단계에서 포팅한다.

## 03 단계 — 경로 점수 + 시설 밀도 격자 (route-score) (2026-11-10) — 구현·자동 테스트 완료

후보 경로 3종에 A축(야간 시설 노출) 점수를 매기고, 시설 밀도 격자를 재계산·조회할 수 있게 했다.
**점수는 검증되지 않은 설계 파라미터이며 위험도·안전도가 아니다.** 지금은 A축만 반영한다(보행난이도 B·날씨 C는 이후 단계, 아직 반영하지 않는다).

### 추가됨

- `GET /api/routes/safe?startLat&startLng&endLat&endLng` — T Map 후보 경로 3종(`searchOption` 0 일반 / 4 대로우선 / 10 최단)을 받아 경로별 `totalScore`, 축별 `breakdown`, 주변 `cctvCount`·`lightCount`·`policeCount` 를 돌려준다. 총점이 가장 높은 경로에 `recommended` 표시. 응답의 `scoreModel` 에 설계 파라미터 안내 문구가 들어 있다. 후보 하나가 실패해도 나머지로 응답하고, 모두 실패하면 일반화된 502.
- **A축 점수**: 경로 전체를 LINESTRING 하나로 만들어 **DB 쿼리 1회**(`ST_DWithin(geography)`, 미터)로 시설 수와 경로 길이를 구한다. 점수 = 구간 함수(가중 시설 수 ÷ 경로 km). 구간 함수는 `PiecewiseScale` 한 곳에만 있다.
- **시설 밀도 격자**: 300m 격자(경도 0.0034° × 위도 0.0027°, 성남 범위), 정수 인덱스 `(grid_i, grid_j)`. `POST /api/density-grid/recalculate`(관리용, `X-Admin-Api-Key`)는 **단일 트랜잭션**으로 다시 만들고, 시설 0개이면서 인접 8칸에도 시설이 없는 셀은 저장하지 않는다. `GET /api/density-grid`(공개, 선택적 사각형 필터)로 조회. 등급은 `LOW_DENSITY / MEDIUM / HIGH_DENSITY`.
- Flyway `V4`: `facility_density_grid` 에 `cctv_count / light_count / police_count` 추가. `facility_count` 는 가중치 없는 실제 개수다.
- 설정값: `application.properties` 의 `score.*`(가중치·반경·구간 함수·격자 범위·등급 경계), `tmap.connect-timeout-ms`, `tmap.read-timeout-ms`. 코드에 기본값을 두지 않는다. 같은 이름의 환경변수로 덮어쓸 수 있다.

### 변경됨

- `TMapRouteService`: FlatMap 의 단일 경로 조회와 SafetyRoad 의 후보 3종 호출을 **하나로 합쳤다**. 기존 `/api/route/walk`, `/walk/detour` 동작은 그대로. T Map 호출에 연결·읽기 타임아웃을 추가했고, 로그·예외에 응답 본문을 남기지 않는다.
- POLICE 가중: SafetyRoad 는 경찰을 ×5 로 의도했지만 격자 집계가 경찰을 한 번 더 세어 실제로는 ×6 이었다. 여기서는 의도한 5배를 설정값(`score.weight.police`)으로 명시하고 한 번만 센다.
- 격자의 "danger_zone" 표현·`HIGH/LOW` 위험 등급은 쓰지 않는다. 시설 밀도가 낮다는 것은 위험이 아니라 시설 데이터가 적다는 뜻이다.

### 한계 / 후속

- 점수 구간·가중치는 검증되지 않은 값이다. 보안등 수집(`GG_SECURITY_LIGHT` 29만 건)이 아직 끝나지 않아 실제 적재 기준 보안등이 적게 잡힌다 — 점수가 시설 수집 상태에 따라 달라진다.
- 개발 DB(flatmapv2)에는 V3·V4 가 아직 적용되지 않았다(앱 재시작 시 적용). 검증은 테스트 DB 에서 했고, 시설 밀도 격자는 개발 DB 에서 아직 한 번도 재계산하지 않았다.
- 급경사지 경로 반경 판정(정점 기준)은 03 에서 재검토했으나 이번 범위 밖이라 유지했다.
- 프론트는 아직 새 계약(`totalScore`, `breakdown`, `{routes, scoreModel}` 래퍼)을 쓰지 않는다. 05 단계에서 포팅한다.


## 02 단계 — 공간 로직 PostGIS 이전 (spatial-core) (2026-11-10) — 구현·자동 테스트 완료

`findAll()` 후 애플리케이션에서 haversine 을 돌던 급경사지 로직을 DB 의 `ST_DWithin(geography)` 로 옮기고, 급경사지 API 를 "붕괴위험 경고 레이어" 계약으로 정리했다.

### 변경됨

- 경로 반경(200m) 이내 급경사지 판별(`findRiskSections`)을 **DB 쿼리 1회**로 교체했다. 결과(감지 대상·`nearestRouteIdx`·경로 순서 정렬)는 교체 전과 같고, 교체 전에 동작 고정 테스트를 먼저 썼다. 거리 값은 구면(haversine)→WGS84 타원체로 약 0.2% 이내 차이.
- 우회 경유지 후보가 다른 급경사지 반경에 걸리는지 검증하는 부분(`DetourService`)을 **오프셋 단계마다 DB 쿼리 1회**로 교체했다. 우회 결과는 교체 전과 같다(테스트로 고정).
- **급경사지 API 계약**: `riskLevel` 없이 경사도(°)·위치·출처만 반환한다. 경로 구간(`riskSections[]`)은 `lat, lng, name, grade, source, distanceFromRoute, nearestRouteIdx`. 측정되지 않은 `grade` 는 `0` 이 아니라 `null`.
- `steep_slope_areas` 위치의 **쓰기 원본을 `geom` 으로 전환**(Flyway `V3`). `latitude/longitude` 는 geom 에서 파생되는 읽기 전용 컬럼이다. 수집 서비스 4곳과 `ElevationService`, 동기화/경사도 갱신 엔드포인트가 모두 geom 으로 쓴다. 기존 행의 좌표는 보존된다(테스트로 확인).
- `SteepSlopeAreaRepository` 를 JPA → `JdbcTemplate` 로 바꿨다(공간 데이터 규칙). 출처별 교체(삭제+저장)는 이제 한 트랜잭션이다.

### 제거됨 (호환성 영향)

- **날씨 기반 급경사지 등급 보정**(`adjustRiskByWeather`)과 `RouteResponse.overallRisk`, `RiskSection.riskLevel`, `DetourRequest.avoidPoints[].riskLevel`. 급경사지에는 보행 위험등급이 없으므로 올릴 등급도, 그로부터 파생할 종합 위험도도 없다. 날씨는 응답의 `weather` 로 계속 내려간다. 점수에 반영하는 것은 04 단계(C축)에서 한다.
- `/risk-stats` 는 00 단계에서 이미 사라졌고, 되살리지 않는 것으로 확정했다. (옛 클라이언트가 `avoidPoints[].riskLevel` 을 보내도 무시된다.)
- **알려진 영향**: 현재 `frontend/`(CRA)는 `riskLevel`·`overallRisk` 를 읽는다. 05 단계에서 UI 를 새로 포팅하므로 그때 새 계약에 맞춘다. 그전까지 구 프론트의 위험 등급 배지는 의미 있는 값을 보여주지 못한다(00 단계부터 급경사지 등급이 `null` 이었다).

### 한계 / 후속

- 반경 판정은 기존과 같은 의미(경로 **정점**까지의 최소 거리)다. 선분 기준으로 바꾸면 정점 간격이 먼 경로에서 감지 대상이 늘 수 있어 03 단계(경로 점수)에서 재검토한다.
- `DetourService` 에 `haversineMeters` 가 4곳 남아 있다. 모두 요청 안의 점끼리의 계산이며 DB 행을 `findAll()` 로 도는 곳은 없다(`docs/dev/DECISIONS.md` 참고).
- 01 단계의 미해결 2개 소스(`ODCLOUD_SEONGNAM_CCTV` 401, `GG_SECURITY_LIGHT` 장시간)는 그대로다. 02 는 급경사지만 다룬다.

## 01 단계 — 성남시 안전시설 재수집 (ingestion) (2026-11-10) — 구현·자동 테스트 완료, 실제 적재 검증 4/6 소스

깨진 덤프를 쓰지 않고 공공 소스에서 CCTV·보안등·경찰시설을 다시 수집해 `safety_facility` 에 적재한다.

### 실제 적재 검증 결과 (flatmapv2 DB, 2026-11-10)

| 소스 | 결과 | 적재 |
|------|------|------|
| `GG_CCTV` | 성공, 2회차 inserted 0 | 2,896 (83,072행 중 성남) |
| `GG_POLICE` | 성공, 2회차 inserted 0, 소방서 36건 제외 | 33 (469행 중) |
| `ODCLOUD_ILLUMINATION` | 성공, 2회차 inserted 0 | 3,133 (177건은 중복 좌표) |
| `SEONGNAM_CSV` | 성공, 2회차 inserted 0 | 9,606 (283,264행 중) |
| `ODCLOUD_SEONGNAM_CCTV` | **실패 — API 가 HTTP 401** | 0 (키에 데이터셋 15147955 활용신청 권한이 없는 것으로 보임, 코드 문제 아님) |
| `GG_SECURITY_LIGHT` | **미확인** — 297,198건·298페이지를 한 요청에서 읽는데 29분 넘게 끝나지 않아 중단(커밋 전이라 DB 변경 없음) | 0 |

검증 SQL: 4개 소스에서 `bad_pct` 0%, 중복 자연키 0건, 성남 밖 0건, 주소에 "성남" 없음 0건(조도 제외), 옛 `GYEONGGI_DATADREAM_*` 코드 0건.
**이 문서의 위 두 소스(`ODCLOUD_SEONGNAM_CCTV`, `GG_SECURITY_LIGHT`)는 아직 검증되지 않았다.**

### 후속 작업

- `ODCLOUD_SEONGNAM_CCTV`: 공공데이터포털에서 15147955 활용신청 후 재동기화(사용자 작업).
- `GG_SECURITY_LIGHT`: 동기 호출 한 번에 30분 이상 걸린다. 수집을 백그라운드로 돌리거나 페이지 단위로 커밋하는 방식 검토 필요. 전체 약 297k건 중 성남 비중은 적을 것으로 보이며, `SEONGNAM_CSV`(9.6k)·`ODCLOUD_ILLUMINATION`(3.1k)이 보안등을 이미 채우고 있다.
- 로그에 `IOException` 은 이제 HTTP 상태("HTTP 401" 등)까지 남긴다(URL·키는 여전히 남기지 않는다).

### 추가됨

- `ingestion` 패키지: 소스 6개 리더 + `FacilityIngestionService`. `data_source` 코드값은 고정 영문이다.

| 코드값 | 대상 | 소스 |
|--------|------|------|
| `GG_CCTV` | CCTV | 경기데이터드림 `CCTV` |
| `GG_SECURITY_LIGHT` | 보안등 | 경기데이터드림 `SECRTLGT` |
| `GG_POLICE` | 경찰시설 | 경기데이터드림 `FiresttnPolcsttnM` (XML) |
| `ODCLOUD_ILLUMINATION` | 보안등 | 공공데이터포털 odcloud 15110584 (조도측정) |
| `ODCLOUD_SEONGNAM_CCTV` | CCTV | 공공데이터포털 odcloud 15147955 (성남시 생활안전 CCTV) |
| `SEONGNAM_CSV` | 보안등 | 성남시 보안등 CSV (**CP949**, 경로는 환경변수 `SEONGNAM_SECURITY_LIGHT_CSV_PATH`) |

- `POST /api/safety/facilities/sync[?source=<코드값>]` (관리용, `X-Admin-Api-Key`). 응답은 소스별 `source/status/read/excluded/inserted` 만 담는다. 실패 원인은 서버 로그에만 남고 키·URL 은 로그에도 남기지 않는다.
- Flyway `V2__rename_legacy_facility_sources.sql`: 옛 `data_source` 값(`GYEONGGI_DATADREAM_SECRTLGT/CCTV`)을 새 코드값으로 이름만 바꾼다(삭제 없음).
- `.env.example`: `SEONGNAM_SECURITY_LIGHT_CSV_PATH` 키 이름 추가.

### 동작

- **성남 필터(전 소스 공통)**: 좌표 범위(위도 37.33~37.53, 경도 127.04~127.20) **그리고** 주소에 "성남". 조도측정(`ODCLOUD_ILLUMINATION`)은 원본에 주소 컬럼이 없어 좌표 범위만 적용한다(주소가 실려 오면 성남이어야 한다).
- **경찰시설**: 경찰서/지구대/파출소/치안센터만 남긴다. 소방(`소방`, `119` 포함)은 제외하고 제외 건수를 로그에 남긴다. 그 밖의 시설도 제외한다.
- **멱등**: `INSERT ... ON CONFLICT (facility_type, location) DO NOTHING`. 같은 좌표를 다른 소스가 가지면 먼저 들어간 행이 유지된다. 옛 `replaceAll`(삭제 후 삽입)은 이 경로에서 쓰지 않는다.
- **품질 게이트**: 소스별로 name/address 가 비었거나 `?` 만 있거나 깨진 문자(U+FFFD)가 든 행이 5% 를 넘으면 그 소스는 실패 처리하고 롤백한다(후보 단계 + 적재 후 DB 단계 모두 검사). 소스 하나가 실패해도 나머지는 진행한다.
- 외부 호출은 연결 10초·읽기 30초 타임아웃, 페이지 사이 300ms 지연, 응답은 UTF-8 로 고정해 해석한다. 서비스 키는 URL 인코딩한다.
- CSV 는 헤더 이름으로 컬럼을 찾는다. UTF-8 파일을 CP949 로 잘못 읽으면 헤더가 맞지 않아 실패한다(깨진 한글을 적재하지 않는다).

### 변경됨 / 제거됨

- `GyeonggiDataService.fetchAndSaveSecurityLights`, `PublicDataService.fetchAndSaveCctvLocations` 삭제(새 수집으로 통합). 기존 보안등 수집은 첫 100건만 읽던 버그가 있었다.
- `POST /api/safety/fetch/security-lights`, `POST /api/safety/fetch/cctv` 삭제 → `POST /api/safety/facilities/sync` 로 대체.
- `POST /api/safety/fetch/all`, `POST /api/data/fetch/all` 은 시설 수집 결과를 `facilities`(소스별 배열)로 돌려주고, 실패한 시설 소스는 `failedSources` 에 코드값으로 나타난다. 기존 `securityLights`, `cctv` 필드는 없어졌다.

### 테스트

기존 81개 중 `AdminEndpointSecurityTest` 는 삭제한 엔드포인트 2개를 빼고 새 엔드포인트 1개를 더해(엔드포인트당 3케이스) 59 → 56개가 되었다. `ErrorResponseTest` 는 같은 목적(실패 원인 비노출)으로 대상 소스만 바꿨다.

| 클래스 | 검증 내용 |
|--------|----------|
| `FacilityIngestionServiceTest` (9) | 2회 실행해도 행 수 불변, 소스 간 같은 좌표는 먼저 들어간 행 유지, CP949 한글 저장 왕복, 깨진 후보 거부, 적재 후 검사 실패 시 롤백, 한 소스 실패해도 나머지 진행, 빈 결과는 실패, 주소 없는 소스는 주소로 감점하지 않음, SQL/Java 불량 기준 일치 |
| `FacilitySyncEndpointTest` (4) | 응답 필드 5개뿐·내부 메시지 비노출, 2회차 inserted 0, 알 수 없는 소스 400(입력 미반사), 키 없으면 401·외부 호출 없음 |
| `GyeonggiJsonReaderTest` (7) · `PoliceXmlReaderTest` (6) · `OdcloudReaderTest` (6) · `SeongnamCsvReaderTest` (5) · `TextQualityTest` (16) | 성남 밖 제외·사유별 건수, 소방서 제외, 페이징, 오류 코드, 키 누락, XXE 차단, CP949, UTF-8 오입력 거부 |
| `AdminEndpointSecurityTest` | `/api/safety/facilities/sync` 를 관리용 목록에 추가(키 없음 401 / 틀린 키 401 / 일반 사용자 403) |

결과: **131개 통과**(기존 `81` → 새 테스트 `+53`, 삭제된 파라미터 케이스 `-3`).

### 알려진 한계

- `GG_CCTV`·`GG_POLICE`·`ODCLOUD_ILLUMINATION` 의 실제 응답 필드명은 레거시 기준으로 맞았다(0% 불량). `GG_SECURITY_LIGHT` 의 필드명은 아직 확인되지 않았다. 경찰시설 이름(`INST_NM`)은 `고등`, `구미`처럼 시설 종류 없이 지명만 있다(UI 표기 시 참고).
- 레거시 CSV 적재 결과: 283,264행 중 성남 9,607행이 통과했고 9,606건이 적재됐다(1건은 중복 좌표). **이 중 이름의 84%(8,087건)와 주소의 3.8%(368건)에 문자 중간 `?` 가 들어 있다**(예: `제일로98번길4 ?TS?`). 변환 실패 문자는 0건이므로 디코딩 문제가 아니라 원본 파일에 이미 들어 있는 값이다. 5% 규칙은 "`?` 만 있는 값"을 기준으로 하므로 이를 잡지 않는다. 이름은 보안등 식별 라벨이고 거리 계산에는 쓰이지 않는다.

## 1단계 — 통합 스키마 구조 + 인프라 + 보안 정리 (2026-11-07)

기능 이식과 데이터 적재는 하지 않았다. 구조(스키마), 실행 환경, 보안만 정리했다.
근거 문서: `docs/schema-merge.md`. 아래 "확정 결정"이 그 문서의 "결정 필요" 항목보다 우선한다.

### 확정 결정 (V1 에 반영)

| 결정 | 반영 |
|------|------|
| 깨진 시설 데이터·danger_zone 데이터는 이식하지 않는다 | 구조만 생성. V1 에 INSERT 없음 (테스트로 0행 확인) |
| `safety_facility` 자연키 UNIQUE (facility_type + location) | `uq_safety_facility_type_location`. 적재는 `ON CONFLICT DO NOTHING` |
| 시설 타입은 POLICE 만, 소방서 제외 | `CHECK (facility_type IN ('CCTV','SECURITY_LIGHT','POLICE'))` |
| `steep_slope_areas` 에 `risk_level` 없음 | 컬럼 없음. `grade`(각도 °, `CHECK 0 ≤ grade < 90`) 와 `geom` 만 |
| `danger_zone` → `facility_density_grid`, 시설 밀도 등급 | `density_level IN ('LOW_DENSITY','MEDIUM','HIGH_DENSITY')`. "위험" 표현 없음 |
| 공간 쿼리는 geography 표현식 GiST, 거리는 미터 | 모든 공간 테이블에 `GIST ((col::geography))`. 저장소 메서드는 `ST_DWithin(geography, 미터)` |
| FlatMap `security_lights` 100건(성남 밖) 미이식 | 테이블 자체를 만들지 않음 |
| `accident_records`, `route_segments`, `road_segments`, `routes` 미생성 | 만들지 않음 (테스트로 부재 확인) |

### 추가됨

- `docker-compose.yml`: PostgreSQL + PostGIS(`postgis/postgis:16-3.4`). 포트·볼륨·DB명·계정은 환경변수. 같은 서버에 테스트용 `<DB_NAME>_test` DB 를 함께 생성(`docker/initdb/01-create-test-db.sh`).
- Flyway 도입(`flyway-core`, Spring Boot BOM 관리 버전). `backend/src/main/resources/db/migration/V1__baseline.sql`.
- `.env.example`(키 이름만, 값 없음). `application.properties` 는 환경변수만 읽는다. `.env` 도 `spring.config.import` 로 읽는다.
- `AdminApiKeyFilter` + `SecurityConfig` 규칙: 관리용 엔드포인트 보호(아래).
- `GlobalExceptionHandler`, `ApiErrors`: 일반화된 오류 응답.
- `SafetyFacilityRepository`(JdbcTemplate + PostGIS), `model/SafetyFacility`.
- 테스트 6개 클래스 / 81 케이스(아래).
- `.gitattributes`: `*.sh` 를 LF 로 고정.

### 변경됨

- `spring.jpa.hibernate.ddl-auto`: `update` → `validate`. 스키마는 Flyway 만 바꾼다. `show-sql` 은 `false`.
- `.gitignore`: `*.sql` 무시 규칙을 제거(Flyway 마이그레이션이 `.sql`). `application.properties` 는 비밀이 없으므로 추적하고, `application-local.*` 만 무시한다.
- 오류 응답 정책: `server.error.include-message/stacktrace/binding-errors=never`.
- `/api/safety/cctv`, `/api/safety/security-lights`, `/api/safety/stats` 는 `safety_facility` 에서 읽는다(응답 필드명은 유지: `id,name,latitude,longitude,address,createdAt`, 추가로 `facilityType,regionCode,dataSource`).
- CCTV·보안등 수집은 `safety_facility` 에 `ON CONFLICT DO NOTHING` 으로 적재한다. `data_source` 는 ASCII 고정값(`GYEONGGI_DATADREAM_CCTV`, `GYEONGGI_DATADREAM_SECRTLGT`).
- 보안등 수집에 성남시 주소 필터 추가. 기존에는 경기도 전체를 받아 가평군 데이터가 들어갔다.
- FlatMap 전용 5개 공간 테이블과 `steep_slope_areas` 는 `latitude/longitude` 를 유지하고 `geom` 을 DB 생성 컬럼(`ST_MakePoint(longitude, latitude)`)으로 둔다. JPA 쓰기 경로를 바꾸지 않으려는 전환 단계 조치이며, 소비 코드를 JdbcTemplate 로 옮긴 뒤 lat/lng 제거를 후속 마이그레이션으로 한다.
- `users` 에 `UNIQUE (provider, provider_id)`, `favorites.user_id` 에 FK(`ON DELETE CASCADE`)를 추가했다(기존 덤프에는 없던 제약).
- 급경사지 수집이 `grade` 를 못 얻을 때 `0.0` 대신 `NULL`(미측정)을 저장한다.
- 급경사지 근처 경로 구간(`RiskSection`)의 `riskLevel` 은 이제 `null` 이다(붕괴위험 지정지에는 보행 위험등급이 없다).
- 인증 실패 응답은 401(기존 기본값 403). 인증은 됐으나 권한이 없으면 403.

### 보안

- **관리용 엔드포인트 인증**: 고정 API 키 헤더 방식을 택했다(`X-Admin-Api-Key`, 값은 `ADMIN_API_KEY`).
  - 이유: 현재 사용자 모델에 역할이 없고 JWT 필터는 권한을 비워 둔다. ADMIN 롤을 쓰려면 사용자·토큰 스키마 변경이 필요해 더 침습적이다.
  - 대상: `/api/safety/**`, `/api/steep-slope/**` 의 GET 이외 전체, `/api/data/**` 전체. GET 조회는 공개 유지.
  - 키 미설정(빈 값)이면 관리용 엔드포인트는 모두 거부된다(fail closed). 비교는 SHA-256 해시끼리 상수 시간으로 한다.
- **오류 응답 일반화**: 컨트롤러가 `e.getMessage()` 를 그대로 응답하던 곳(약 15곳)을 `ApiErrors.internal()` 로 교체. 수집 집계 응답은 실패 원인 대신 실패한 소스 이름만(`failedSources`) 알린다. 원인은 서버 로그에만 남는다.
- **비밀값**: `application.properties` 의 값을 `application-local.properties`(gitignore)로 옮겼다. 저장소에는 환경변수 참조만 남는다. 커밋 대상에 `.env`, `application-local.*`, `_legacy/`, `*.dump`, `*.tif` 가 없음을 `git add -n` 으로 확인했다.

### 제거됨

- 더미/시드 엔드포인트: `POST /api/safety/seed/all`, `POST /api/data/seed/all`, `POST /api/steep-slope/seed`, `POST /api/steep-slope/seed/seongnam`, `DELETE /api/steep-slope/dummy`. `DataSeedService` 삭제.
- 선택된 결정에 따른 코드 정리: `RoadSegment`/`Route` 엔티티·리포지토리·컨트롤러(`/api/segments`, `/api/routes`), `CctvLocation`/`SecurityLight` 엔티티·리포지토리.
- 붕괴위험 지정지의 보행 위험등급 로직: `SteepSlopeArea.riskLevel`, `calculateRiskLevel`, `riskLevel` 필터(`GET /api/steep-slope?riskLevel=`), `GET /api/steep-slope/risk-stats`, `GradeUpdateRequest` 의 `riskLevel/applyRisk`.

### 테스트

실제 PostGIS DB 에 붙는 통합 테스트다. 매 실행마다 `<이름>_test` DB 를 Flyway clean 후 다시 마이그레이션한다(이름이 `_test` 로 끝나지 않으면 거부).

| 클래스 | 검증 내용 |
|--------|----------|
| `ApplicationContextTest` | 컨텍스트 로드 = Flyway 적용 + Hibernate `validate` 통과 |
| `FlywayMigrationTest` (9) | V1 성공, PostGIS, 테이블 존재/부재, 데이터 0행, `risk_level` 부재, 생성 geom, 각도 CHECK, 밀도 등급 CHECK, GiST·geography 인덱스 |
| `SafetyFacilityRepositoryTest` (6) | 자연키 중복 삽입 차단, `ON CONFLICT DO NOTHING`, 타입별 허용, 소방서 타입 거부, 반경이 미터 단위, 출처별 교체 |
| `AdminEndpointSecurityTest` (59) | 관리용 17개 엔드포인트 × (키 없음 401 / 틀린 키 401 / 일반 사용자 403), 올바른 키 통과, 더미 5개 404, 공개 조회 유지, 즐겨찾기 로그인 필요 |
| `AdminApiKeyFilterTest` (3) | 키 일치/불일치, 키 미설정 시 fail closed |
| `ErrorResponseTest` (3) | 예외 메시지·내부 정보 비노출, 집계는 실패 소스 이름만, 잘못된 JSON 은 일반 400 |

### 빌드·테스트 결과

- `./mvnw test`: **81 tests, 0 failures, 0 errors, 0 skipped**.
- 환경: JDK 25(Corretto), PostgreSQL 18.3 + PostGIS 3.6.2(로컬 서버의 별도 DB `flatmap_test`).
- 실제 `application.properties` 로 앱을 환경변수만으로 기동해 스모크 확인: 공개 GET 200, 관리용 POST 키 없음/틀린 키 401, 제거된 더미 404, 올바른 키 200, 알 수 없는 경로는 일반화된 404 본문.

### 알려진 한계 / 후속 작업

- **Flyway 버전 경고**: Spring Boot 3.2.0 이 관리하는 Flyway 9.22.x 는 공식 지원 PostgreSQL 이 15 까지다. PostgreSQL 18.3 에서 `support has not been tested` 경고가 나지만 마이그레이션은 성공한다. docker-compose 의 PG16 도 같은 경고가 날 수 있다(확인 불가: docker 미설치라 compose 는 실행하지 못했다). Flyway 10/11 로 올리려면 `flyway-database-postgresql` 모듈과 Boot 호환 확인이 필요하다.
- **docker-compose.yml, docker/initdb 는 이 PC 에 Docker 가 없어 실행·검증하지 못했다.** 이미지 태그와 설정은 문서 기준으로 작성했다.
- **프론트엔드(수정하지 않음)**: `GET /api/steep-slope/risk-stats` 가 없어져 SafetyPanel 통계는 비어 있다(호출은 조용히 무시됨). `riskLevel` 응답 필드와 필터가 없어져 급경사지 마커가 회색(미측정)으로 보이고 위험도 필터는 효과가 없다. 붕괴위험 경고 레이어 UI 로 재설계가 필요하다.
- **보행 경로 위험구간**: `/api/route/walk` 의 `riskSections[].riskLevel` 이 급경사지에 대해 `null` 이다. 우회 경로(`DetourService`)의 등급 기반 우선순위는 사실상 동일 순위가 된다. 보행난이도(DEM 기반 경로 경사)는 아직 이식되지 않았다.
- 서비스 코드에 `haversineMeters` 루프(`SteepSlopeService`, `DetourService`)가 남아 있다. CLAUDE.md 규칙 4 에 따라 후속 단계에서 `ST_DWithin(geography)` 로 옮긴다.
- `facility_density_grid`, `SafetyFacilityRepository.findWithinMeters` 의 소비 코드(SafetyRoad 경로 점수·격자 재계산)는 아직 이식하지 않았다. 구조만 있다.
- 로컬 개발 DB 는 새 DB 로 시작해야 한다. 기존 DB(Hibernate 가 만든 스키마)에 붙이면 Flyway 가 비어 있지 않은 스키마로 거부한다.
- `backend/dem/` 의 스크립트 3개에 DB 접속 정보가 하드코딩되어 있다. `.gitignore` 로 제외했으며, 환경변수로 바꾼 뒤 추적하는 것을 권장한다.
