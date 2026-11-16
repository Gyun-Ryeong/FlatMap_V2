package com.flatmap.navigation.walk;

import java.util.EnumMap;
import java.util.Map;

/**
 * 보행난이도(B축) 설정 묶음. 값은 모두 application.properties(walk.*)에서 오며 코드에 기본값을 두지 않는다.
 * 임계값·구간 점수 가중은 <b>검증되지 않은 설계 파라미터</b>다.
 *
 * <p>임계값은 이 축(DEM 기반 경로 경사) 전용이다. 급경사지(붕괴위험 지정지) 데이터에는 적용하지 않는다.
 * 경사도 단위는 각도(°)다.
 *
 * @param intervalMeters 경로를 따라 경사를 읽는 간격(미터). 격자 해상도(약 30m)와 비슷하게 둔다.
 * @param maxSamples     경로 하나에서 읽는 샘플 수 상한(아주 긴 경로 보호)
 * @param minCoverage    샘플 중 격자에 값이 있는 비율의 하한. 이보다 낮으면 B축을 점수에 넣지 않는다.
 * @param weightGood     양호 구간의 점수 가중(0~1)
 * @param weightCaution  주의 구간의 점수 가중
 * @param weightDanger   위험 구간의 점수 가중
 */
public record WalkModel(
        double intervalMeters,
        int maxSamples,
        double minCoverage,
        WalkMode defaultMode,
        Map<WalkMode, Thresholds> thresholds,
        double weightGood,
        double weightCaution,
        double weightDanger) {

    /** 한계 안내 문구. 응답(breakdown·scoreModel)과 UI 설명에 그대로 쓴다. */
    public static final String LIMITATION =
            "경사는 약 30m 격자 DEM(Copernicus GLO-30)에서 계산하며, 격자 평활화 때문에 실제 경사보다 낮게 나오는 경향이 있습니다. "
                    + "DEM 은 지표면(건물·수목 포함) 모델이라 짧은 급경사·계단·옹벽은 반영하지 못하고 건물·수목 가장자리에서는 값이 어긋날 수 있습니다. "
                    + "설계 파라미터 기반의 상대 비교 값이며 실제 보행 난이도를 보증하지 않습니다.";

    /** 모드별 임계값(각도 °): 경사 ≤ goodMax 양호, ≤ cautionMax 주의, 그보다 크면 위험. */
    public record Thresholds(double goodMaxDeg, double cautionMaxDeg) {
        public Thresholds {
            if (!(goodMaxDeg > 0) || !(cautionMaxDeg > goodMaxDeg) || !(cautionMaxDeg < 90)) {
                throw new IllegalArgumentException("임계값은 0 < 양호 상한 < 주의 상한 < 90 이어야 한다.");
            }
        }
    }

    public WalkModel {
        if (!(intervalMeters > 0)) throw new IllegalArgumentException("샘플 간격은 0 보다 커야 한다.");
        if (maxSamples < 2) throw new IllegalArgumentException("샘플 수 상한은 2 이상이어야 한다.");
        if (!(minCoverage > 0) || minCoverage > 1) throw new IllegalArgumentException("최소 커버리지는 0 초과 1 이하여야 한다.");
        if (defaultMode == null) throw new IllegalArgumentException("기본 보행 유형이 필요하다.");
        Map<WalkMode, Thresholds> copy = new EnumMap<>(WalkMode.class);
        for (WalkMode mode : WalkMode.values()) {
            Thresholds t = thresholds == null ? null : thresholds.get(mode);
            if (t == null) throw new IllegalArgumentException(mode + " 임계값이 없다.");
            copy.put(mode, t);
        }
        // 보행약자 기준이 일반보다 느슨하면 "같은 경로가 WEAK 에서 더 나쁘게 나온다"는 전제가 깨진다.
        if (copy.get(WalkMode.WEAK).goodMaxDeg() > copy.get(WalkMode.GENERAL).goodMaxDeg()
                || copy.get(WalkMode.WEAK).cautionMaxDeg() > copy.get(WalkMode.GENERAL).cautionMaxDeg()) {
            throw new IllegalArgumentException("WEAK 임계값은 GENERAL 보다 크지 않아야 한다.");
        }
        thresholds = Map.copyOf(copy);
        for (double w : new double[]{weightGood, weightCaution, weightDanger}) {
            if (!Double.isFinite(w) || w < 0 || w > 1) throw new IllegalArgumentException("구간 가중은 0~1 이어야 한다.");
        }
        // 경사가 클수록 점수가 좋아지는 설정을 막는다(단조성).
        if (!(weightGood >= weightCaution && weightCaution >= weightDanger)) {
            throw new IllegalArgumentException("구간 가중은 양호 ≥ 주의 ≥ 위험 이어야 한다.");
        }
    }

    public Thresholds thresholds(WalkMode mode) {
        return thresholds.get(mode);
    }

    /** 경사(°)를 모드의 임계값으로 분류한다. 경계는 ≤ 쪽 등급에 속한다(3.18° 는 WEAK 양호, 4.76° 는 GENERAL 양호). */
    public SlopeClass classify(WalkMode mode, double slopeDeg) {
        Thresholds t = thresholds.get(mode);
        if (slopeDeg <= t.goodMaxDeg()) return SlopeClass.GOOD;
        if (slopeDeg <= t.cautionMaxDeg()) return SlopeClass.CAUTION;
        return SlopeClass.DANGER;
    }
}
