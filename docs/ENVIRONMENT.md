# 환경변수

키 이름·용도·필수 여부만 적습니다(**값은 여기에도, 저장소 어디에도 없습니다**). 실제 값은 서버의 `.env`(커밋 금지)에 둡니다.
`.env.example`(저장소 루트)과 `frontend/.env.example`이 키 이름의 원본이고, 이 문서와 두 파일이 일치하는지는 자동 테스트(`EnvDocsConsistencyTest`)가 확인합니다.

| 표기 | 뜻 |
|---|---|
| **필수** | 없으면 백엔드가 기동하지 않거나(필수 4개) 핵심 기능이 동작하지 않음 |
| **운영 필수** | 운영 프로필(`prod`)에서는 없으면 기동이 실패함. 개발에서는 기본값이 있음 |
| 기능 필수 | 해당 기능을 쓸 때만 필요(없어도 앱은 뜸) |
| 선택 | 기본값이 있어 보통 필요 없음 |
| 테스트 전용 | 통합 테스트에서만 사용 |

## 데이터베이스

| 키 | 용도 | 필수 | 비고 |
|---|---|---|---|
| `DB_NAME` | 개발·운영 DB 이름 | **필수** | compose 가 DB 를 만들 때와 호스트의 DB 접속에 쓴다 |
| `DB_USERNAME` | DB 계정 | **필수** | |
| `DB_PASSWORD` | DB 비밀번호 | **필수** | 운영에서는 길고 무작위인 값을 쓴다 |
| `DB_PORT` | 개발 compose 의 호스트 포트 | 선택 | 비우면 5433. 운영 compose 는 DB 포트를 호스트에 열지 않는다 |
| `DB_URL` | 백엔드가 접속하는 JDBC URL | **필수** | 호스트에서 백엔드를 직접 실행할 때 쓴다. 운영 compose 는 컨테이너 내부 주소(`db:5432`)로 덮어쓴다 |
| `TEST_DB_URL` | 통합 테스트 전용 DB 의 JDBC URL | 테스트 전용 | DB 이름이 `_test` 로 끝나야 한다(테스트가 매번 비운다) |
| `TEST_DB_USERNAME` | 테스트 DB 계정 | 테스트 전용 | |
| `TEST_DB_PASSWORD` | 테스트 DB 비밀번호 | 테스트 전용 | |

## 인증·보안

| 키 | 용도 | 필수 | 비고 |
|---|---|---|---|
| `JWT_SECRET` | 로그인 쿠키(JWT) 서명 비밀 | **필수** | 32바이트 이상. 운영 프로필은 짧으면 기동 실패. 바꾸면 기존 로그인이 모두 무효가 된다 |
| `ADMIN_API_KEY` | 관리용 API(`X-Admin-Api-Key` 헤더) 키 | **운영 필수** | 16자 이상. 비어 있으면 관리용 API 가 전부 거부된다(fail closed) |
| `FRONTEND_URL` | 사이트의 공개 주소(예: `https://사이트주소`) | **운영 필수** | CORS 허용 출처이자 카카오 로그인 후 되돌아오는 주소. localhost 는 운영에서 거부됨 |
| `COOKIE_SECURE` | 로그인 쿠키의 `Secure` 속성 | 선택 | 운영 기본 `true`(HTTPS 필요). HTTP 로만 접근하는 내부 시험 환경에서만 `false` |

## 카카오

| 키 | 용도 | 필수 | 비고 |
|---|---|---|---|
| `KAKAO_REST_API_KEY` | 카카오 REST 키: 로그인(OAuth), 재난안전 데이터 수집 때의 주소→좌표 변환 | 기능 필수 | 없으면 카카오 로그인이 동작하지 않는다 |
| `KAKAO_OAUTH_CLIENT_SECRET` | 카카오 로그인 클라이언트 시크릿 | 기능 필수 | 카카오 콘솔에서 클라이언트 시크릿을 켠 경우 |
| `KAKAO_OAUTH_REDIRECT_URI` | 카카오가 로그인 후 호출하는 주소 | **운영 필수** | `https://사이트주소/api/auth/kakao/callback` 형태. 카카오 콘솔의 Redirect URI 에 같은 값을 등록해야 한다 |
| `VITE_KAKAO_API_KEY` | 지도용 카카오 JavaScript 키 | **필수**(지도) | 브라우저 번들에 들어가는 **공개 값**. 카카오 콘솔의 허용 도메인으로 제한한다. 운영 compose 는 이미지 빌드 때 쓰고, 로컬 개발은 `frontend/.env` |

## 외부 API 키

