# 배포 가이드

특정 클라우드에 종속되지 않는 배포 방법입니다. Docker 이미지 2개(백엔드, 프론트)와 `docker-compose.prod.yml`(DB 포함 전체 스택)을 씁니다.
**서비스 이름은 FlatMap 이고, 배포할 클라우드는 아직 정해지지 않았습니다.** 클라우드를 정하면 이 가이드의 "클라우드에 올릴 때" 절을 그 환경에 맞게 채웁니다.

환경변수 하나하나의 뜻과 필수 여부는 [`ENVIRONMENT.md`](ENVIRONMENT.md)에 있습니다.

> **이 가이드의 검증 상태**
>
> - **확인함**: 패키징한 JAR가 `.env` 없이 환경변수만으로 운영 프로필(`prod`)로 기동하고, 빈 DB에 Flyway가 V1~V5를 적용하며, 헬스체크·쿠키 속성·관리 API 보호·CORS가 동작함. 운영 설정이 잘못되면(`FRONTEND_URL` 누락, localhost 주소, 짧은 비밀, 빈 관리 키) 기동이 실패하고 메시지에 값이 노출되지 않음. Dockerfile·compose·nginx 설정의 구조(DB 포트 비공개, 비루트, 비밀값 리터럴 없음 등)는 자동 테스트가 검사함.
> - **확인하지 못함**: 작성 환경에 Docker가 없어 **이미지 빌드, `docker compose` 실행, nginx 컨테이너의 실제 프록시 동작, 컨테이너 헬스체크는 한 번도 실행하지 못했습니다.** 아래 명령은 문법과 구조만 검증된 상태입니다. 첫 배포에서 실패하는 단계가 있으면 알려 주세요.

## 1. 구성

```
인터넷 ──▶ [TLS 종단: 클라우드 로드밸런서 또는 리버스 프록시]   ← 이 스택에는 포함되어 있지 않음
              │
              ▼  HTTP (HTTP_PORT, 기본 80)
         ┌──────────┐   /api/*    ┌──────────┐        ┌──────────────┐
         │ frontend │ ──────────▶ │ backend  │ ─────▶ │ db           │
         │ (nginx)  │  내부망      │ (Spring) │ 내부망  │ PostGIS 16   │
         └──────────┘             └──────────┘        └──────────────┘
          공개 진입점              127.0.0.1:8080       호스트에 포트 없음
                                   (서버 안 관리용)
```

- **공개되는 것은 프론트(nginx) 하나**입니다. 브라우저는 `/api`를 같은 주소로 호출하고 nginx가 백엔드로 넘기므로 CORS·터널·별도 API 도메인이 필요 없습니다.
- 관리용 경로(`/api/data/*`, `/api/walk-difficulty/*`, 그리고 `/api/safety`·`/api/steep-slope`·`/api/density-grid`의 쓰기 요청)는 nginx가 공개 주소에서 막습니다. 관리 호출은 **서버 안에서 `127.0.0.1:8080`으로** 합니다.
- **HTTPS는 이 스택 밖에서 처리해야 합니다.** 로그인 쿠키는 `Secure` 속성이라 HTTPS가 아니면 브라우저가 보내지 않습니다. 앞단(로드밸런서, Caddy, nginx+certbot 등)이 TLS를 끝내고 `X-Forwarded-Proto`를 넘기면 됩니다.

## 2. 준비물

- Docker Engine + Docker Compose v2 (`docker compose` 명령, 최근 버전)
- 공개 주소(도메인) 하나와 그 주소의 HTTPS
- 카카오 개발자 앱(JavaScript 키, REST 키, 로그인 설정)
- T Map 앱 키, (선택) 기상청 키, (데이터 수집 시) 공공데이터·경기데이터드림·재난안전데이터 키
- (보행난이도를 쓰려면) Copernicus DEM GeoTIFF, (보안등 수집 시) 보안등 CSV

## 3. 시크릿 만들기

서버에서 한 번 만들어 `.env`에 붙여 넣습니다. 터미널 기록·채팅·저장소에 남기지 마세요.

```bash
# JWT_SECRET — 32바이트 이상 (아래는 64자)
openssl rand -base64 48

# ADMIN_API_KEY — 16자 이상 (아래는 64자)
openssl rand -hex 32

# DB_PASSWORD — 특수문자 없이 길게
openssl rand -hex 24
```

