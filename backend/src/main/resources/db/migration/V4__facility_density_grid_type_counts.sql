-- ============================================================================
-- V4__facility_density_grid_type_counts.sql : 03 단계(route-score)
--
-- facility_density_grid 에 시설 종류별 개수 컬럼을 더한다.
--  * facility_count 는 격자 안 시설의 "실제 개수"(CCTV + 보안등 + 경찰)다.
--    SafetyRoad 의 danger_zone.facility_count 는 경찰을 가중(×5)한 값이라 이름과 의미가 어긋났다.
--    가중 합은 density_score 계산에만 쓰고 저장하지 않는다(가중치는 설정값이라 바뀔 수 있다).
--  * 테이블은 03 이전에는 비어 있었으므로 기존 행 보정은 필요 없다.
-- ============================================================================
ALTER TABLE facility_density_grid
    ADD COLUMN cctv_count   integer NOT NULL DEFAULT 0,
    ADD COLUMN light_count  integer NOT NULL DEFAULT 0,
    ADD COLUMN police_count integer NOT NULL DEFAULT 0,
    ADD CONSTRAINT chk_density_type_counts
        CHECK (cctv_count >= 0 AND light_count >= 0 AND police_count >= 0
               AND facility_count = cctv_count + light_count + police_count);

COMMENT ON COLUMN facility_density_grid.facility_count IS '격자 안 시설 실제 개수 = cctv_count + light_count + police_count (가중치 미적용)';
COMMENT ON COLUMN facility_density_grid.density_score  IS '가중 시설 수를 설정된 구간 함수로 바꾼 0~100 값. 검증되지 않은 설계 파라미터이며 위험도가 아니다.';
