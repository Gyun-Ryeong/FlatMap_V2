# CHANGELOG

> 단계 번호: 아래 "1단계 — 통합 스키마…" 는 로드맵의 00(foundation) 이다. 로드맵 01(ingestion)은 이 문서 맨 위 항목이다.

## 05 단계 — 프론트엔드 (Vite 전환 + SafetyRoad UI 포팅 + FlatMap 기능 이식) (2026-11-10) — 구현·자동 테스트 완료, 지도 화면은 사용자 확인 필요

프론트를 CRA에서 Vite로 옮기고, SafetyRoad UI(홈·우선순위·SOS·실시간 위치)를 뼈대로 삼아 FlatMap 기능(로그인·즐겨찾기·날씨·우회·급경사지)을 새 API 계약에 맞게 이식했다.
**경로 점수는 검증되지 않은 설계 파라미터이며 위험도·안전도가 아니다.** 화면 문구도 그렇게 쓴다. 보행난이도(B)와 보행약자/일반 모드, RAG 는 개발 중이며 아직 연동되지 않았다.

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
