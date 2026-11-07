# CHANGELOG

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
