package com.flatmap.navigation.routescore;

import com.flatmap.navigation.dto.WeatherInfo;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 날씨 보정 계수의 경계값 — 눈/비/영하/강풍 경계에서 기대한 계수가 선택되는지. */
class WeatherCorrectionTest {

    private final WeatherCorrection c = new WeatherCorrection(1.8, 1.6, 1.4, 1.5, 1.2, 0, 10);

    private static WeatherInfo w(int pty, double temp, double wind) {
        WeatherInfo w = new WeatherInfo();
        w.setPrecipitationType(pty);
        w.setTemperature(temp);
        w.setWindSpeed(wind);
        return w;
    }

    @Test
    void precipitationTypesMapToSnowSleetRain() {
        for (int snow : new int[]{3, 7}) assertThat(c.apply(w(snow, 10, 1))).isEqualTo(new WeatherCorrection.Applied("SNOW", 1.8));
        for (int sleet : new int[]{2, 6}) assertThat(c.apply(w(sleet, 10, 1))).isEqualTo(new WeatherCorrection.Applied("SLEET", 1.6));
        for (int rain : new int[]{1, 5}) assertThat(c.apply(w(rain, 10, 1))).isEqualTo(new WeatherCorrection.Applied("RAIN", 1.4));
    }

    @Test
    void freezingBoundaryIsInclusive() {
        assertThat(c.apply(w(0, 0.0, 1)).condition()).isEqualTo("FREEZING");     // 정확히 0℃ → 결빙
        assertThat(c.apply(w(0, -3.5, 1)).multiplier()).isEqualTo(1.5);
        assertThat(c.apply(w(0, 0.1, 1)).condition()).isEqualTo("NONE");         // 0.1℃ → 해당 없음
    }

    @Test
    void strongWindBoundaryIsInclusive() {
        assertThat(c.apply(w(0, 15, 10.0)).condition()).isEqualTo("STRONG_WIND");  // 정확히 10m/s → 강풍
        assertThat(c.apply(w(0, 15, 9.9)).condition()).isEqualTo("NONE");
        assertThat(c.apply(w(0, 15, 10.0)).multiplier()).isEqualTo(1.2);
    }

    @Test
    void overlappingConditionsUseOnlyTheFirstInPriorityOrder() {
        // 눈 + 영하 + 강풍 → 눈 하나만(곱하지 않는다)
        assertThat(c.apply(w(3, -5, 15))).isEqualTo(new WeatherCorrection.Applied("SNOW", 1.8));
        // 영하 + 강풍 → 영하
        assertThat(c.apply(w(0, -5, 15)).condition()).isEqualTo("FREEZING");
    }

    @Test
    void scoreIs100DividedByMultiplier() {
        assertThat(c.score(1.0)).isEqualTo(100.0);
        assertThat(c.score(2.0)).isEqualTo(50.0);
        assertThat(c.score(1.25)).isEqualTo(80.0);
    }

    @Test
    void thresholdsComeFromTheConfiguredValues() {
        WeatherCorrection other = new WeatherCorrection(1.8, 1.6, 1.4, 1.5, 1.2, -2, 15);
        assertThat(other.apply(w(0, -1, 12)).condition()).isEqualTo("NONE");     // -1℃ > -2℃, 12 < 15
        assertThat(other.apply(w(0, -2, 1)).condition()).isEqualTo("FREEZING");
        assertThat(other.apply(w(0, 5, 15)).condition()).isEqualTo("STRONG_WIND");
    }

    @Test
    void invalidConfigurationIsRejected() {
        assertThatThrownBy(() -> new WeatherCorrection(0.9, 1.6, 1.4, 1.5, 1.2, 0, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WeatherCorrection(1.8, 1.6, 1.4, 1.5, 1.2, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WeatherCorrection(Double.NaN, 1.6, 1.4, 1.5, 1.2, 0, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
