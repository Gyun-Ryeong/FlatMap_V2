package com.flatmap.navigation.walk;

/**
 * 경사 격자의 범위와 해상도. 셀 하나는 DEM 픽셀 하나이고 한 변이 {@code 1 / cellsPerDegree} 도다.
 * Copernicus DEM 은 픽셀 <b>중심</b>이 정수 초 단위 좌표(예: 127.04°, 37.33°)에 놓이므로, 셀 (i, j) 의 중심을
 * ({@code minLng + i / cellsPerDegree}, {@code minLat + j / cellsPerDegree}) 로 정의한다. 셀은 중심 둘레 ±반 칸이다.
 * 적재 도구가 DEM 픽셀과의 정렬을 확인한다.
 *
 * <p>셀 인덱스: {@code i = floor((경도 - minLng) × cellsPerDegree + 0.5)}, {@code j = floor((위도 - minLat) × cellsPerDegree + 0.5)}
 * (가장 가까운 중심). 적재 도구와 샘플링 쿼리가 같은 값을 쓴다 — 값은 {@code walk.grid.*} 설정이다.
 * 범위의 양 끝 중심을 포함하므로 열·행 수는 (최대 - 최소) × cellsPerDegree + 1 이다.
 */
public record WalkGrid(double minLat, double maxLat, double minLng, double maxLng, int cellsPerDegree) {

    public WalkGrid {
        if (cellsPerDegree <= 0) throw new IllegalArgumentException("cells-per-degree 는 0 보다 커야 한다.");
        if (!(minLat < maxLat) || !(minLng < maxLng)) {
            throw new IllegalArgumentException("격자 범위가 올바르지 않다(최소 < 최대).");
        }
        double cols = (maxLng - minLng) * cellsPerDegree;
        double rows = (maxLat - minLat) * cellsPerDegree;
        if (Math.abs(cols - Math.rint(cols)) > 1e-6 || Math.abs(rows - Math.rint(rows)) > 1e-6) {
            throw new IllegalArgumentException("격자 범위가 셀 크기의 정수 배가 아니다.");
        }
        if (Math.rint(cols) * Math.rint(rows) > 4_000_000) {
            throw new IllegalArgumentException("격자가 너무 크다.");
        }
    }

    public int cols() {
        return (int) Math.rint((maxLng - minLng) * cellsPerDegree) + 1;
    }

    public int rows() {
        return (int) Math.rint((maxLat - minLat) * cellsPerDegree) + 1;
    }

    public double cellDegrees() {
        return 1.0 / cellsPerDegree;
    }

    /** 셀 중심 경도. */
    public double centerLng(int i) {
        return minLng + (double) i / cellsPerDegree;
    }

    /** 셀 중심 위도. */
    public double centerLat(int j) {
        return minLat + (double) j / cellsPerDegree;
    }
}
