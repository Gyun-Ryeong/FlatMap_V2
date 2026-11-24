package com.flatmap.navigation.density;

import com.flatmap.navigation.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 격자 재계산 — 셀 기준 (i, j) 는 원점 (경도 127.04, 위도 37.33), 셀 크기 경도 0.0034 x 위도 0.0027.
 * 셀 (10, 20): 경도 [127.074, 127.0774) x 위도 [37.384, 37.3867)
 */
@IntegrationTest
class FacilityDensityGridServiceTest {

    private static final String INSERT =
            "INSERT INTO safety_facility (facility_type, name, location, data_source) " +
            "VALUES (?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326), 'TEST')";

    @Autowired
    FacilityDensityGridService service;
    @Autowired
    FacilityDensityGridRepository repository;
    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM safety_facility");
        jdbc.update("DELETE FROM facility_density_grid");
    }

    private int seq = 0;

    private void add(String type, double lat, double lng) {
        jdbc.update(INSERT, type, "f" + (seq++), lng, lat);
    }

    /** 셀 (i, j) 의 안쪽 점 (셀 중앙에서 k 만큼 밀어 서로 다른 좌표를 만든다). */
    private void addIn(String type, int i, int j, int k) {
        double lng = 127.04 + i * 0.0034 + 0.0005 + k * 0.0001;
        double lat = 37.33 + j * 0.0027 + 0.0005 + k * 0.0001;
        add(type, lat, lng);
    }

    private Map<String, DensityCell> byIndex() {
        return service.findCells().stream()
                .collect(Collectors.toMap(c -> c.gridI() + "," + c.gridJ(), c -> c));
    }

    @Test
    void emptyFacilityTableStoresNoCells() {
        FacilityDensityGridService.RecalcResult r = service.recalculate();
        assertThat(r.totalCells()).isZero();
        assertThat(repository.count()).isZero();
    }

    @Test
    void cellsWithNoFacilityAndNoNeighborFacilityAreNotStored() {
        addIn("CCTV", 10, 20, 0);

        service.recalculate();

        Map<String, DensityCell> cells = byIndex();
        // 시설 셀 (10,20) 과 인접 8칸 = 9개. 그 바깥(예: (12,20), (10,22), (0,0))은 저장하지 않는다.
        assertThat(cells).hasSize(9);
        assertThat(cells).containsKeys("10,20", "9,19", "11,21", "9,21", "11,19", "10,19", "10,21", "9,20", "11,20");
        assertThat(cells).doesNotContainKeys("12,20", "10,22", "8,20", "0,0");
    }

    @Test
    void neighborsWithZeroFacilitiesAreKeptButMarkedLowDensityWithZeroCounts() {
        addIn("CCTV", 10, 20, 0);
        service.recalculate();

        DensityCell neighbor = byIndex().get("11,20");
        assertThat(neighbor.facilityCount()).isZero();
        assertThat(neighbor.densityScore()).isEqualByComparingTo("10.00");   // 0 → 10점
        assertThat(neighbor.densityLevel()).isEqualTo(DensityLevel.LOW_DENSITY);
    }

    @Test
    void countsAreSplitByTypeAndScoredWithTheConfiguredWeights() {
        // 셀 (10,20): CCTV 2 + 보안등 1 + 경찰 1 → 실제 개수 4, 가중 2 + 1 + 5 = 8
        // 점수: 5~10 구간 61 + (8-5) * 4 = 73 → HIGH_DENSITY (60 초과)
        addIn("CCTV", 10, 20, 0);
        addIn("CCTV", 10, 20, 1);
        addIn("SECURITY_LIGHT", 10, 20, 2);
        addIn("POLICE", 10, 20, 3);

        service.recalculate();

        DensityCell cell = byIndex().get("10,20");
        assertThat(cell.cctvCount()).isEqualTo(2);
        assertThat(cell.lightCount()).isEqualTo(1);
        assertThat(cell.policeCount()).isEqualTo(1);
        assertThat(cell.facilityCount()).isEqualTo(4);
        assertThat(cell.densityScore()).isEqualByComparingTo("73.00");
        assertThat(cell.densityLevel()).isEqualTo(DensityLevel.HIGH_DENSITY);
        assertThat(cell.scoreVersion()).isEqualTo(1);
    }

    @Test
    void levelBoundariesFollowTheConfiguredScores() {
        // 가중 1 → 30점(경계 포함 LOW_DENSITY), 가중 2 → 40점 MEDIUM, 가중 5 → 61점 HIGH_DENSITY
        addIn("CCTV", 2, 2, 0);
        addIn("CCTV", 20, 2, 0);
        addIn("CCTV", 20, 2, 1);
        for (int k = 0; k < 5; k++) addIn("SECURITY_LIGHT", 40, 2, k);

        service.recalculate();

        Map<String, DensityCell> cells = byIndex();
        assertThat(cells.get("2,2").densityScore()).isEqualByComparingTo("30.00");
        assertThat(cells.get("2,2").densityLevel()).isEqualTo(DensityLevel.LOW_DENSITY);
        assertThat(cells.get("20,2").densityScore()).isEqualByComparingTo("40.00");
        assertThat(cells.get("20,2").densityLevel()).isEqualTo(DensityLevel.MEDIUM);
        assertThat(cells.get("40,2").densityScore()).isEqualByComparingTo("61.00");
        assertThat(cells.get("40,2").densityLevel()).isEqualTo(DensityLevel.HIGH_DENSITY);
    }

    @Test
    void polygonComesFromTheIndexAndContainsItsFacilities() {
        addIn("CCTV", 10, 20, 0);
        service.recalculate();

        DensityCell cell = byIndex().get("10,20");
        assertThat(cell.minLng()).isCloseTo(127.074, within(1e-9));
        assertThat(cell.maxLng()).isCloseTo(127.0774, within(1e-9));
        assertThat(cell.minLat()).isCloseTo(37.384, within(1e-9));
        assertThat(cell.maxLat()).isCloseTo(37.3867, within(1e-9));
        assertThat(cell.bounds()).hasSize(5);
        assertThat(cell.bounds().get(0)).containsExactly(cell.minLat(), cell.minLng());
        assertThat(cell.bounds().get(4)).containsExactly(cell.minLat(), cell.minLng());

        Boolean contained = jdbc.queryForObject(
                "SELECT bool_and(ST_Contains(g.grid_polygon, f.location)) FROM facility_density_grid g, safety_facility f " +
                        "WHERE g.grid_i = 10 AND g.grid_j = 20", Boolean.class);
        assertThat(contained).isTrue();
    }

    @Test
    void aFacilityOnACellEdgeBelongsToExactlyOneCell() {
        // 경도 127.0434 = 셀 0 과 셀 1 의 경계 → 반개구간이라 셀 1 에만 들어간다.
        add("CCTV", 37.3340, 127.0434);

        service.recalculate();

        Map<String, DensityCell> cells = byIndex();
        assertThat(cells.get("1,1").cctvCount()).isEqualTo(1);
        assertThat(cells.get("0,1").cctvCount()).isZero();
        assertThat(cells.values().stream().mapToInt(DensityCell::cctvCount).sum()).isEqualTo(1);
    }

    @Test
    void facilitiesOutsideTheGridAreIgnored() {
        add("CCTV", 37.60, 127.10);   // 위도 범위 밖
        add("CCTV", 37.40, 126.90);   // 경도 범위 밖

        assertThat(service.recalculate().totalCells()).isZero();
    }

    @Test
    void recalculatingTwiceGivesTheSameRowsWithoutDuplicates() {
        addIn("CCTV", 10, 20, 0);
        addIn("POLICE", 30, 40, 0);

        FacilityDensityGridService.RecalcResult first = service.recalculate();
        List<DensityCell> before = service.findCells();
        FacilityDensityGridService.RecalcResult second = service.recalculate();

        assertThat(second.totalCells()).isEqualTo(first.totalCells()).isEqualTo(18);
        assertThat(repository.count()).isEqualTo(18);
        assertThat(service.findCells()).extracting(c -> c.gridI() + "," + c.gridJ() + ":" + c.densityScore())
                .containsExactlyElementsOf(before.stream().map(c -> c.gridI() + "," + c.gridJ() + ":" + c.densityScore()).toList());
    }

    @Test
    void recalculationReplacesStaleCells() {
        addIn("CCTV", 10, 20, 0);
        service.recalculate();
        jdbc.update("DELETE FROM safety_facility");
        addIn("CCTV", 30, 40, 0);

        service.recalculate();

        assertThat(byIndex()).containsKey("30,40").doesNotContainKey("10,20");
    }

    @Test
    void resultSummaryCountsByLevel() {
        addIn("CCTV", 10, 20, 0);   // 셀 가중 1 → 30점 LOW_DENSITY, 인접 8칸도 LOW_DENSITY
        FacilityDensityGridService.RecalcResult r = service.recalculate();
        assertThat(r.byLevel()).containsEntry(DensityLevel.LOW_DENSITY, 9)
                .containsEntry(DensityLevel.MEDIUM, 0).containsEntry(DensityLevel.HIGH_DENSITY, 0);
    }
}
