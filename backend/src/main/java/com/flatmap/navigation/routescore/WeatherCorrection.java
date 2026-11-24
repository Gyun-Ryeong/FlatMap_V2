package com.flatmap.navigation.routescore;

import com.flatmap.navigation.dto.WeatherInfo;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 날씨 → C축(날씨 보정) 변환 규칙. 모든 값은 application.properties(weather.correction.*)에서 온다.
 *
 * <p>날씨마다 "보정 계수(1.0 이상)"를 정하고, C축 점수 = 100 ÷ 계수 이다(계수 1.0 이면 100점, 클수록 낮다).
 * 이 계수는 기상 조건이 보행에 불리하다고 가정한 <b>검증되지 않은 설계 파라미터</b>이며 위험도가 아니다.
 *
 * <p>조건이 겹치면 아래 순서로 먼저 맞는 것 하나만 쓴다(곱하지 않는다): 눈 → 비/눈 → 비 → 영하 → 강풍 → 해당 없음.
 * 이 순서와 경계(기온 ≤ freezing, 풍속 ≥ wind 임계)는 기존 {@code WeatherInfo.getRiskMultiplier()} 와 같다.
 *
 * @param freezingTempCelsius 이 기온 이하이면 결빙 보정 (경계 포함)
 * @param strongWindMps       이 풍속(m/s) 이상이면 강풍 보정 (경계 포함)
 */
public record WeatherCorrection(
        double snow,
        double sleet,
        double rain,
        double freezing,
        double strongWind,
        double freezingTempCelsius,
        double strongWindMps) {

    /** 보정 사유 코드. 응답 breakdown 에 그대로 실린다. */
    public static final String CLEAR = "NONE";

    public WeatherCorrection {
        for (double m : new double[]{snow, sleet, rain, freezing, strongWind}) {
            if (!Double.isFinite(m) || m < 1.0) {
                throw new IllegalArgumentException("날씨 보정 계수는 1.0 이상이어야 한다.");
            }
        }
        if (!Double.isFinite(freezingTempCelsius) || !Double.isFinite(strongWindMps) || strongWindMps <= 0) {
            throw new IllegalArgumentException("날씨 보정 경계값이 올바르지 않다.");
        }
    }

    /** 이 날씨에 적용되는 보정 사유와 계수. */
    public Applied apply(WeatherInfo w) {
        int pty = w.getPrecipitationType();
        if (pty == 3 || pty == 7) return new Applied("SNOW", snow);
        if (pty == 2 || pty == 6) return new Applied("SLEET", sleet);
        if (pty == 1 || pty == 5) return new Applied("RAIN", rain);
        if (w.getTemperature() <= freezingTempCelsius) return new Applied("FREEZING", freezing);
        if (w.getWindSpeed() >= strongWindMps) return new Applied("STRONG_WIND", strongWind);
        return new Applied(CLEAR, 1.0);
    }

    /** C축 점수(0~100) = 100 ÷ 계수. */
    public double score(double multiplier) {
        return 100.0 / multiplier;
    }

    /** 점수 계산에 쓰인 입력값. breakdown.detail 에 들어간다. */
    public Map<String, Object> detail(WeatherInfo w, Applied applied) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("condition", applied.condition());
        d.put("multiplier", applied.multiplier());
        d.put("precipitationType", w.getPrecipitationType());
        d.put("temperature", w.getTemperature());
        d.put("windSpeed", w.getWindSpeed());
        d.put("freezingTempCelsius", freezingTempCelsius);
        d.put("strongWindMps", strongWindMps);
        return d;
    }

    public record Applied(String condition, double multiplier) {
    }
}
