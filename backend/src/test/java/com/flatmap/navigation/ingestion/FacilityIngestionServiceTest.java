package com.flatmap.navigation.ingestion;

import com.flatmap.navigation.model.SafetyFacility;
import com.flatmap.navigation.repository.SafetyFacilityRepository;
import com.flatmap.navigation.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 PostGIS 테스트 DB 에 적재한다. 외부 호출은 없다(리더를 가짜로 대체하거나 로컬 CP949 픽스처를 읽는다).
 */
@IntegrationTest
class FacilityIngestionServiceTest {

    @Autowired
    SafetyFacilityRepository repository;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PlatformTransactionManager txManager;

    @TempDir
    Path dir;

    @BeforeEach
    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM safety_facility");
    }

    /** 지정한 후보 행을 돌려주는 가짜 리더. */
    private static FacilityReader reader(FacilitySource source, Supplier<List<SafetyFacility>> rows) {
        return new FacilityReader() {
            @Override
            public FacilitySource source() {
                return source;
            }

            @Override
            public SourceReading read() {
                List<SafetyFacility> list = rows.get();
                SourceReading.Stats stats = new SourceReading.Stats();
                list.forEach(f -> stats.countRead());
                return stats.build(list);
            }
        };
    }

    private FacilityIngestionService service(FacilityReader... readers) {
        return new FacilityIngestionService(List.of(readers), repository, txManager);
    }

    private static SafetyFacility f(FacilitySource s, String name, String addr, double lat, double lng) {
        return new SafetyFacility(s.facilityType(), name, addr, null, lat, lng, s.code());
    }

    private static List<SafetyFacility> cctv(int n) {
        List<SafetyFacility> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(f(FacilitySource.GG_CCTV, "CCTV-" + i, "경기도 성남시 분당구 " + i, 37.35 + i * 0.001, 127.10));
        }
        return list;
    }

    private long count() {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM safety_facility", Long.class);
        return n == null ? 0 : n;
    }

    // ---- 1. 멱등 ----

    @Test
    void runningTheSameSyncTwiceDoesNotGrowTheTable() {
        FacilityIngestionService svc = service(reader(FacilitySource.GG_CCTV, () -> cctv(50)));

        SourceResult first = svc.sync(FacilitySource.GG_CCTV);
        long afterFirst = count();
        SourceResult second = svc.sync(FacilitySource.GG_CCTV);

        assertThat(first.isSuccess()).isTrue();
        assertThat(first.inserted()).isEqualTo(50);
        assertThat(afterFirst).isEqualTo(50);
        assertThat(second.isSuccess()).isTrue();
        assertThat(second.inserted()).isZero();
        assertThat(count()).isEqualTo(50);
    }

    @Test
    void firstSourceWinsWhenTwoSourcesShareACoordinate() {
        // 기본 정책(DECISIONS): 먼저 들어간 행 유지, 뒤 행은 건너뜀.
        FacilityIngestionService svc = service(
                reader(FacilitySource.GG_CCTV, () -> List.of(f(FacilitySource.GG_CCTV, "경기 CCTV", "성남시 A", 37.40, 127.10))),
                reader(FacilitySource.ODCLOUD_SEONGNAM_CCTV,
                        () -> List.of(f(FacilitySource.ODCLOUD_SEONGNAM_CCTV, "생활안전CCTV", "성남시 A", 37.40, 127.10))));

        svc.sync(FacilitySource.GG_CCTV);
        SourceResult second = svc.sync(FacilitySource.ODCLOUD_SEONGNAM_CCTV);

        assertThat(second.isSuccess()).isTrue();
        assertThat(second.inserted()).isZero();
        assertThat(jdbc.queryForObject("SELECT data_source FROM safety_facility", String.class)).isEqualTo("GG_CCTV");
    }

    // ---- 4. CP949 한글이 DB 에 정상 저장 ----

    @Test
    void cp949CsvKoreanIsStoredCorrectly() throws Exception {
        Path csv = dir.resolve("lights.csv");
        String content = "보안등위치명,설치개수,소재지도로명주소,소재지지번주소,위도,경도\n"
                + "정자1동 001,1,경기도 성남시 분당구 불정로 1,경기도 성남시 분당구 정자동 1,37.3650,127.1080\n"
                + "수진 002,1,,경기도 성남시 수정구 수진동 2,37.4380,127.1370\n"
                + "상면876 TS,1,,경기도 가평군 상면 행현리663-19,37.748447,127.353638\n";
        Files.write(csv, content.getBytes(Charset.forName("x-windows-949")));
        IngestionSettings settings = new IngestionSettings(0, 1000, 10, csv.toString(), "", "");
        FacilityIngestionService svc = service(new SeongnamCsvReader(settings));

        SourceResult result = svc.sync(FacilitySource.SEONGNAM_CSV);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.read()).isEqualTo(3);
        assertThat(result.excluded()).isEqualTo(1);
        assertThat(result.inserted()).isEqualTo(2);
        List<String> names = jdbc.queryForList(
                "SELECT name FROM safety_facility WHERE data_source = 'SEONGNAM_CSV' ORDER BY name", String.class);
        assertThat(names).containsExactly("수진 002", "정자1동 001");
        String addr = jdbc.queryForObject(
                "SELECT address FROM safety_facility WHERE name = '정자1동 001'", String.class);
        assertThat(addr).isEqualTo("경기도 성남시 분당구 불정로 1");
        // 같은 파일을 다시 동기화해도 행 수 불변
        svc.sync(FacilitySource.SEONGNAM_CSV);
        assertThat(count()).isEqualTo(2);
    }

    // ---- 5. 한글 비율 검증 실패 → 롤백, 다른 소스는 진행 ----

    @Test
    void badBatchIsRejectedAndNothingIsWritten() {
        List<SafetyFacility> broken = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            broken.add(f(FacilitySource.GG_CCTV, "????", "경기도 성남시 ??? " + i, 37.35 + i * 0.001, 127.10));
        }
        FacilityIngestionService svc = service(reader(FacilitySource.GG_CCTV, () -> broken));

        SourceResult result = svc.sync(FacilitySource.GG_CCTV);

        assertThat(result.isSuccess()).isFalse();
        assertThat(count()).isZero();
    }

    @Test
    void failedQualityCheckAfterLoadRollsBackTheInsertedRows() {
        // 같은 출처에 깨진 행이 이미 많이 있으면, 새 행(정상)을 넣은 뒤 소스 전체 비율이 5% 를 넘어 롤백된다.
        for (int i = 0; i < 10; i++) {
            jdbc.update("INSERT INTO safety_facility (facility_type, name, address, location, data_source) " +
                    "VALUES ('CCTV', '???', '??', ST_SetSRID(ST_MakePoint(?, 37.45), 4326), 'GG_CCTV')", 127.05 + i * 0.001);
        }
        FacilityIngestionService svc = service(reader(FacilitySource.GG_CCTV, () -> cctv(1)));

        SourceResult result = svc.sync(FacilitySource.GG_CCTV);

        assertThat(result.isSuccess()).isFalse();
        assertThat(count()).isEqualTo(10); // 새 정상 행 1건도 남지 않았다(롤백)
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM safety_facility WHERE name LIKE 'CCTV-%'", Long.class)).isZero();
    }

    @Test
    void oneFailingSourceDoesNotStopTheOthers() {
        FacilityIngestionService svc = service(
                reader(FacilitySource.GG_CCTV, () -> { throw new IngestionException("boom"); }),
                reader(FacilitySource.GG_SECURITY_LIGHT, () -> { throw new IllegalStateException("jdbc:postgresql://h/db?password=x"); }),
                reader(FacilitySource.GG_POLICE,
                        () -> List.of(f(FacilitySource.GG_POLICE, "수진지구대", "경기도 성남시 수정구 1", 37.44, 127.13))));

        Map<String, SourceResult> bySource = svc.syncAll().stream()
                .collect(Collectors.toMap(SourceResult::source, r -> r));

        assertThat(bySource.get("GG_CCTV").isSuccess()).isFalse();
        assertThat(bySource.get("GG_SECURITY_LIGHT").isSuccess()).isFalse();
        assertThat(bySource.get("GG_POLICE").isSuccess()).isTrue();
        assertThat(bySource.get("GG_POLICE").inserted()).isEqualTo(1);
        // 리더가 등록되지 않은 소스도 실패로 보고되고 전체는 계속 진행된다
        assertThat(bySource.get("SEONGNAM_CSV").isSuccess()).isFalse();
        assertThat(bySource).hasSize(FacilitySource.values().length);
        assertThat(count()).isEqualTo(1);
    }

    @Test
    void emptyResultIsReportedAsFailure() {
        FacilityIngestionService svc = service(reader(FacilitySource.GG_CCTV, List::of));
        assertThat(svc.sync(FacilitySource.GG_CCTV).isSuccess()).isFalse();
    }

    @Test
    void sourceWithoutAddressColumnIsNotPenalizedForEmptyAddress() {
        List<SafetyFacility> rows = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            rows.add(f(FacilitySource.ODCLOUD_ILLUMINATION, "가로등", null, 37.35 + i * 0.001, 127.10));
        }
        FacilityIngestionService svc = service(reader(FacilitySource.ODCLOUD_ILLUMINATION, () -> rows));

        SourceResult result = svc.sync(FacilitySource.ODCLOUD_ILLUMINATION);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.inserted()).isEqualTo(20);
    }

    @Test
    void repositoryBadTextCountsMatchTextQualityDefinition() {
        String sql = "INSERT INTO safety_facility (facility_type, name, address, location, data_source) " +
                "VALUES ('CCTV', ?, ?, ST_SetSRID(ST_MakePoint(?, 37.45), 4326), 'X')";
        jdbc.update(sql, "정상", "경기도 성남시", 127.051);
        jdbc.update(sql, "???", "경기도 성남시", 127.052);          // name 이 ? 만
        jdbc.update(sql, "정상", "  ", 127.053);                    // address 공백
        jdbc.update(sql, "정상", "경기도 �성남", 127.054);     // 깨진 문자
        jdbc.update(sql, "정상", null, 127.055);                    // address NULL

        long[] withAddress = repository.badTextCounts("X", true);
        long[] nameOnly = repository.badTextCounts("X", false);

        assertThat(withAddress).containsExactly(5, 4);
        assertThat(nameOnly).containsExactly(5, 1);
    }
}
