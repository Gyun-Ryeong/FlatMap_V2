-- ----------------------------------------------------------------------------
-- 02 spatial-core: steep_slope_areas 의 쓰기 원본을 geom 으로 전환한다.
--
-- 이전: latitude/longitude 가 원본이고 geom 이 거기서 파생되는 생성 컬럼이었다(애플리케이션이 lat/lng 를 썼다).
-- 이후: geom(Point, 4326)이 원본이고 latitude/longitude 는 geom 에서 파생되는 읽기 전용 생성 컬럼이다.
--       애플리케이션은 geom 만 쓴다(JdbcTemplate + ST_SetSRID(ST_MakePoint(lng, lat), 4326)).
--       기존 조회(SELECT latitude, longitude)와 검증 SQL 은 그대로 동작한다.
--
-- 기존 행은 geom 값이 보존된다(DROP EXPRESSION 은 값을 유지한 채 일반 컬럼으로 바꾼다. PostgreSQL 13+).
-- 좌표가 없던 행(latitude/longitude NULL)은 geom 도 NULL 이다.
-- ----------------------------------------------------------------------------

ALTER TABLE steep_slope_areas ALTER COLUMN geom DROP EXPRESSION;

ALTER TABLE steep_slope_areas
    DROP COLUMN latitude,
    DROP COLUMN longitude;

ALTER TABLE steep_slope_areas
    ADD COLUMN latitude  double precision GENERATED ALWAYS AS (ST_Y(geom)) STORED,
    ADD COLUMN longitude double precision GENERATED ALWAYS AS (ST_X(geom)) STORED;

COMMENT ON COLUMN steep_slope_areas.geom      IS '위치(WGS84 Point). 쓰기 원본.';
COMMENT ON COLUMN steep_slope_areas.latitude  IS 'geom 에서 파생(읽기 전용).';
COMMENT ON COLUMN steep_slope_areas.longitude IS 'geom 에서 파생(읽기 전용).';
