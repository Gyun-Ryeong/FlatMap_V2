import { formatHumidity, formatTemperature, formatWind, isRealWeather, weatherIcon } from '../../utils/weatherView';

// 경로 기준 날씨(경로 중간 지점). 기상 값을 가져오지 못한 경우(fallback)는 실제 값처럼 보이지 않게 안내만 하며,
// 그 경우 날씨 보정(C축)은 점수에 반영되지 않았다.
export default function WeatherCard({ weather }) {
  if (!weather) return null;

  if (!isRealWeather(weather)) {
    return (
      <div className="weather-card weather-card-missing">
        <span className="weather-icon-large">{weatherIcon('unknown')}</span>
        <div>
          <div className="weather-desc">날씨 정보를 가져오지 못했습니다</div>
          <div className="weather-missing-note">이 경로의 점수에는 날씨 보정이 반영되지 않았습니다.</div>
        </div>
      </div>
    );
  }

  return (
    <div className="weather-card">
      <div className="weather-main">
        <span className="weather-icon-large">{weatherIcon(weather.icon)}</span>
        <div className="weather-info">
          <span className="weather-temp">{formatTemperature(weather)}</span>
          <span className="weather-desc">{weather.description || '--'}</span>
        </div>
        <div className="weather-detail">
          <span>습도 {formatHumidity(weather)}</span>
          <span>풍속 {formatWind(weather)}</span>
        </div>
      </div>
      {weather.warning && (
        <div className="weather-warning">
          <span aria-hidden="true">⚠️</span>
          <span>{weather.warning}</span>
        </div>
      )}
    </div>
  );
}
