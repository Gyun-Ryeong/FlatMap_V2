package com.flatmap.navigation.density;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 격자 정의. 셀은 정수 인덱스 {@code (i, j)} 로 식별한다 — i 는 경도(동쪽) 방향, j 는 위도(북쪽) 방향이고
 * 원점은 (minLng, minLat) 이다. 부동소수 누적 대신 {@code min + i * cell} 을 BigDecimal 로 계산해
 * 같은 설정이면 언제나 같은 경계가 나온다.
 *
 * <p>셀은 반개구간 {@code [하한, 상한)} 이다: 경계 위의 점은 한 셀에만 속한다.
 */
public record GridSpec(BigDecimal minLat, BigDecimal minLng, BigDecimal cellLat, BigDecimal cellLng,
                       int cols, int rows) {

    public GridSpec {
        if (cellLat.signum() <= 0 || cellLng.signum() <= 0) {
            throw new IllegalArgumentException("격자 크기는 0 보다 커야 한다.");
        }
        if (cols <= 0 || rows <= 0) {
            throw new IllegalArgumentException("격자 범위가 비어 있다.");
        }
    }

    /** 범위를 덮는 최소 셀 수로 격자를 만든다(끝 셀은 max 를 조금 넘을 수 있다). */
    public static GridSpec covering(BigDecimal minLat, BigDecimal maxLat, BigDecimal minLng, BigDecimal maxLng,
                                    BigDecimal cellLat, BigDecimal cellLng) {
        if (maxLat.compareTo(minLat) <= 0 || maxLng.compareTo(minLng) <= 0) {
            throw new IllegalArgumentException("격자 범위가 올바르지 않다.");
        }
        int cols = maxLng.subtract(minLng).divide(cellLng, 0, RoundingMode.CEILING).intValueExact();
        int rows = maxLat.subtract(minLat).divide(cellLat, 0, RoundingMode.CEILING).intValueExact();
        return new GridSpec(minLat, minLng, cellLat, cellLng, cols, rows);
    }

    /** 셀 경계 {minLng, minLat, maxLng, maxLat}. */
    public double[] cellBounds(int i, int j) {
        BigDecimal lng0 = minLng.add(cellLng.multiply(BigDecimal.valueOf(i)));
        BigDecimal lat0 = minLat.add(cellLat.multiply(BigDecimal.valueOf(j)));
        return new double[]{lng0.doubleValue(), lat0.doubleValue(),
                lng0.add(cellLng).doubleValue(), lat0.add(cellLat).doubleValue()};
    }

    /** 경도가 속한 열 인덱스. 격자 밖이면 범위 밖 값(음수 또는 cols 이상)이 나온다. */
    public int columnOf(double lng) {
        return BigDecimal.valueOf(lng).subtract(minLng).divide(cellLng, 0, RoundingMode.FLOOR).intValue();
    }

    /** 위도가 속한 행 인덱스. 격자 밖이면 범위 밖 값이 나온다. */
    public int rowOf(double lat) {
        return BigDecimal.valueOf(lat).subtract(minLat).divide(cellLat, 0, RoundingMode.FLOOR).intValue();
    }

    public boolean contains(int i, int j) {
        return i >= 0 && i < cols && j >= 0 && j < rows;
    }
}
