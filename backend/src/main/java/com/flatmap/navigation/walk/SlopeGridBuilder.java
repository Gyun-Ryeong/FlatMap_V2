package com.flatmap.navigation.walk;

/**
 * DEM 에서 경사 격자를 만든다(순수 계산).
 *
 * <p>경사는 3×3 이웃으로 구하는 Horn 방식(GDAL {@code gdaldem slope} 와 같은 식)이다:
 * {@code dz/dx = ((c + 2f + i) - (a + 2d + g)) / (8 dx)}, {@code dz/dy = ((g + 2h + i) - (a + 2b + c)) / (8 dy)},
 * 경사 = atan(√(dz/dx² + dz/dy²)) 를 각도(°)로. 픽셀 한 변의 미터 길이는 위도에 따른 WGS84 도→미터 환산을 쓴다.
 * 이웃 중 값이 없거나(NaN) 래스터 밖인 셀은 만들지 않는다.
 *
 * <p>이 값은 약 30m 격자에서 이웃 평균으로 구한 것이라 <b>실제 경사보다 낮게 나오는 경향</b>이 있다({@link WalkModel#LIMITATION}).
 */
public final class SlopeGridBuilder {

    private SlopeGridBuilder() {}

    /** 격자의 각 셀 중심이 있는 DEM 픽셀에서 경사를 계산한다. 격자와 DEM 이 정렬돼 있어야 한다. */
    public static SlopeCells build(DemRaster dem, WalkGrid grid) {
        checkAligned(dem, grid);
        SlopeCells cells = new SlopeCells(grid.cols() * grid.rows());
        for (int j = 0; j < grid.rows(); j++) {
            double lat = grid.centerLat(j);
            int row = dem.rowOf(lat);
            if (row < 1 || row >= dem.height() - 1) continue;
            double dy = dem.pixelLat() * metersPerDegreeLat(lat);
            double dx = dem.pixelLng() * metersPerDegreeLng(lat);
            for (int i = 0; i < grid.cols(); i++) {
                int col = dem.colOf(grid.centerLng(i));
                if (col < 1 || col >= dem.width() - 1) continue;
                double slope = horn(dem, col, row, dx, dy);
                if (Double.isNaN(slope)) continue;
                cells.add(i, j, slope, dem.value(col, row));
            }
        }
        return cells;
    }

    /** (col,row) 중심 3×3 의 Horn 경사(°). 이웃에 NaN 이 있으면 NaN. */
    static double horn(DemRaster dem, int col, int row, double dx, double dy) {
        float a = dem.value(col - 1, row - 1), b = dem.value(col, row - 1), c = dem.value(col + 1, row - 1);
        float d = dem.value(col - 1, row), e = dem.value(col, row), f = dem.value(col + 1, row);
        float g = dem.value(col - 1, row + 1), h = dem.value(col, row + 1), i = dem.value(col + 1, row + 1);
        if (Float.isNaN(a + b + c + d + e + f + g + h + i)) return Double.NaN;
        double dzdx = ((c + 2.0 * f + i) - (a + 2.0 * d + g)) / (8.0 * dx);
        double dzdy = ((g + 2.0 * h + i) - (a + 2.0 * b + c)) / (8.0 * dy);   // 행이 남쪽으로 커지므로 부호만 다르다(크기는 같다)
        return Math.toDegrees(Math.atan(Math.hypot(dzdx, dzdy)));
    }

    /** 격자 셀 한 변 = DEM 픽셀 한 변이고 셀 중심이 픽셀 중심과 맞아야 한다. 아니면 셀과 픽셀이 어긋나 값이 틀어진다. */
    static void checkAligned(DemRaster dem, WalkGrid grid) {
        double cell = grid.cellDegrees();
        if (Math.abs(dem.pixelLng() - cell) > cell * 1e-6 || Math.abs(dem.pixelLat() - cell) > cell * 1e-6) {
            throw new DemException("DEM 해상도가 격자 해상도와 다르다");
        }
        // 픽셀 (0,0) 의 중심 = 모서리 + 반 픽셀. 격자의 첫 중심과의 거리가 픽셀 정수 개여야 한다.
        double offLng = (grid.minLng() - (dem.originLng() + dem.pixelLng() / 2)) / cell;
        double offLat = ((dem.originLat() - dem.pixelLat() / 2) - grid.minLat()) / cell;
        if (Math.abs(offLng - Math.rint(offLng)) > 1e-4 || Math.abs(offLat - Math.rint(offLat)) > 1e-4) {
            throw new DemException("DEM 픽셀 중심과 격자 중심이 맞지 않는다");
        }
    }

    /** 위도 φ 에서 위도 1° 의 미터 길이(WGS84 근사식). */
    static double metersPerDegreeLat(double latDeg) {
        double p = Math.toRadians(latDeg);
        return 111132.92 - 559.82 * Math.cos(2 * p) + 1.175 * Math.cos(4 * p) - 0.0023 * Math.cos(6 * p);
    }

    /** 위도 φ 에서 경도 1° 의 미터 길이(WGS84 근사식). */
    static double metersPerDegreeLng(double latDeg) {
        double p = Math.toRadians(latDeg);
        return 111412.84 * Math.cos(p) - 93.5 * Math.cos(3 * p) + 0.118 * Math.cos(5 * p);
    }
}
