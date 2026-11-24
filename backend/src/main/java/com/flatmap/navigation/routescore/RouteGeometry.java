package com.flatmap.navigation.routescore;

import java.util.List;

/** 경로 좌표 → WKT. 경로 전체를 LINESTRING 하나로 만들어 DB 에 한 번만 묻는다(DECISIONS §2). */
public final class RouteGeometry {

    private RouteGeometry() {}

    /**
     * 좌표는 {@code [위도, 경도]} 순이고 WKT 는 "경도 위도" 순이다.
     * 점이 하나뿐이면 POINT(길이 0), 둘 이상이면 LINESTRING.
     */
    public static String toWkt(List<double[]> latLngPoints) {
        if (latLngPoints == null || latLngPoints.isEmpty()) {
            throw new IllegalArgumentException("경로 좌표가 비어 있다.");
        }
        StringBuilder sb = new StringBuilder();
        for (double[] p : latLngPoints) {
            if (p == null || p.length < 2 || !Double.isFinite(p[0]) || !Double.isFinite(p[1])) {
                throw new IllegalArgumentException("경로 좌표가 올바르지 않다.");
            }
            if (sb.length() > 0) sb.append(", ");
            sb.append(plain(p[1])).append(' ').append(plain(p[0]));
        }
        return latLngPoints.size() == 1 ? "POINT(" + sb + ")" : "LINESTRING(" + sb + ")";
    }

    /** 과학적 표기(1.0E-4)가 WKT 에 섞이지 않도록 일반 소수 표기로 쓴다. */
    private static String plain(double v) {
        return new java.math.BigDecimal(v).setScale(8, java.math.RoundingMode.HALF_UP).toPlainString();
    }
}
