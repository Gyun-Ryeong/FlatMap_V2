-- 06 단계: 보행난이도(B축) 경사 격자.
--
-- DEM(Copernicus GLO-30, 1 arc-second ≈ 30m)의 한 픽셀이 셀 하나다. 경사 값은 DEM 에서 미리 계산해 넣고(적재 도구),
-- 경로 샘플링은 셀 인덱스(PK)로 조회한다 — 공간 인덱스가 필요 없다.
-- Copernicus DEM 은 픽셀 중심이 정수 초 좌표에 놓인다. 셀 (i, j) 의 중심 = (최소 경도 + i / cells_per_degree, 최소 위도 + j / cells_per_degree).
-- 셀 인덱스(가장 가까운 중심): cell_i = floor((경도 - 최소 경도) * cells_per_degree + 0.5), cell_j = floor((위도 - 최소 위도) * cells_per_degree + 0.5).
-- 격자 범위·해상도는 설정(walk.grid.*)이며 적재 도구와 샘플링이 같은 값을 쓴다.
--
-- 이 테이블은 보행난이도 전용이다. 급경사지(steep_slope_areas, 붕괴위험 지정지)와 컬럼·테이블을 섞지 않는다.
CREATE TABLE walk_slope_cell (
    cell_i       integer          NOT NULL CHECK (cell_i >= 0),
    cell_j       integer          NOT NULL CHECK (cell_j >= 0),
    slope_deg    double precision NOT NULL CHECK (slope_deg >= 0 AND slope_deg < 90),  -- 경사도(각도 °), % 아님
    elevation_m  real,
    PRIMARY KEY (cell_i, cell_j)
);

COMMENT ON TABLE walk_slope_cell IS '보행난이도(B축)용 DEM 경사 격자. 급경사지 붕괴위험 데이터와 무관.';
COMMENT ON COLUMN walk_slope_cell.slope_deg IS 'DEM 3x3(Horn) 경사도, 각도(°). 30m 격자 평활화로 실제보다 낮게 나오는 경향이 있다.';
