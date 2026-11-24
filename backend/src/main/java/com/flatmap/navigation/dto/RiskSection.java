package com.flatmap.navigation.dto;

/**
 * 경로 반경 이내의 급경사지(붕괴위험 지정지) 한 건 — "붕괴위험 경고 레이어"의 경로 구간 항목.
 *
 * <p>보행 위험등급(riskLevel)은 없다. 붕괴위험 경고와 보행난이도(DEM 기반 경로 경사)는 별개의 축이며,
 * 이 항목에는 보행난이도 임계값(3.18°/4.76°)을 적용하지 않는다.
 *
 * <ul>
 *   <li>{@code grade}: 경사도, 단위 각도(°). 값이 측정되지 않았으면 {@code null}(0°가 아니다).</li>
 *   <li>{@code source}: 출처(예: GYEONGGI, DISASTER, PUBLIC_DATA, MOIS_CSV).</li>
 *   <li>{@code distanceFromRoute}: 경로 정점까지의 최소 거리(미터, 소수 첫째 자리).</li>
 *   <li>{@code nearestRouteIdx}: 가장 가까운 경로 정점의 인덱스(0 기준).</li>
 * </ul>
 */
public class RiskSection {
    private final double lat;
    private final double lng;
    private final String name;
    private final Double grade;
    private final String source;
    private final double distanceFromRoute;
    private final int nearestRouteIdx;

    public RiskSection(double lat, double lng, String name, Double grade,
                       String source, double distanceFromRoute, int nearestRouteIdx) {
        this.lat = lat;
        this.lng = lng;
        this.name = name;
        this.grade = grade;
        this.source = source;
        this.distanceFromRoute = Math.round(distanceFromRoute * 10.0) / 10.0;
        this.nearestRouteIdx = nearestRouteIdx;
    }

    public double getLat() { return lat; }
    public double getLng() { return lng; }
    public String getName() { return name; }
    public Double getGrade() { return grade; }
    public String getSource() { return source; }
    public double getDistanceFromRoute() { return distanceFromRoute; }
    public int getNearestRouteIdx() { return nearestRouteIdx; }
}