- `JWT_SECRET`을 바꾸면 모든 사용자의 로그인이 풀립니다.
- `ADMIN_API_KEY`가 비어 있거나 16자 미만이면 운영 프로필은 **기동하지 않습니다**.

## 4. `.env` 준비

```bash
cp .env.example .env
chmod 600 .env          # 서버에서만 보관, 커밋 금지
```

운영에서 반드시 채울 키(이름만):

| 키 | 값의 형태 |
|---|---|
| `DB_NAME` `DB_USERNAME` `DB_PASSWORD` | 운영 DB 이름·계정·비밀번호 |
| `DB_URL` | 아무 값이나 있어도 됨 — 운영 compose가 컨테이너 내부 주소(`db:5432`)로 덮어씀 |
| `JWT_SECRET` `ADMIN_API_KEY` | 3절에서 만든 값 |
| `FRONTEND_URL` | 사이트 공개 주소 전체, 예: `https://사이트주소` (localhost 불가) |
| `KAKAO_REST_API_KEY` `KAKAO_OAUTH_CLIENT_SECRET` | 카카오 로그인 |
| `KAKAO_OAUTH_REDIRECT_URI` | `https://사이트주소/api/auth/kakao/callback` |
| `VITE_KAKAO_API_KEY` | 카카오 JavaScript 키(이미지 빌드 때 번들에 들어가는 공개 값) |
| `TMAP_APP_KEY` | 경로 검색 |

선택: `WEATHER_API_KEY`(날씨), 수집용 키들(`PUBLIC_DATA_API_KEY`, `GYEONGGI_API_KEY`, `GYEONGGI_UPHILL_LANE_API_KEY`, `DISASTER_*`), `HTTP_PORT`, `DATA_DIR` 등.
`TEST_DB_*`는 운영에 필요 없습니다.

## 5. 카카오 개발자 콘솔 설정

- **플랫폼 > Web 사이트 도메인**에 `https://사이트주소`를 등록합니다. 등록하지 않으면 지도가 뜨지 않습니다.
- **카카오 로그인 > Redirect URI**에 `KAKAO_OAUTH_REDIRECT_URI`와 **똑같은 값**을 등록합니다.
- JavaScript 키는 브라우저에 노출되는 키이므로 위 도메인으로만 제한해 쓰세요.

## 6. 데이터 파일 두기 (선택)

이미지에는 데이터 파일을 넣지 않습니다. 서버에 디렉터리를 만들고 `DATA_DIR`(기본 `./data`)로 가리킵니다. 컨테이너에는 `/data`로 **읽기 전용** 마운트됩니다.

```
data/
├── dem/Copernicus_DSM_COG_10_N37_00_E127_00_DEM.tif   # 보행난이도 경사 격자용 (없으면 격자 적재가 409로 거부됨)
└── seongnam_security_light.csv                         # 성남시 보안등 CSV(CP949) (없으면 그 소스 수집이 실패)
```

## 7. 이미지 빌드와 기동

```bash
docker compose -f docker-compose.prod.yml build
docker compose -f docker-compose.prod.yml up -d
docker compose -f docker-compose.prod.yml ps        # db, backend, frontend 모두 healthy 가 될 때까지
```

- 필수 환경변수가 비어 있으면 compose가 어느 변수가 빠졌는지 알려 주며 시작하지 않습니다.
- 백엔드는 DB가 healthy가 된 뒤 시작하고, 프론트는 백엔드가 healthy가 된 뒤 시작합니다.
- 백엔드 이미지는 테스트를 돌리지 않고 빌드합니다(테스트는 CI가 실제 PostGIS로 돌립니다).

## 8. DB 초기화 (자동)

별도 작업이 없습니다. 백엔드가 처음 뜰 때 **Flyway가 스키마를 만듭니다**(V1 기본 스키마·PostGIS 확장 → … → V5 경사 격자). `postgis/postgis` 이미지의 `POSTGRES_USER`는 슈퍼유저라 확장 생성이 됩니다.

```bash
docker compose -f docker-compose.prod.yml logs backend | grep -E "Migrating schema|Successfully applied"
```

> 운영 보안을 더 높이려면 앱 전용 비슈퍼유저 계정을 만들고 PostGIS 확장은 관리자가 미리 만들어 두세요(개발 PC가 이 방식으로 동작함). 이 가이드의 기본 구성은 단순함을 위해 `POSTGRES_USER` 하나를 씁니다.

