package com.flatmap.navigation.density;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 저장·조회되는 격자 셀 한 개. bounds 는 지도에 바로 그릴 수 있는 닫힌 사각형 {@code [위도, 경도] x 5}.
 * densityScore 는 설계 파라미터에서 나온 0~100 값이며 위험도가 아니다.
 */
public record DensityCell(
        int gridI,
        int gridJ,
        double minLat,
        double minLng,
        double maxLat,
        double maxLng,
        int facilityCount,
        int cctvCount,
        int lightCount,
        int policeCount,
        BigDecimal densityScore,
        DensityLevel densityLevel,
        int scoreVersion,
        LocalDateTime calculatedAt,
        List<double[]> bounds) {

    public static DensityCell of(int gridI, int gridJ, double minLat, double minLng, double maxLat, double maxLng,
                                 int cctv, int light, int police, BigDecimal score, DensityLevel level,
                                 int scoreVersion, LocalDateTime calculatedAt) {
        List<double[]> bounds = List.of(
                new double[]{minLat, minLng},
                new double[]{minLat, maxLng},
                new double[]{maxLat, maxLng},
                new double[]{maxLat, minLng},
                new double[]{minLat, minLng});
        return new DensityCell(gridI, gridJ, minLat, minLng, maxLat, maxLng,
                cctv + light + police, cctv, light, police, score, level, scoreVersion, calculatedAt, bounds);
    }
}
