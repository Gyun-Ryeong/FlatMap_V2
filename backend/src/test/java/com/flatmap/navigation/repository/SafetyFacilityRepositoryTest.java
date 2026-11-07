package com.flatmap.navigation.repository;

import com.flatmap.navigation.model.SafetyFacility;
import com.flatmap.navigation.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
class SafetyFacilityRepositoryTest {

    private static final String INSERT_RAW =
            "INSERT INTO safety_facility (facility_type, name, location, data_source) " +
            "VALUES (?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326), 'TEST')";

    @Autowired
    SafetyFacilityRepository repository;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM safety_facility");
    }

    private static SafetyFacility cctv(String name, double lat, double lng) {
        return new SafetyFacility(SafetyFacility.TYPE_CCTV, name, "성남시 테스트", null, lat, lng, "TEST");
    }

    @Test
    void duplicateNaturalKeyIsRejectedByUniqueConstraint() {
        jdbc.update(INSERT_RAW, "CCTV", "a", 127.1234, 37.4321);

        assertThatThrownBy(() -> jdbc.update(INSERT_RAW, "CCTV", "b", 127.1234, 37.4321))
                .isInstanceOf(DataIntegrityViolationException.class);

        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM safety_facility", Integer.class);
        assertThat(n).isEqualTo(1);
    }

    @Test
    void onConflictDoNothingSkipsDuplicatesWithoutError() {
        int first = repository.insertIgnoringDuplicates(List.of(cctv("a", 37.4321, 127.1234)));
        int second = repository.insertIgnoringDuplicates(List.of(
                cctv("same-place-other-name", 37.4321, 127.1234),   // 중복 → 건너뜀
                cctv("new-place", 37.4400, 127.1300)));              // 신규 → 삽입

        assertThat(first).isEqualTo(1);
        assertThat(second).isEqualTo(1);
        assertThat(repository.countByType("CCTV")).isEqualTo(2);
    }

    @Test
    void sameCoordinateWithDifferentTypeIsAllowed() {
        jdbc.update(INSERT_RAW, "CCTV", "cam", 127.1234, 37.4321);
        jdbc.update(INSERT_RAW, "SECURITY_LIGHT", "lamp", 127.1234, 37.4321);

        assertThat(repository.countByType("CCTV")).isEqualTo(1);
        assertThat(repository.countByType("SECURITY_LIGHT")).isEqualTo(1);
    }

    @Test
    void onlyKnownFacilityTypesAreAllowed_fireStationIsNotAType() {
        assertThatThrownBy(() -> jdbc.update(INSERT_RAW, "FIRE_STATION", "소방서", 127.12, 37.43))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update(INSERT_RAW, "POLICE", "경찰서", 127.12, 37.43);
        assertThat(repository.countByType("POLICE")).isEqualTo(1);
    }

    @Test
    void radiusQueriesUseMeters() {
        // 위도 0.001° ≈ 111m. 중심 시설과 그 북쪽 약 111m 떨어진 시설.
        repository.insertIgnoringDuplicates(List.of(
                cctv("center", 37.4000, 127.1200),
                cctv("north-111m", 37.4010, 127.1200)));

        assertThat(repository.findWithinMeters("CCTV", 37.4000, 127.1200, 50)).extracting(SafetyFacility::getName)
                .containsExactly("center");
        assertThat(repository.findWithinMeters("CCTV", 37.4000, 127.1200, 150)).extracting(SafetyFacility::getName)
                .containsExactlyInAnyOrder("center", "north-111m");
        assertThat(repository.countWithinMeters("CCTV", 37.4000, 127.1200, 150)).isEqualTo(2);
    }

    @Test
    void replaceAllReplacesOnlyTheGivenTypeAndSource() {
        jdbc.update("INSERT INTO safety_facility (facility_type, name, location, data_source) " +
                "VALUES ('CCTV', 'keep', ST_SetSRID(ST_MakePoint(127.10, 37.40), 4326), 'OTHER_SOURCE')");
        repository.replaceAll("CCTV", "SRC", List.of(
                new SafetyFacility("CCTV", "old", null, null, 37.41, 127.11, "SRC")));
        int inserted = repository.replaceAll("CCTV", "SRC", List.of(
                new SafetyFacility("CCTV", "new", null, null, 37.42, 127.12, "SRC")));

        assertThat(inserted).isEqualTo(1);
        List<String> names = repository.findByType("CCTV").stream().map(SafetyFacility::getName).toList();
        assertThat(names).containsExactlyInAnyOrder("keep", "new");
    }
}
