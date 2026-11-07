package com.flatmap.navigation.service;

import com.flatmap.navigation.dto.RiskSection;
import com.flatmap.navigation.dto.RouteResponse;
import com.flatmap.navigation.entity.SteepSlopeArea;
import com.flatmap.navigation.util.GeoUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class DetourService {

    private static final Logger log = LoggerFactory.getLogger(DetourService.class);

    private static final int MAX_WAYPOINTS = 5;

    // 경유지를 경로 법선 방향으로 밀어내는 거리. 앞쪽 값부터 시도하고,
    // 해당 지점이 다른 급경사지 반경에 걸리면 다음(더 먼) 값으로 재시도한다.
    private static final double[] OFFSET_STEPS_METERS = {250.0, 350.0, 450.0, 600.0};

    // 위험구간 군집화 반경. 이 거리 안에 모인 급경사지들은 하나의 장애물로 보고 경유지 1개만 만든다.
    private static final double CLUSTER_RADIUS_METERS = 300.0;

    // T Map이 100m 미만으로 인접한 경유지를 거부하므로, 여유를 두고 이보다 가까우면 중복으로 간주해 제외
    private static final double MIN_WAYPOINT_SPACING_METERS = 150.0;

    private final SteepSlopeService steepSlopeService;

    public DetourService(SteepSlopeService steepSlopeService) {
        this.steepSlopeService = steepSlopeService;
    }

    /**
     * 위험구간 목록과 원래 경로 좌표를 기반으로 우회 경유지를 생성한다.
     *
     * 1) 인접한 위험구간을 군집으로 묶어, 군집마다 경유지 1개만 만든다.
     * 2) 경유지는 경로 진행 방향의 법선(수직) 방향으로 밀어내되,
     *    그 지점이 다른 급경사지 반경에 들어가지 않는 거리까지 오프셋을 늘려가며 탐색한다.
     * 3) 안전한 지점을 못 찾은 군집은 경유지를 만들지 않는다.
     *
     * @return T Map passList 형식 문자열 "lng1,lat1_lng2,lat2"  (최대 5개)
     */
    public String generatePassList(List<RiskSection> riskSections, List<RouteResponse.Coord> routeCoords) {
        if (riskSections == null || riskSections.isEmpty()) return null;
        if (routeCoords == null || routeCoords.size() < 2) return null;

        List<SteepSlopeArea> allAreas = steepSlopeService.getAllAreasWithCoords();

        // 1단계: 인접 위험구간 군집화
        List<List<RiskSection>> clusters = clusterRiskSections(riskSections);
        log.info("위험구간 {}건 → {}개 군집으로 통합", riskSections.size(), clusters.size());

        // 2단계: 군집을 위험도 높은 순으로 정렬해 상위 MAX_WAYPOINTS개만 사용, 이후 경로 순서로 재정렬
        List<List<RiskSection>> topClusters = clusters.stream()
                .sorted(Comparator.comparingInt(this::clusterRiskRank))
                .limit(MAX_WAYPOINTS)
                .sorted(Comparator.comparingInt(this::clusterRouteIdx))
                .collect(java.util.stream.Collectors.toList());

        StringBuilder passList = new StringBuilder();
        List<double[]> acceptedWaypoints = new ArrayList<>();

        for (List<RiskSection> cluster : topClusters) {
            double[] centroid = centroidOf(cluster);
            int anchorIdx = nearestRouteIdx(routeCoords, centroid[0], centroid[1]);

            double[] waypoint = findSafeWaypoint(routeCoords, anchorIdx, centroid[0], centroid[1], allAreas);

            if (waypoint == null) {
                log.info("군집({}건, 중심 {},{}) — 안전한 경유지를 찾지 못해 건너뜀",
                        cluster.size(), centroid[0], centroid[1]);
                continue;
            }

            // 군집화 이후에도 경유지가 서로 붙을 수 있으므로 최종 간격을 확인한다.
            // (T Map은 100m 미만 인접 경유지를 400 에러로 거부함)
            final double[] wp = waypoint;
            boolean tooClose = acceptedWaypoints.stream().anyMatch(existing ->
                    GeoUtils.haversineMeters(existing[0], existing[1], wp[0], wp[1])
                            < MIN_WAYPOINT_SPACING_METERS);
            if (tooClose) {
                log.info("경유지 중복으로 제외: ({}, {})", waypoint[0], waypoint[1]);
                continue;
            }

            acceptedWaypoints.add(waypoint);
            if (passList.length() > 0) passList.append("_");
            // T Map passList 형식: 경도,위도 (lng,lat)
            passList.append(String.format("%.6f,%.6f", waypoint[1], waypoint[0]));
            log.info("우회 경유지 생성: 군집 {}건 → ({}, {})", cluster.size(), waypoint[0], waypoint[1]);
        }

        String result = passList.length() > 0 ? passList.toString() : null;
        log.info("생성된 passList: {}", result);
        return result;
    }

    /**
     * 인접한 위험구간을 하나의 군집으로 묶는다.
     * 군집 중심(centroid)에서 CLUSTER_RADIUS_METERS 이내인 경우에만 합류시켜,
     * 여러 지점이 사슬처럼 이어져 군집이 무한정 커지는 것을 막는다.
     */
    private List<List<RiskSection>> clusterRiskSections(List<RiskSection> sections) {
        List<List<RiskSection>> clusters = new ArrayList<>();

        for (RiskSection rs : sections) {
            List<RiskSection> target = null;

            for (List<RiskSection> cluster : clusters) {
                double[] c = centroidOf(cluster);
                if (GeoUtils.haversineMeters(c[0], c[1], rs.getLat(), rs.getLng()) <= CLUSTER_RADIUS_METERS) {
                    target = cluster;
                    break;
                }
            }

            if (target == null) {
                target = new ArrayList<>();
                clusters.add(target);
            }
            target.add(rs);
        }

        return clusters;
    }

    /** 군집 구성원 좌표의 산술 평균 → [lat, lng] */
    private double[] centroidOf(List<RiskSection> cluster) {
        double sumLat = 0, sumLng = 0;
        for (RiskSection rs : cluster) {
            sumLat += rs.getLat();
            sumLng += rs.getLng();
        }
        return new double[]{sumLat / cluster.size(), sumLng / cluster.size()};
    }

    /** 군집의 대표 위험도 순위 (구성원 중 가장 높은 위험도 기준, 낮을수록 위험) */
    private int clusterRiskRank(List<RiskSection> cluster) {
        int best = Integer.MAX_VALUE;
        for (RiskSection rs : cluster) {
            best = Math.min(best, riskRank(rs.getRiskLevel()));
        }
        return best;
    }

    private int riskRank(String riskLevel) {
        if (riskLevel == null) return 3;
        switch (riskLevel) {
            case "VERY_HIGH": return 0;
            case "HIGH": return 1;
            case "MEDIUM": return 2;
            default: return 3;
        }
    }

    /** 군집이 경로상 어디쯤인지 (구성원 nearestRouteIdx의 최솟값) */
    private int clusterRouteIdx(List<RiskSection> cluster) {
        int min = Integer.MAX_VALUE;
        for (RiskSection rs : cluster) {
            min = Math.min(min, rs.getNearestRouteIdx());
        }
        return min;
    }

    /** 지정 좌표에서 가장 가까운 경로 좌표의 인덱스 */
    private int nearestRouteIdx(List<RouteResponse.Coord> coords, double lat, double lng) {
        int bestIdx = 0;
        double bestDist = Double.MAX_VALUE;
        for (int i = 0; i < coords.size(); i++) {
            RouteResponse.Coord c = coords.get(i);
            double d = GeoUtils.haversineMeters(c.getLat(), c.getLng(), lat, lng);
            if (d < bestDist) {
                bestDist = d;
                bestIdx = i;
            }
        }
        return bestIdx;
    }

    /**
     * 오프셋을 단계적으로 늘려가며, 어떤 급경사지의 판정 반경에도 들지 않는 경유지를 탐색한다.
     * 각 오프셋마다 법선의 양쪽(시계/반시계) 후보를 모두 검사하고,
     * 안전한 후보가 둘이면 회피 대상에서 더 멀어지는 쪽을 채택한다.
     *
     * @return [lat, lng] 또는 안전한 지점을 찾지 못하면 null
     */
    private double[] findSafeWaypoint(List<RouteResponse.Coord> coords, int idx,
                                      double hazardLat, double hazardLng,
                                      List<SteepSlopeArea> allAreas) {
        for (double offset : OFFSET_STEPS_METERS) {
            double[][] candidates = calculateCandidates(coords, idx, offset);
            if (candidates == null) return null;

            double[] best = null;
            double bestHazardDist = -1;

            for (double[] cand : candidates) {
                double clearance = minDistanceToArea(cand[0], cand[1], allAreas);
                if (clearance < SteepSlopeService.RISK_RADIUS_METERS) continue;

                double hazardDist = GeoUtils.haversineMeters(cand[0], cand[1], hazardLat, hazardLng);
                if (hazardDist > bestHazardDist) {
                    bestHazardDist = hazardDist;
                    best = cand;
                }
            }

            if (best != null) {
                log.debug("경유지 확정: offset={}m, 좌표=({},{}), 회피대상까지 {}m",
                        offset, best[0], best[1], Math.round(bestHazardDist));
                return best;
            }

            log.debug("offset={}m — 양쪽 후보 모두 급경사지 반경에 걸림, 다음 단계로 확대", offset);
        }

        return null;
    }

    /**
     * 경로의 특정 인덱스 지점에서 진행 방향에 수직인 양쪽 방향으로 offsetMeters만큼 이동한
     * 두 후보 좌표를 반환한다.
     *
     * @return [[lat, lng](시계방향), [lat, lng](반시계방향)] 또는 방향 계산 불가 시 null
     */
    private double[][] calculateCandidates(List<RouteResponse.Coord> coords, int idx, double offsetMeters) {
        if (coords == null || coords.size() < 2) return null;

        // 전후 좌표로 진행 방향 벡터 계산
        int prevIdx = Math.max(0, idx - 5);
        int nextIdx = Math.min(coords.size() - 1, idx + 5);

        // prevIdx == nextIdx인 경우 방지
        if (prevIdx == nextIdx) {
            if (nextIdx < coords.size() - 1) nextIdx++;
            else prevIdx = Math.max(0, prevIdx - 1);
        }

        RouteResponse.Coord prev = coords.get(prevIdx);
        RouteResponse.Coord next = coords.get(nextIdx);
        RouteResponse.Coord center = coords.get(idx);

        // 진행 방향 벡터 (lat, lng 단위)
        double dLat = next.getLat() - prev.getLat();
        double dLng = next.getLng() - prev.getLng();

        // 길이가 0이면 방향을 정할 수 없음
        double length = Math.sqrt(dLat * dLat + dLng * dLng);
        if (length < 1e-10) return null;

        // 법선 벡터 (시계 방향 90도 회전: (dLat, dLng) → (dLng, -dLat))
        double normLat = dLng / length;
        double normLng = -dLat / length;

        // 위도 1도 ≈ 111,000m, 경도 1도 ≈ 111,000m * cos(lat)
        double metersPerDegLat = 111000.0;
        double metersPerDegLng = 111000.0 * Math.cos(Math.toRadians(center.getLat()));

        double offsetLat = (offsetMeters * normLat) / metersPerDegLat;
        double offsetLng = (offsetMeters * normLng) / metersPerDegLng;

        return new double[][]{
                {center.getLat() + offsetLat, center.getLng() + offsetLng},   // 시계방향
                {center.getLat() - offsetLat, center.getLng() - offsetLng}    // 반시계방향
        };
    }

    /** 지정 좌표에서 가장 가까운 급경사지까지의 거리(m). 데이터가 없으면 Double.MAX_VALUE */
    private double minDistanceToArea(double lat, double lng, List<SteepSlopeArea> areas) {
        double min = Double.MAX_VALUE;
        for (SteepSlopeArea area : areas) {
            double d = GeoUtils.haversineMeters(lat, lng, area.getLatitude(), area.getLongitude());
            if (d < min) min = d;
        }
        return min;
    }
}
