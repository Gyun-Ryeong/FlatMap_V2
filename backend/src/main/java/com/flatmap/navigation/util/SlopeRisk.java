package com.flatmap.navigation.util;

/**
 * 경사도(각도, °)를 보행 위험등급으로 환산한다.
 *
 * <p>등급 기준은 보행 관련 법령의 경사로 설치 기준을 따른다.
 * <ul>
 *   <li>「장애인·노인·임산부 등의 편의증진 보장에 관한 법률 시행규칙」 [별표 1]
 *       — 경사로 기울기 권장 1/18, 최대 1/12</li>
 *   <li>「보도 설치 및 관리지침」(국토교통부) — 보도 종단경사 기준</li>
 * </ul>
 *
 * <p>기울기 비율을 각도로 환산하면 다음과 같다.
 * <pre>
 *   1/18 → atan(1/18) ≈ 3.18°
 *   1/12 → atan(1/12) ≈ 4.76°
 * </pre>
 *
 * <p>이 클래스가 위험등급 기준값의 유일한 정의 지점이다.
 * 기준을 바꿀 때는 아래 두 상수만 수정하면 전체에 반영된다.
 */
public final class SlopeRisk {

    private SlopeRisk() {}

    /** 권장 기울기 1/18 = atan(1/18) ≈ 3.18° — 이 값 미만이면 안전 */
    public static final double CAUTION_THRESHOLD_DEG = Math.toDegrees(Math.atan(1.0 / 18.0));

    /** 최대 기울기 1/12 = atan(1/12) ≈ 4.76° — 이 값 이상이면 위험 */
    public static final double DANGER_THRESHOLD_DEG = Math.toDegrees(Math.atan(1.0 / 12.0));

    /**
     * 경사도(°)를 등급으로 환산한다.
     *
     * @param grade 경사도(각도). null이면 미측정으로 보고 null을 반환한다.
     * @return VERY_HIGH(위험) / MEDIUM(주의) / LOW(안전), 미측정이면 null
     */
    public static String levelOf(Double grade) {
        if (grade == null) return null;
        if (grade >= DANGER_THRESHOLD_DEG) return "VERY_HIGH";
        if (grade >= CAUTION_THRESHOLD_DEG) return "MEDIUM";
        return "LOW";
    }
}
