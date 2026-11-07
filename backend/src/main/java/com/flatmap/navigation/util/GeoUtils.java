package com.flatmap.navigation.util;

/**
 * 위경도(WGS84) 좌표 간 거리 계산 유틸.
 */
public final class GeoUtils {

    private static final double EARTH_RADIUS_METERS = 6371000;

    private GeoUtils() {}

    /**
     * Haversine 공식으로 두 좌표 간 거리(미터)를 계산한다.
     */
    public static double haversineMeters(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_METERS * c;
    }
}
