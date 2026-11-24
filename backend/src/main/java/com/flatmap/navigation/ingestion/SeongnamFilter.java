package com.flatmap.navigation.ingestion;

/**
 * 성남시 범위 판정. 모든 소스에 같은 기준을 쓴다: 좌표 범위 AND 주소에 "성남".
 * 좌표 범위는 성남시 전체를 넉넉히 덮는 사각형이며(docs/dev/ingestion-check.md 의 검증 SQL 과 같은 값),
 * 성남이 아닌 인접 지역은 주소 검사가 거른다.
 */
public final class SeongnamFilter {

    public static final double MIN_LAT = 37.33;
    public static final double MAX_LAT = 37.53;
    public static final double MIN_LNG = 127.04;
    public static final double MAX_LNG = 127.20;

    private SeongnamFilter() {}

    public static boolean inBounds(double lat, double lng) {
        return lat >= MIN_LAT && lat <= MAX_LAT && lng >= MIN_LNG && lng <= MAX_LNG;
    }

    public static boolean isSeongnamAddress(String address) {
        return address != null && address.contains("성남");
    }
}
