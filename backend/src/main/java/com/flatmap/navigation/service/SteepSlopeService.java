package com.flatmap.navigation.service;

import com.flatmap.navigation.dto.RiskSection;
import com.flatmap.navigation.dto.RouteResponse;
import com.flatmap.navigation.dto.WeatherInfo;
import com.flatmap.navigation.entity.SteepSlopeArea;
import com.flatmap.navigation.repository.SteepSlopeAreaRepository;
import com.flatmap.navigation.util.GeoUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class SteepSlopeService {

    private static final Logger log = LoggerFactory.getLogger(SteepSlopeService.class);

    // 경로 좌표에서 이 반경(미터) 이내의 급경사지를 위험구간으로 판별
    // 우회 경유지 안전성 검증(DetourService)에서도 동일 기준을 사용하므로 공개한다
    public static final double RISK_RADIUS_METERS = 200.0;

    private final SteepSlopeAreaRepository repository;

    public SteepSlopeService(SteepSlopeAreaRepository repository) {
        this.repository = repository;
    }

    /**
     * 좌표가 확보된 급경사지 전체를 반환한다. (우회 경유지 안전성 검증용)
     */
    public List<SteepSlopeArea> getAllAreasWithCoords() {
        List<SteepSlopeArea> result = new ArrayList<>();
        for (SteepSlopeArea area : repository.findAll()) {
            if (area.getLatitude() != null && area.getLongitude() != null) {
                result.add(area);
            }
        }
        return result;
    }

    /**
     * 도보 경로 좌표 목록과 DB의 급경사지 데이터를 비교하여 위험구간을 반환한다.
     * DB에 데이터가 없으면 빈 리스트를 반환한다.
     */
    public List<RiskSection> findRiskSections(List<RouteResponse.Coord> routeCoords) {
        List<SteepSlopeArea> allAreas = repository.findAll();

        if (allAreas.isEmpty()) {
            log.info("급경사지 데이터가 없음 — 위험구간 판별 건너뜀");
            return Collections.emptyList();
        }

        // 경로 좌표 범위 로깅 (디버깅용)
        double minLat = Double.MAX_VALUE, maxLat = -Double.MAX_VALUE;
        double minLng = Double.MAX_VALUE, maxLng = -Double.MAX_VALUE;
        for (RouteResponse.Coord c : routeCoords) {
            minLat = Math.min(minLat, c.getLat()); maxLat = Math.max(maxLat, c.getLat());
            minLng = Math.min(minLng, c.getLng()); maxLng = Math.max(maxLng, c.getLng());
        }
        log.info("급경사지 {}건 vs 경로 좌표 {}개 비교 시작 (경로 범위: lat {}-{}, lng {}-{})",
                allAreas.size(), routeCoords.size(),
                String.format("%.4f", minLat), String.format("%.4f", maxLat),
                String.format("%.4f", minLng), String.format("%.4f", maxLng));

        // 1단계: 각 급경사지별 경로 최소거리 계산
        List<RiskSection> riskSections = new ArrayList<>();
        double globalMinDist = Double.MAX_VALUE;
        String globalMinName = "";

        for (SteepSlopeArea area : allAreas) {
            if (area.getLatitude() == null || area.getLongitude() == null) continue;

            double minDist = Double.MAX_VALUE;
            int minIdx = -1;

            for (int i = 0; i < routeCoords.size(); i++) {
                RouteResponse.Coord coord = routeCoords.get(i);
                double distance = GeoUtils.haversineMeters(
                        coord.getLat(), coord.getLng(),
                        area.getLatitude(), area.getLongitude()
                );
                if (distance < minDist) {
                    minDist = distance;
                    minIdx = i;
                }
            }

            // 전체 DB 중 경로와 가장 가까운 데이터 추적
            if (minDist < globalMinDist) {
                globalMinDist = minDist;
                globalMinName = area.getName();
            }

            // 디버깅 로그 (500m 이내)
            if (minDist < 500) {
                String status = minDist <= RISK_RADIUS_METERS ? "MATCHED" : "MISS";
                log.info("[거리분석] {} (lat={}, lng={}) : 최소 {}m (좌표[{}]) → {}",
                        area.getName(), area.getLatitude(), area.getLongitude(),
                        Math.round(minDist), minIdx, status);
            }

            // 2단계: 반경 이내이면 위험구간으로 추가
            if (minDist <= RISK_RADIUS_METERS) {
                riskSections.add(new RiskSection(
                        area.getLatitude(),
                        area.getLongitude(),
                        area.getName(),
                        area.getGrade() != null ? area.getGrade() : 0.0,
                        // 붕괴위험 지정지에는 보행 위험등급이 없다(null). 보행난이도는 경로 DEM 경사로 따로 계산한다.
                        null,
                        minDist,
                        minIdx
                ));
                log.info("★ 붕괴위험 지정지 감지: {} (경사도 {}°, 경로에서 {}m, 좌표인덱스 {})",
                        area.getName(), area.getGrade(), Math.round(minDist), minIdx);
            }
        }

        // 디버깅: DB 전체에서 경로와 가장 가까운 데이터 로그
        log.info("[디버깅] DB 전체 중 경로와 가장 가까운 데이터: '{}' = {}m",
                globalMinName, Math.round(globalMinDist));

        // nearestRouteIdx 기준으로 경로 순서대로 정렬
        riskSections.sort(Comparator.comparingInt(RiskSection::getNearestRouteIdx));

        log.info("위험구간 판별 완료: {}건 감지 (반경 {}m)", riskSections.size(), RISK_RADIUS_METERS);
        return riskSections;
    }

    /**
     * 날씨 조건에 따라 위험구간의 riskLevel을 보정한다.
     * 기존 경사도 기반 등급에 날씨 위험 계수를 곱하여 상향 조정.
     */
    public void adjustRiskByWeather(List<RiskSection> riskSections, WeatherInfo weather) {
        if (weather == null || riskSections.isEmpty()) return;

        double multiplier = weather.getRiskMultiplier();
        if (multiplier <= 1.0) {
            log.info("날씨 보정 불필요 (multiplier={})", multiplier);
            return;
        }

        log.info("날씨 보정 적용: multiplier={} (PTY={}, 기온={}℃, 풍속={}m/s)",
                multiplier, weather.getPrecipitationType(),
                weather.getTemperature(), weather.getWindSpeed());

        for (RiskSection rs : riskSections) {
            String original = rs.getRiskLevel();
            if (original == null) continue; // 등급이 없는 구간(붕괴위험 지정지)은 보정 대상이 아니다
            String adjusted = upgradeRiskLevel(original, multiplier);
            if (!original.equals(adjusted)) {
                rs.setRiskLevel(adjusted);
                log.info("  {} : {} → {} (날씨 보정)", rs.getName(), original, adjusted);
            }
        }
    }

    private String upgradeRiskLevel(String current, double multiplier) {
        // 경사도에 multiplier를 적용한 것처럼 등급 상향
        // 법정 기준(SlopeRisk)의 정적 분류는 LOW/MEDIUM/VERY_HIGH 3단계뿐이므로,
        // 날씨 보정도 같은 3단계 내에서만 상향한다. ("HIGH"는 정적 분류에 존재하지 않는 값)
        String[] levels = {"LOW", "MEDIUM", "VERY_HIGH"};
        int idx = 0;
        for (int i = 0; i < levels.length; i++) {
            if (levels[i].equals(current)) { idx = i; break; }
        }

        // multiplier 1.4+ → 1단계 상향, 1.6+ → 2단계 상향
        int boost = 0;
        if (multiplier >= 1.6) boost = 2;
        else if (multiplier >= 1.4) boost = 1;
        else if (multiplier >= 1.2) boost = 1;

        int newIdx = Math.min(idx + boost, levels.length - 1);
        return levels[newIdx];
    }
}
