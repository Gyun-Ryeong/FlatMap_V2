package com.flatmap.navigation.ingestion;

import com.flatmap.navigation.model.SafetyFacility;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 한 소스를 읽은 결과: 적재 후보 행과, 읽은 행 수·제외 사유별 건수.
 * 제외 사유 키: invalid_coords, outside_bounds, address_not_seongnam, fire_station, not_police.
 */
public record SourceReading(List<SafetyFacility> facilities, int read, Map<String, Integer> excludedByReason) {

    public static final String INVALID_COORDS = "invalid_coords";
    public static final String OUTSIDE_BOUNDS = "outside_bounds";
    public static final String ADDRESS_NOT_SEONGNAM = "address_not_seongnam";
    public static final String FIRE_STATION = "fire_station";
    public static final String NOT_POLICE = "not_police";

    public int excluded() {
        return excludedByReason.values().stream().mapToInt(Integer::intValue).sum();
    }

    public int excluded(String reason) {
        return excludedByReason.getOrDefault(reason, 0);
    }

    /** 읽는 동안 건수를 세는 가변 집계. */
    public static final class Stats {
        private int read;
        private final Map<String, Integer> excluded = new LinkedHashMap<>();

        public void countRead() {
            read++;
        }

        public void exclude(String reason) {
            excluded.merge(reason, 1, Integer::sum);
        }

        public SourceReading build(List<SafetyFacility> facilities) {
            return new SourceReading(facilities, read, Collections.unmodifiableMap(new LinkedHashMap<>(excluded)));
        }
    }
}
