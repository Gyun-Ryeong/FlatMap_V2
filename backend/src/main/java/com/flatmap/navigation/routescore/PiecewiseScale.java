package com.flatmap.navigation.routescore;

import java.util.ArrayList;
import java.util.List;

/**
 * 구간별 선형 함수(점수 변환). 점수 변환 규칙은 이 클래스 한 곳에만 둔다 —
 * 경로 A축 점수와 격자 밀도 점수가 같은 구현을 쓰고, 경계값은 설정(application.properties)에서 온다.
 *
 * <p>꺾은점 {@code (x, y)} 를 x 오름차순으로 받아 사이는 선형 보간하고, 양 끝 밖은 끝 값으로 고정한다.
 * y 는 0~100 이어야 한다.
 */
public final class PiecewiseScale {

    private final double[] xs;
    private final double[] ys;

    public PiecewiseScale(double[] xs, double[] ys) {
        if (xs.length != ys.length || xs.length < 2) {
            throw new IllegalArgumentException("꺾은점은 2개 이상이어야 한다.");
        }
        for (int i = 0; i < xs.length; i++) {
            if (!Double.isFinite(xs[i]) || !Double.isFinite(ys[i])) {
                throw new IllegalArgumentException("꺾은점에 유한하지 않은 값이 있다.");
            }
            if (ys[i] < 0 || ys[i] > 100) {
                throw new IllegalArgumentException("점수는 0~100 이어야 한다: " + ys[i]);
            }
            if (i > 0 && xs[i] <= xs[i - 1]) {
                throw new IllegalArgumentException("x 는 엄격히 오름차순이어야 한다: " + xs[i]);
            }
        }
        this.xs = xs.clone();
        this.ys = ys.clone();
    }

    /** "0:0,2:20,5:40" 형식. 형식이 틀리면 IllegalArgumentException (기동 시 바로 실패한다). */
    public static PiecewiseScale parse(String spec) {
        if (spec == null || spec.isBlank()) {
            throw new IllegalArgumentException("구간 함수 설정이 비어 있다.");
        }
        List<double[]> points = new ArrayList<>();
        for (String pair : spec.split(",")) {
            String[] xy = pair.trim().split(":");
            if (xy.length != 2) {
                throw new IllegalArgumentException("구간 함수 형식은 x:y,x:y 이어야 한다.");
            }
            try {
                points.add(new double[]{Double.parseDouble(xy[0].trim()), Double.parseDouble(xy[1].trim())});
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("구간 함수에 숫자가 아닌 값이 있다.");
            }
        }
        double[] xs = new double[points.size()];
        double[] ys = new double[points.size()];
        for (int i = 0; i < xs.length; i++) {
            xs[i] = points.get(i)[0];
            ys[i] = points.get(i)[1];
        }
        return new PiecewiseScale(xs, ys);
    }

    public double apply(double x) {
        if (Double.isNaN(x) || x <= xs[0]) return ys[0];
        int last = xs.length - 1;
        if (x >= xs[last]) return ys[last];
        for (int i = 1; i <= last; i++) {
            if (x <= xs[i]) {
                double t = (x - xs[i - 1]) / (xs[i] - xs[i - 1]);
                return ys[i - 1] + t * (ys[i] - ys[i - 1]);
            }
        }
        return ys[last];
    }
}
