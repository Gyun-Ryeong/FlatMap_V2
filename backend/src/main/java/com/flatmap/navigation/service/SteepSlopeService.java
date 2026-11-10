package com.flatmap.navigation.service;

import com.flatmap.navigation.dto.RiskSection;
import com.flatmap.navigation.dto.RouteResponse;
import com.flatmap.navigation.model.SteepSlopeArea;
import com.flatmap.navigation.repository.SteepSlopeAreaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
     * 도보 경로 좌표 목록과 DB의 급경사지 데이터를 비교하여 위험구간을 반환한다.
     * 반경 판정은 DB 의 ST_DWithin(geography, 미터)로 한 번의 쿼리에 끝낸다.
     * 급경사지가 없거나 경로가 비어 있으면 빈 리스트를 반환한다.
     */
    public List<RiskSection> findRiskSections(List<RouteResponse.Coord> routeCoords) {
        if (routeCoords == null || routeCoords.isEmpty()) return List.of();

        List<double[]> points = new ArrayList<>(routeCoords.size());
        for (RouteResponse.Coord c : routeCoords) {
            points.add(new double[]{c.getLat(), c.getLng()});
        }

        List<SteepSlopeAreaRepository.RouteHit> hits = repository.findAlongRoute(points, RISK_RADIUS_METERS);

        List<RiskSection> riskSections = new ArrayList<>(hits.size());
        for (SteepSlopeAreaRepository.RouteHit hit : hits) {
            SteepSlopeArea area = hit.area();
            // 붕괴위험 지정지에는 보행 위험등급이 없다. 경사도(°)는 측정되지 않았으면 null 로 둔다(0° 아님).
            riskSections.add(new RiskSection(
                    area.getLatitude(),
                    area.getLongitude(),
                    area.getName(),
                    area.getGrade(),
                    area.getSource(),
                    hit.distanceMeters(),
                    hit.nearestIdx()
            ));
            log.info("★ 붕괴위험 지정지 감지: {} (경사도 {}°, 경로에서 {}m, 좌표인덱스 {})",
                    area.getName(), area.getGrade(), Math.round(hit.distanceMeters()), hit.nearestIdx());
        }

        log.info("위험구간 판별 완료: 경로 좌표 {}개, {}건 감지 (반경 {}m)",
                routeCoords.size(), riskSections.size(), RISK_RADIUS_METERS);
        return riskSections;
    }

    /**
     * 점들({@code [lat, lng]}) 중 급경사지 판정 반경(RISK_RADIUS_METERS) 안에 드는 점의 인덱스(0 기준)를 반환한다.
     * 우회 경유지 후보가 다른 급경사지에 걸리는지 검증할 때 쓴다.
     */
    public Set<Integer> pointsWithinRiskRadius(List<double[]> points) {
        return repository.findPointIndexesWithAreaWithin(points, RISK_RADIUS_METERS);
    }
}
