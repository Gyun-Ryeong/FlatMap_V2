package com.flatmap.navigation.walk;

import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 경사 샘플 → 구간 비율(양호/주의/위험)과 B축 점수. DB 에 접근하지 않는 순수 계산이다.
 *
 * <p>점수 = 100 × (양호 비율 × w양호 + 주의 비율 × w주의 + 위험 비율 × w위험). 가중은 설정값(walk.score.weight.*)이고
 * 양호 ≥ 주의 ≥ 위험 이므로 경사가 클수록 점수는 같거나 낮아진다(단조).
 * 격자에 값이 있는 샘플이 {@code walk.min-coverage} 미만이면 평가하지 않는다(빈 결과) — 데이터가 없는 구간을
 * "양호"나 "위험"으로 채우지 않기 위해서다.
 */
@Component
public class WalkAssessor {

    private final WalkModel model;

    public WalkAssessor(WalkModel model) {
        this.model = model;
    }

    public Optional<WalkAssessment> assess(SlopeProfile profile, WalkMode mode) {
        int total = profile.sampleCount();
        if (total == 0) return Optional.empty();

        int[] counts = new int[SlopeClass.values().length];
        int data = 0;
        double max = 0, sum = 0;
        for (double s : profile.slopes()) {
            if (Double.isNaN(s)) continue;
            data++;
            counts[model.classify(mode, s).ordinal()]++;
            max = Math.max(max, s);
            sum += s;
        }
        if (data == 0) return Optional.empty();
        double coverage = (double) data / total;
        if (coverage < model.minCoverage()) return Optional.empty();

        double good = (double) counts[SlopeClass.GOOD.ordinal()] / data;
        double caution = (double) counts[SlopeClass.CAUTION.ordinal()] / data;
        double danger = (double) counts[SlopeClass.DANGER.ordinal()] / data;
        double score = 100.0 * (good * model.weightGood() + caution * model.weightCaution()
                + danger * model.weightDanger());
        double[] pct = percentsSummingTo100(counts, data);

        return Optional.of(new WalkAssessment(mode, round1(score), pct[0], pct[1], pct[2],
                total, data, coverage, profile.lengthMeters(), max, sum / data,
                model.thresholds(mode), total > 1 ? profile.lengthMeters() / (total - 1) : 0));
    }

    /**
     * 개수 → 소수 첫째 자리 백분율. 반올림 때문에 합이 100.0 이 아니게 되지 않도록
     * 가장 큰 나머지 순서로 0.1 단위를 나눠 준다(최대 잉여 방식).
     */
    static double[] percentsSummingTo100(int[] counts, int total) {
        int n = counts.length;
        int[] tenths = new int[n];
        double[] remainder = new double[n];
        int assigned = 0;
        for (int k = 0; k < n; k++) {
            double exact = 1000.0 * counts[k] / total;
            tenths[k] = (int) Math.floor(exact + 1e-9);
            remainder[k] = exact - tenths[k];
            assigned += tenths[k];
        }
        for (int left = 1000 - assigned; left > 0; left--) {
            int best = 0;
            for (int k = 1; k < n; k++) if (remainder[k] > remainder[best]) best = k;
            tenths[best]++;
            remainder[best] = -1;
        }
        double[] out = new double[n];
        for (int k = 0; k < n; k++) out[k] = tenths[k] / 10.0;
        return out;
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
