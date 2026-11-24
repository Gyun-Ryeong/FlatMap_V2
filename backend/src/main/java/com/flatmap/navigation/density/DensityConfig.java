package com.flatmap.navigation.density;

import com.flatmap.navigation.routescore.FacilityWeights;
import com.flatmap.navigation.routescore.PiecewiseScale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

/** 시설 밀도 격자 설정 바인딩(score.grid.*). 기본값을 코드에 두지 않는다. */
@Configuration
public class DensityConfig {

    @Bean
    GridSpec gridSpec(@Value("${score.grid.min-lat}") BigDecimal minLat,
                      @Value("${score.grid.max-lat}") BigDecimal maxLat,
                      @Value("${score.grid.min-lng}") BigDecimal minLng,
                      @Value("${score.grid.max-lng}") BigDecimal maxLng,
                      @Value("${score.grid.cell-lat}") BigDecimal cellLat,
                      @Value("${score.grid.cell-lng}") BigDecimal cellLng) {
        return GridSpec.covering(minLat, maxLat, minLng, maxLng, cellLat, cellLng);
    }

    @Bean
    DensityScorer densityScorer(FacilityWeights weights,
                                @Value("${score.grid.score-breakpoints}") String breakpoints,
                                @Value("${score.grid.low-density-max-score}") double lowDensityMaxScore,
                                @Value("${score.grid.medium-max-score}") double mediumMaxScore) {
        return new DensityScorer(weights, PiecewiseScale.parse(breakpoints), lowDensityMaxScore, mediumMaxScore);
    }
}