## 9. 헬스체크

서버 안에서:

```bash
# 백엔드(DB 연결 포함) — {"status":"UP"} 이면 정상
curl -s http://127.0.0.1:8080/actuator/health
curl -s http://127.0.0.1:8080/actuator/health/readiness

# 프론트 → 백엔드 프록시까지 한 번에
curl -s -o /dev/null -w "%{http_code}\n" http://127.0.0.1/            # 200 (HTTP_PORT 를 바꿨다면 그 포트)
curl -s -o /dev/null -w "%{http_code}\n" http://127.0.0.1/api/density-grid   # 200
```

공개 주소에서 관리 경로가 막혀 있는지:

```bash
curl -s -o /dev/null -w "%{http_code}\n" -X POST https://사이트주소/api/density-grid/recalculate   # 403 이어야 함
curl -s -o /dev/null -w "%{http_code}\n" -X POST https://사이트주소/api/walk-difficulty/grid/rebuild # 404 이어야 함
```

`actuator`는 health 외에는 열려 있지 않고(다른 경로는 404), health 응답에는 세부 정보가 없습니다. nginx는 `/actuator`를 백엔드로 넘기지 않으므로 헬스는 서버 안에서만 봅니다. **공개 주소의 `/actuator/…`는 프론트 화면(index.html)이 200으로 응답하니 외부 모니터링의 헬스 판정에 쓰지 마세요.**

## 10. 첫 데이터 채우기

처음에는 시설·격자 데이터가 비어 있어 지도에 아무것도 보이지 않습니다. **서버 안에서** 백엔드로 직접 호출합니다(`ADMIN_API_KEY`는 `.env`에서 읽음).

```bash
KEY=$(grep '^ADMIN_API_KEY=' .env | cut -d= -f2-)
B=http://127.0.0.1:8080

curl -X POST -H "X-Admin-Api-Key: $KEY" "$B/api/safety/facilities/sync"        # 안전시설 수집(오래 걸릴 수 있음)
curl -X POST -H "X-Admin-Api-Key: $KEY" "$B/api/density-grid/recalculate"      # 시설 수집 뒤에
curl -X POST -H "X-Admin-Api-Key: $KEY" "$B/api/walk-difficulty/grid/rebuild"  # DEM 파일이 있을 때
curl -X POST -H "X-Admin-Api-Key: $KEY" "$B/api/steep-slope/fetch/all"         # 급경사지
```

- 시설 수집 6개 소스 중 일부는 알려진 문제가 있습니다(README "알려진 한계"). 소스를 하나씩 보려면 `?source=GG_CCTV`처럼 지정합니다.
- 같은 호출을 다시 해도 안전합니다(수집은 중복을 넣지 않고, 격자 계산·적재는 전체 교체입니다).

## 11. 운영

| 작업 | 방법 |
|---|---|
| 로그 | `docker compose -f docker-compose.prod.yml logs -f backend` (표준 출력, 컨테이너당 10MB × 5개 회전) |
| 업데이트 | `git pull` → `docker compose -f docker-compose.prod.yml build` → `up -d` (Flyway가 새 마이그레이션을 적용) |
| 롤백 | 이전 커밋으로 되돌려 다시 빌드. 이미지 태그를 따로 쓰려면 `IMAGE_TAG`. **Flyway 마이그레이션은 되돌려지지 않으니** 스키마가 바뀐 버전 사이를 오갈 때는 DB 백업을 먼저 복원하세요 |
| DB 백업 | `docker compose -f docker-compose.prod.yml exec db sh -c 'pg_dump -U "$POSTGRES_USER" "$POSTGRES_DB"' > backup.sql` |
| DB 복원 | 빈 DB에 `psql`로 적용(PostGIS 확장이 있는 DB에) |
| 시크릿 교체 | `.env` 수정 → `up -d`. `JWT_SECRET` 교체는 전체 로그아웃, `DB_PASSWORD`는 DB 안의 비밀번호도 같이 바꿔야 함 |
| 중지 | `docker compose -f docker-compose.prod.yml down` (데이터 볼륨은 유지, 삭제는 `down -v` — **DB 데이터가 사라집니다**) |

## 12. 클라우드에 올릴 때 (대상 미정)

