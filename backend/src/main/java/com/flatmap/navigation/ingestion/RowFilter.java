package com.flatmap.navigation.ingestion;

import com.flatmap.navigation.model.SafetyFacility;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 모든 리더가 공유하는 행 검증·정규화. 성남 범위(좌표 AND 주소)를 한 곳에서 적용한다.
 */
final class RowFilter {

    private static final int MAX_TEXT = 255; // safety_facility.name / address 길이

    private RowFilter() {}

    /**
     * @return 적재 후보, 또는 null(제외됨 — stats 에 사유가 기록된다)
     */
    static SafetyFacility accept(FacilitySource source, String name, String defaultName, String address,
                                 Double lat, Double lng, SourceReading.Stats stats) {
        if (lat == null || lng == null || !Double.isFinite(lat) || !Double.isFinite(lng)
                || (lat == 0.0 && lng == 0.0)) {
            stats.exclude(SourceReading.INVALID_COORDS);
            return null;
        }
        if (!SeongnamFilter.inBounds(lat, lng)) {
            stats.exclude(SourceReading.OUTSIDE_BOUNDS);
            return null;
        }
        String addr = clean(address);
        if (source.addressRequired()) {
            if (!SeongnamFilter.isSeongnamAddress(addr)) {
                stats.exclude(SourceReading.ADDRESS_NOT_SEONGNAM);
                return null;
            }
        } else if (addr != null && !SeongnamFilter.isSeongnamAddress(addr)) {
            // 주소 컬럼이 없는 소스라도 주소가 실려 온 행은 성남이어야 한다.
            stats.exclude(SourceReading.ADDRESS_NOT_SEONGNAM);
            return null;
        }
        String finalName = clean(name);
        if (finalName == null) finalName = defaultName;
        return new SafetyFacility(source.facilityType(), finalName, addr, null, lat, lng, source.code());
    }

    /** 앞뒤 공백 제거, 빈 값은 null, 컬럼 길이 초과분은 자른다. */
    static String clean(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        return t.length() > MAX_TEXT ? t.substring(0, MAX_TEXT) : t;
    }

    static Double parseDouble(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        try {
            return Double.parseDouble(t);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * API 키를 URL 쿼리에 넣을 값으로 만든다. 이미 퍼센트 인코딩된 키("%" 포함)는 그대로 두고,
     * 디코딩된 키(+, /, = 포함)는 인코딩한다.
     */
    static String encodeKey(String key) {
        if (key.contains("%")) return key;
        return URLEncoder.encode(key, StandardCharsets.UTF_8);
    }

    static String requireKey(String key) {
        if (key == null || key.isBlank()) throw new IngestionException("API key not configured");
        return key.trim();
    }
}
