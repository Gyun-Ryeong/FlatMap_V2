import { describe, expect, it } from 'vitest';
import {
  formatHumidity, formatTemperature, formatWind, isRealWeather, weatherIcon,
} from '../weatherView';

describe('날씨 표시', () => {
  const real = { temperature: 12.4, windSpeed: 3.26, humidity: 70, icon: 'rain', fallback: false };
  const fallback = { temperature: 20, windSpeed: 1, humidity: 50, icon: 'unknown', fallback: true };

  it('알려진 아이콘은 이모지로, 모르는 값은 물음표로', () => {
    expect(weatherIcon('rain')).toBe('🌧️');
    expect(weatherIcon('unknown')).toBe('❔');
    expect(weatherIcon(undefined)).toBe('❔');
  });

  it('대체값(fallback)은 실제 날씨가 아니다', () => {
    expect(isRealWeather(real)).toBe(true);
    expect(isRealWeather(fallback)).toBe(false);
    expect(isRealWeather(null)).toBe(false);
  });

  it('실제 날씨는 반올림해 표시한다', () => {
    expect(formatTemperature(real)).toBe('12°C');
    expect(formatWind(real)).toBe('3.3m/s');
    expect(formatHumidity(real)).toBe('70%');
  });

  it('대체값이나 값이 없으면 수치를 지어내지 않고 "--" 로 표시한다', () => {
    [fallback, null].forEach((w) => {
      expect(formatTemperature(w)).toBe('--');
      expect(formatWind(w)).toBe('--');
      expect(formatHumidity(w)).toBe('--');
    });
  });
});
