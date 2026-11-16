package com.flatmap.navigation.routescore;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 점수 모델 설정 바인딩. 기본값을 코드에 두지 않는다 — 설정이 없으면 기동 단계에서 실패한다.
 * 값의 의미는 application.properties 의 score.* 주석을 본다.
 */
@Configuration
public class ScoreModelConfig {

    @Bean
    FacilityWeights facilityWeights(@Value("${score.weight.cctv}") double cctv,
                                    @Value("${score.weight.security-light}") double securityLight,
                                    @Value("${score.weight.police}") double police) {
        return new FacilityWeights(cctv, securityLight, police);
    }

    @Bean
    ScoreModel scoreModel(@Value("${score.version}") int version,
                          FacilityWeights weights,
                          @Value("${score.route.radius-meters}") double radiusMeters,
                          @Value("${score.route.police-radius-meters}") double policeRadiusMeters,
                          @Value("${score.route.min-length-km}") double minRouteKm,
                          @Value("${score.route.exposure-breakpoints}") String exposureBreakpoints,
                          @Value("${score.route.axis-weight.a}") double axisWeightA,
                          @Value("${score.route.axis-weight.b}") double axisWeightB,
                          @Value("${score.route.axis-weight.c}") double axisWeightC) {
        return new ScoreModel(version, weights, radiusMeters, policeRadiusMeters, minRouteKm,
                PiecewiseScale.parse(exposureBreakpoints), axisWeightA, axisWeightB, axisWeightC);
    }

    @Bean
    WeatherCorrection weatherCorrection(@Value("${weather.correction.snow}") double snow,
                                        @Value("${weather.correction.sleet}") double sleet,
                                        @Value("${weather.correction.rain}") double rain,
                                        @Value("${weather.correction.freezing}") double freezing,
                                        @Value("${weather.correction.strong-wind}") double strongWind,
                                        @Value("${weather.correction.freezing-temp-celsius}") double freezingTemp,
                                        @Value("${weather.correction.strong-wind-mps}") double strongWindMps) {
        return new WeatherCorrection(snow, sleet, rain, freezing, strongWind, freezingTemp, strongWindMps);
    }
}
