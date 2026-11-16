package com.flatmap.navigation.walk;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

/**
 * 보행난이도(B축) 설정 바인딩. 기본값을 코드에 두지 않는다 — 설정이 없으면 기동 단계에서 실패한다.
 * 값의 의미는 application.properties 의 walk.* 주석을 본다. 임계값·가중은 검증되지 않은 설계 파라미터다.
 */
@Configuration
public class WalkConfig {

    @Bean
    WalkGrid walkGrid(@Value("${walk.grid.min-lat}") double minLat,
                      @Value("${walk.grid.max-lat}") double maxLat,
                      @Value("${walk.grid.min-lng}") double minLng,
                      @Value("${walk.grid.max-lng}") double maxLng,
                      @Value("${walk.grid.cells-per-degree}") int cellsPerDegree) {
        return new WalkGrid(minLat, maxLat, minLng, maxLng, cellsPerDegree);
    }

    @Bean
    WalkModel walkModel(@Value("${walk.sample.interval-meters}") double intervalMeters,
                        @Value("${walk.sample.max-samples}") int maxSamples,
                        @Value("${walk.min-coverage}") double minCoverage,
                        @Value("${walk.default-mode}") String defaultMode,
                        @Value("${walk.threshold.general.good-max-deg}") double generalGood,
                        @Value("${walk.threshold.general.caution-max-deg}") double generalCaution,
                        @Value("${walk.threshold.weak.good-max-deg}") double weakGood,
                        @Value("${walk.threshold.weak.caution-max-deg}") double weakCaution,
                        @Value("${walk.score.weight.good}") double weightGood,
                        @Value("${walk.score.weight.caution}") double weightCaution,
                        @Value("${walk.score.weight.danger}") double weightDanger) {
        Map<WalkMode, WalkModel.Thresholds> thresholds = new EnumMap<>(WalkMode.class);
        thresholds.put(WalkMode.GENERAL, new WalkModel.Thresholds(generalGood, generalCaution));
        thresholds.put(WalkMode.WEAK, new WalkModel.Thresholds(weakGood, weakCaution));
        return new WalkModel(intervalMeters, maxSamples, minCoverage, WalkMode.parse(defaultMode, null),
                thresholds, weightGood, weightCaution, weightDanger);
    }

    /** DEM 원본 위치. 요청으로 받지 않고 설정(환경변수 WALK_DEM_PATH)에서만 읽는다. */
    @Bean
    DemSource demSource(@Value("${walk.dem.path}") String path) {
        Path file = Path.of(path);
        return new DemSource() {
            @Override
            public boolean exists() {
                return java.nio.file.Files.isRegularFile(file);
            }

            @Override
            public DemRaster load() {
                return GeoTiffDemReader.read(file);
            }
        };
    }
}