특정 클라우드를 가정하지 않았습니다. 어디에 올리든 필요한 것은 같습니다.

- **컨테이너 이미지 2개**: `backend/Dockerfile`, `frontend/Dockerfile`(빌드 인자 `VITE_KAKAO_API_KEY`).
- **PostGIS가 되는 PostgreSQL**: 관리형 DB를 쓰려면 PostGIS 확장을 지원하는지, 앱 계정이 확장을 만들 수 있는지(또는 관리자가 미리 만들어 둘 수 있는지) 확인하세요. 버전은 PostgreSQL 16 + PostGIS 3.4로 CI에서 시험합니다.
- **HTTPS 종단과 `X-Forwarded-Proto`**: 앞단 로드밸런서가 처리.
- **프론트(nginx)의 DNS 주소**: 백엔드 주소를 요청 때마다 다시 해석하도록 DNS 서버 주소를 `NGINX_RESOLVER`(프론트 컨테이너 환경변수, 기본값 Docker의 `127.0.0.11`)로 받습니다. Docker 밖(예: 다른 컨테이너 플랫폼)에서는 그 환경의 DNS 주소로 바꾸세요.
- **백엔드 환경변수는 플랫폼의 비밀 저장소로** 주입하고(`.env` 파일 대신), 프론트 컨테이너에는 `BACKEND_URL`(백엔드 내부 주소)만 필요합니다.
- 관리 호출은 공개 주소에서 막혀 있으므로, 플랫폼의 "컨테이너에 접속/일회성 작업" 기능이나 내부 네트워크에서 백엔드로 호출해야 합니다.

## 13. 문제 해결

| 증상 | 원인과 확인 |
|---|---|
| `backend`가 곧바로 종료됨, 로그에 "운영 설정이 올바르지 않습니다: …" | 나열된 설정 이름을 `.env`에서 고칩니다(메시지에는 값이 없고 이름·이유만 있음). 흔한 원인: `FRONTEND_URL`·`KAKAO_OAUTH_REDIRECT_URI`에 localhost, 짧은 `JWT_SECRET`, 빈 `ADMIN_API_KEY` |
| 로그에 "Could not resolve placeholder 'X'" | 그 환경변수가 없습니다(`DB_URL`·`DB_USERNAME`·`DB_PASSWORD`·`JWT_SECRET`은 없으면 기동 불가) |
| compose가 "… 이 필요합니다"라며 시작하지 않음 | 안내된 변수를 `.env`에 채웁니다 |
| 로그인이 되지 않음 | ① HTTPS인지(쿠키가 `Secure`) ② 카카오 콘솔의 Redirect URI가 `KAKAO_OAUTH_REDIRECT_URI`와 글자 그대로 같은지 ③ `FRONTEND_URL`이 실제 접속 주소와 같은지(CORS) |
| 지도가 뜨지 않음 | 카카오 콘솔의 Web 도메인에 사이트 주소가 없거나, 이미지를 `VITE_KAKAO_API_KEY` 없이 빌드함(키는 빌드 때 번들에 들어가므로 바꾸면 **프론트 이미지를 다시 빌드**) |
| 경로 검색이 느림 | 후보 4종을 T Map에 순차 호출합니다(최악 약 20초). nginx 프록시 읽기 제한은 60초 |
| `grid/rebuild`가 409 | DEM 파일이 `DATA_DIR/dem/` 아래에 없습니다 |
| `up`이 오래 걸리고 backend가 starting | 첫 기동은 마이그레이션이 있어 오래 걸릴 수 있습니다(헬스체크 시작 유예 60초) |

## 14. 사용자가 직접 정해야 하는 것 (이 단계에서 멈추는 지점)

1. **클라우드(또는 서버) 대상 선택** — 위 12절을 그 환경에 맞게 채웁니다.
2. 계정·프로젝트·도메인·HTTPS 준비.
3. 실제 배포(이 가이드 순서대로).

채울 값(키 이름): `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `ADMIN_API_KEY`, `FRONTEND_URL`, `KAKAO_REST_API_KEY`, `KAKAO_OAUTH_CLIENT_SECRET`, `KAKAO_OAUTH_REDIRECT_URI`, `VITE_KAKAO_API_KEY`, `TMAP_APP_KEY` (필수), `WEATHER_API_KEY` 외 수집용 키(선택).
