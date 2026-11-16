package com.flatmap.navigation.walk;

/**
 * 경로를 따라 읽은 경사 샘플. {@code slopes[k]} 는 k 번째 샘플 위치 셀의 경사(°)이고, 격자에 값이 없으면 NaN 이다.
 *
 * @param lengthMeters 경로 길이(미터)
 */
public record SlopeProfile(double lengthMeters, double[] slopes) {

    public int sampleCount() {
        return slopes.length;
    }

    /** 값이 있는 샘플 수. */
    public int dataCount() {
        int n = 0;
        for (double s : slopes) if (!Double.isNaN(s)) n++;
        return n;
    }
}
