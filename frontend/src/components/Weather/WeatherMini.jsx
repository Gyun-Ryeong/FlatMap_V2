import { formatTemperature, isRealWeather, weatherIcon } from '../../utils/weatherView';

// 지도 위에 얹는 현재 위치(또는 기본 위치) 날씨 칩. 실제 기상 값이 있을 때만 보인다.
export default function WeatherMini({ weather }) {
  if (!isRealWeather(weather)) return null;
  return (
    <div className={`weather-mini${weather.warning ? ' warning' : ''}`} title={weather.warning || undefined}>
      <span aria-hidden="true">{weatherIcon(weather.icon)}</span>
      <span className="weather-mini-temp">{formatTemperature(weather)}</span>
    </div>
  );
}
