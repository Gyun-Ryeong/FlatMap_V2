package com.flatmap.navigation.density;

import com.flatmap.navigation.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 재계산은 단일 트랜잭션: 삽입 도중 실패하면 삭제도 되돌려져 기존 격자가 그대로 남는다. */
@IntegrationTest
class FacilityDensityGridRollbackTest {

    @Autowired
    FacilityDensityGridService service;
    @SpyBean
    FacilityDensityGridRepository repository;
    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void clean() {
        Mockito.reset(repository);
        jdbc.update("DELETE FROM safety_facility");
        jdbc.update("DELETE FROM facility_density_grid");
    }

    @Test
    void failureDuringInsertKeepsThePreviousGrid() {
        jdbc.update("INSERT INTO safety_facility (facility_type, name, location, data_source) " +
                "VALUES ('CCTV', 'a', ST_SetSRID(ST_MakePoint(127.0765, 37.3855), 4326), 'TEST')");
        service.recalculate();
        long before = repository.count();
        assertThat(before).isEqualTo(9);

        Mockito.doAnswer(inv -> {
            // 삭제는 이미 실행된 뒤 — 일부를 넣다가 실패하는 상황
            repository.deleteAll();
            throw new IllegalStateException("insert failed");
        }).when(repository).insertAll(Mockito.anyList(), Mockito.any(), Mockito.anyInt());

        assertThatThrownBy(() -> service.recalculate()).isInstanceOf(RuntimeException.class);

        Mockito.reset(repository);
        assertThat(repository.count()).as("롤백되어 이전 격자가 그대로").isEqualTo(before);
        assertThat(service.findCells()).hasSize(9);
    }

    @Test
    void failureDuringAggregationKeepsThePreviousGrid() {
        jdbc.update("INSERT INTO safety_facility (facility_type, name, location, data_source) " +
                "VALUES ('CCTV', 'a', ST_SetSRID(ST_MakePoint(127.0765, 37.3855), 4326), 'TEST')");
        service.recalculate();

        Mockito.doThrow(new IllegalStateException("boom")).when(repository).aggregateCells(Mockito.any());

        assertThatThrownBy(() -> service.recalculate()).isInstanceOf(RuntimeException.class);
        Mockito.reset(repository);
        assertThat(repository.count()).isEqualTo(9);
        assertThat(List.copyOf(service.findCells())).hasSize(9);
    }
}
