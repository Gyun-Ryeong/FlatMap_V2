import WeatherCard from '../Weather/WeatherCard';
import { canRequestDetour } from '../../utils/detour';
import { getLevelStyle } from '../../utils/scoreLevel';

function SlopeWarnings({ sections }) {
  if (!sections || sections.length === 0) {
    return (
      <p className="route-detail-note">
        경로 주변에서 확인된 붕괴위험 지정 급경사지가 없습니다. (수집된 표본 데이터 기준이며, 안전을 뜻하지 않습니다.)
      </p>
    );
  }
  return (
    <div className="slope-warning">
      <div className="slope-warning-title">경로 주변 붕괴위험 지정 급경사지 {sections.length}곳</div>
      <ul className="slope-warning-list">
        {sections.map((rs, i) => (
          <li key={`${rs.lat}-${rs.lng}-${i}`}>
            <span className="slope-warning-name">{rs.name || `급경사지 ${i + 1}`}</span>
            <span className="slope-warning-detail">
              경사 {rs.grade != null ? `${rs.grade.toFixed(1)}°` : '미측정'} · 경로에서 {Math.round(rs.distanceFromRoute)}m
            </span>
          </li>
        ))}
      </ul>
      <p className="route-detail-note">
        붕괴위험 지정지는 경로 점수에 포함되지 않는 별도의 경고입니다. 지도에는 주황색으로 표시됩니다.
      </p>
    </div>
  );
}

function DetourResult({ route, data }) {
  const before = data.originalTotalScore;
  const after = data.totalScore;
  const km = (data.totalDistance / 1000).toFixed(1);
  const min = Math.round(data.totalDuration / 60);
  const level = getLevelStyle(after);
  return (
    <div className="detour-result">
      <div className="detour-result-title">우회 경로 (파란 점선)</div>
      <div className="detour-result-stats">
        <span>{min}분 · {km}km</span>
        <span>
          경고 구간 {route.riskSections.length}곳 → {data.riskSections.length}곳
        </span>
        {before != null && after != null && (
          <span style={{ color: level.color }}>
            점수 {before.toFixed(1)} → {after.toFixed(1)}
          </span>
        )}
      </div>
      {data.riskSections.length > 0 && (
        <p className="route-detail-note">경고 구간을 완전히 피하지는 못했습니다. 주의해서 이동하세요.</p>
      )}
    </div>
  );
}

// 선택된 경로 카드 아래: 날씨, 붕괴위험 경고 구간, 우회 경로
export default function RouteDetail({ route, detour, onRequestDetour }) {
  return (
    <div className="route-detail">
      <WeatherCard weather={route.weather} />
      <SlopeWarnings sections={route.riskSections} />

      {canRequestDetour(route) && detour.status !== 'done' && (
        <button
          type="button"
          className="detour-btn"
          onClick={onRequestDetour}
          disabled={detour.status === 'loading'}
        >
          {detour.status === 'loading' ? '우회 경로 찾는 중...' : '🔄 경고 구간을 피하는 우회 경로 찾기'}
        </button>
      )}
      {(detour.status === 'rejected' || detour.status === 'failed') && (
        <p className="route-error">{detour.message}</p>
      )}
      {detour.status === 'done' && <DetourResult route={route} data={detour.data} />}
    </div>
  );
}
