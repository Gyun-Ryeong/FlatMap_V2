package com.flatmap.navigation.walk;

/**
 * 고도 래스터(단일 밴드, 미터). 픽셀 (0,0) 은 왼쪽 위(북서) 모서리가 ({@code originLng}, {@code originLat}) 이고
 * 열은 동쪽으로, 행은 남쪽으로 커진다.
 *
 * @param pixelLng 픽셀 가로 크기(경도 °, 양수)
 * @param pixelLat 픽셀 세로 크기(위도 °, 양수)
 * @param values   행 우선 {@code values[row * width + col]}. 값이 없는 곳은 NaN.
 */
public record DemRaster(int width, int height, double originLng, double originLat,
                        double pixelLng, double pixelLat, float[] values) {

    public DemRaster {
        if (width <= 0 || height <= 0 || values.length != (long) width * height) {
            throw new IllegalArgumentException("래스터 크기와 값 개수가 맞지 않는다.");
        }
        if (!(pixelLng > 0) || !(pixelLat > 0)) {
            throw new IllegalArgumentException("픽셀 크기는 0 보다 커야 한다.");
        }
    }

    public float value(int col, int row) {
        return values[row * width + col];
    }

    /** 경도가 속한 열(래스터 밖이면 범위를 벗어난 값). */
    public int colOf(double lng) {
        return (int) Math.floor((lng - originLng) / pixelLng);
    }

    /** 위도가 속한 행(래스터 밖이면 범위를 벗어난 값). 위도가 클수록 행이 작다. */
    public int rowOf(double lat) {
        return (int) Math.floor((originLat - lat) / pixelLat);
    }

    public boolean contains(int col, int row) {
        return col >= 0 && col < width && row >= 0 && row < height;
    }
}