| 키 | 용도 | 필수 | 비고 |
|---|---|---|---|
| `TMAP_APP_KEY` | T Map 보행자 경로 | 기능 필수 | 없으면 경로 검색이 실패한다 |
| `WEATHER_API_KEY` | 기상청 초단기 예보(날씨·C축) | 선택 | 없거나 실패하면 날씨 없이 동작하고 점수에서 날씨 항목이 빠진다 |
| `PUBLIC_DATA_API_KEY` | 공공데이터포털(성남시 CCTV·보호구역·복지시설 등 수집) | 기능 필수 | 데이터 수집(관리용)에만 쓴다 |
| `GYEONGGI_API_KEY` | 경기데이터드림(시설·사고다발지 수집) | 기능 필수 | 데이터 수집(관리용)에만 쓴다 |
| `GYEONGGI_UPHILL_LANE_API_KEY` | 경기데이터드림 오르막차로(급경사지 수집) | 기능 필수 | 데이터 수집(관리용)에만 쓴다 |
| `DISASTER_STEEP_SLOPE_API_KEY` | 재난안전데이터공유플랫폼(safetydata.go.kr) API `DSSP-IF-10158` 수집 | 기능 필수 | 데이터 수집(관리용)에만 쓴다 |
| `DISASTER_TRAFFIC_DOWNHILL_API_KEY` | 재난안전데이터공유플랫폼 API `DSSP-IF-10006` 수집 | 기능 필수 | 데이터 수집(관리용)에만 쓴다 |
| `DISASTER_STEEP_SLOPE_FINAL_API_KEY` | 재난안전데이터공유플랫폼 API `DSSP-IF-10159` 수집 | 기능 필수 | 데이터 수집(관리용)에만 쓴다 |
| `GYEONGGI_SECURITY_LIGHT_API_KEY` | (예약) | 선택 | **현재 코드에서 사용하지 않음** — 값을 읽기만 하고 호출하는 곳이 없다 |
| `GYEONGGI_ACCIDENT_ICY_API_KEY` | (예약) | 선택 | **현재 코드에서 사용하지 않음** (사고다발지는 `GYEONGGI_API_KEY` 를 쓴다) |
| `GYEONGGI_ACCIDENT_NORMAL_API_KEY` | (예약) | 선택 | **현재 코드에서 사용하지 않음** |
| `GYEONGGI_SENIOR_WELFARE_API_KEY` | (예약) | 선택 | **현재 코드에서 사용하지 않음** |
| `GYEONGGI_SENIOR_CENTER_API_KEY` | (예약) | 선택 | **현재 코드에서 사용하지 않음** |

## 데이터 파일 경로

| 키 | 용도 | 필수 | 비고 |
|---|---|---|---|
| `WALK_DEM_PATH` | 보행난이도 경사 격자를 만드는 DEM(GeoTIFF) 경로 | 기능 필수 | 호스트 실행 기본값 `backend/dem/Copernicus_DSM_COG_10_N37_00_E127_00_DEM.tif`. 값을 줄 거면 줄 전체가 채워져야 한다(빈 값이면 기본값이 적용되지 않는다). 운영 compose 는 컨테이너 안의 `/data/dem/…` 로 고정한다 |
| `SEONGNAM_SECURITY_LIGHT_CSV_PATH` | 성남시 보안등 CSV(CP949) 경로 | 기능 필수 | 슬래시(`/`)로 적는다. 운영 compose 는 `/data/seongnam_security_light.csv` 로 고정한다 |
| `DATA_DIR` | 운영 compose 가 `/data` 로 마운트하는 호스트 디렉터리 | 선택 | 기본 `./data`. DEM·CSV 를 둔다(이미지에 넣지 않는다) |

## RAG 연동 지점 (개발 중)

| 키 | 용도 | 필수 | 비고 |
|---|---|---|---|
| `RAG_ENABLED` | RAG 연동 지점(`/api/rag/*`)을 켠다 | 선택 | 기본 `false`(빈 값도 꺼짐). **RAG 는 개발 중이며 이 저장소에 RAG 서버·구현체가 없다.** 켜도 "미연결" 상태이고 화면에 질문 진입점은 나오지 않는다 |

## 프론트엔드 (`frontend/.env`, Vite — `VITE_` 접두사만 브라우저에 노출됨)

| 키 | 용도 | 필수 | 비고 |
|---|---|---|---|
| `VITE_API_BASE_URL` | API 기본 경로 | 선택 | 비우면 같은 출처의 `/api` |
| `VITE_BACKEND_URL` | 카카오 로그인 시작 주소의 백엔드 출처 | 선택 | 비우면 같은 출처(운영: nginx 프록시, 개발: Vite 프록시). 백엔드가 다른 출처일 때만 |
| `VITE_DEV_PROXY_TARGET` | (개발 전용) Vite 개발 서버의 `/api` 프록시 대상 | 선택 | 비우면 `http://localhost:8080` |

## 배포(compose) 보조 설정

| 키 | 용도 | 필수 | 비고 |
|---|---|---|---|
| `HTTP_PORT` | 공개 HTTP 포트(프론트 컨테이너) | 선택 | 기본 80 |
| `BACKEND_HOST_PORT` | 서버 안 관리용 호출용 백엔드 포트 | 선택 | 기본 8080, 호스트의 `127.0.0.1` 에만 열린다 |
| `COMPOSE_PROJECT_NAME` | compose 프로젝트 이름 | 선택 | 기본 `flatmap` |
| `IMAGE_PREFIX` | 이미지 이름 접두사 | 선택 | 기본 `flatmap` (`flatmap-backend`, `flatmap-frontend`) |
| `IMAGE_TAG` | 이미지 태그 | 선택 | 기본 `local` |

## 코드 안에 있는 설정(환경변수 이름으로도 덮어쓸 수 있음)

점수·경사·날씨 보정의 가중치·반경·구간·임계값(`score.*`, `walk.*`, `weather.*`)은 `backend/src/main/resources/application.properties`에 있고, 같은 이름의 환경변수(예: `SCORE_WEIGHT_POLICE`, `WALK_THRESHOLD_WEAK_GOOD_MAX_DEG`)로 덮어쓸 수 있습니다. 이 값들은 **검증되지 않은 설계 파라미터**이며 키 목록에 넣지 않았습니다.
