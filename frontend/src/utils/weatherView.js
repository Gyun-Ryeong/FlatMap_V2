// 날씨 표시용 순수 함수. 서버의 WeatherInfo(icon, fallback, warning, ...)를 화면에 맞게 읽는다.
const ICONS = {
  clear: '☀️',
  cloudy: '⛅',
  overcast: '☁️',
  rain: '🌧️',
  snow: '❄️',
  sleet: '🌨️',
};

export function weatherIcon(icon) {
  return ICONS[icon] ?? '❔';
}

// fallback=true 는 기상 API 값이 아니라 대체값이다. 실제 관측처럼 보이지 않게 표시하고 점수에도 반영되지 않았다.
export function isRealWeather(weather) {
  return !!weather && weather.fallback !== true;
}

export function formatTemperature(weather) {
  if (!isRealWeather(weather) || weather.temperature == null) return '--';
  return `${Math.round(weather.temperature)}°C`;
}

export function formatWind(weather) {
  if (!isRealWeather(weather) || weather.windSpeed == null) return '--';
  return `${weather.windSpeed.toFixed(1)}m/s`;
}

export function formatHumidity(weather) {
  if (!isRealWeather(weather) || weather.humidity == null) return '--';
  return `${weather.humidity}%`;
}
