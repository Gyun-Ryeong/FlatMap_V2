import { useState } from 'react';
import { getLevelStyle } from '../../utils/scoreLevel';
import { describeWalkDetail } from '../../utils/walkMode';

export function Breakdown({ breakdown }) {
  if (!breakdown || !breakdown.length) return null;
  return (
    <div className="score-breakdown">
      <div className="score-breakdown-title">점수 내역 (축별 점수 × 가중치의 평균)</div>
      {breakdown.map((axis) => {
        const walk = axis.axis === 'B' ? describeWalkDetail(axis.detail) : null;
        return (
        <div key={axis.axis} className="score-breakdown-row">
          <span className="score-breakdown-name">{axis.axis} · {axis.name}</span>
          <span className="score-breakdown-bar" aria-hidden="true">
            <span style={{ width: `${Math.max(0, Math.min(100, axis.score))}%` }} />
          </span>
          <span className="score-breakdown-score">{axis.score.toFixed(1)}점</span>
          <span className="score-breakdown-weight">가중치 {axis.weight}</span>
          {walk && (
            <p className="score-breakdown-extra">
              경로 경사 구간 비율: {walk.ratios}<br />
              {walk.criteria}<br />
              {walk.slopes} · 점수가 높을수록 오르막 부담이 작습니다.
            </p>
          )}
        </div>
        );
      })}
    </div>
  );
}

// renderDetail(route): 선택된 경로 카드 아래에 붙는 추가 정보(날씨·경고·우회 등)
export default function RouteResult({ routes, selectedRouteId, onSelectRoute, renderDetail }) {
  const [openBreakdownId, setOpenBreakdownId] = useState(null);
  if (!routes.length) return null;

  return (
    <div style={{ padding: '12px 16px', borderBottom: '1px solid rgba(203, 210, 219,0.4)' }}>
      <div style={{ fontSize: 15, fontWeight: 800, color: '#41454c', marginBottom: 9 }}>
        비교할 경로를 선택하세요
      </div>

      {routes.map((route) => {
        const level = getLevelStyle(route.totalScore);
        const selected = route.routeId === selectedRouteId;
        const km = (route.totalDistance / 1000).toFixed(1);
        const min = Math.round(route.totalTime / 60);
        const hasScore = route.totalScore != null;
        const breakdownOpen = openBreakdownId === route.routeId;

        return (
          <div key={route.routeId} style={{ marginBottom: 8 }}>
            <div
              role="button"
              tabIndex={0}
              aria-pressed={selected}
              onClick={() => onSelectRoute(route.routeId)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' || e.key === ' ') {
                  e.preventDefault();
                  onSelectRoute(route.routeId);
                }
              }}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 10,
                minHeight: 88,
                padding: '12px',
                borderRadius: 11,
                border: `2px solid ${selected ? level.border : '#e0e2e5'}`,
                background: selected ? level.bg : 'white',
                cursor: 'pointer',
                boxShadow: selected ? `0 2px 10px ${level.color}1a` : '0 1px 3px rgba(0, 0, 0,0.04)',
                position: 'relative',
                overflow: 'hidden',
                transition: 'all 0.12s',
              }}
            >
              {selected && (
                <div style={{ position: 'absolute', left: 0, top: 0, bottom: 0, width: 3, background: level.color, opacity: 0.7 }} />
              )}

              <div style={{ paddingLeft: selected ? 4 : 0, flex: 1, minWidth: 0 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 5, marginBottom: 2 }}>
                  <span style={{ fontSize: 16, fontWeight: 800, color: '#212328' }}>{route.searchOption}</span>
                  {route.recommended && (
                    <span style={{ fontSize: 11, padding: '3px 7px', borderRadius: 99, background: '#e3ebf2', color: '#334563', fontWeight: 800 }}>
                      점수 최고
                    </span>
                  )}
                </div>
                <div style={{ display: 'flex', gap: 10, fontSize: 13, color: '#686d74' }}>
                  <span style={{ fontWeight: 800, color: '#2b2d35', fontSize: 15 }}>{min}분</span>
                  <span>{km}km</span>
                </div>
                <div
                  title="경로 주변에 있는 시설 개수 (반경은 서버 설정값)"
                  style={{ display: 'flex', flexWrap: 'wrap', gap: '2px 10px', marginTop: 4, fontSize: 13, fontWeight: 700 }}
                >
                  <span style={{ color: '#5a7ab8' }}>CCTV {route.cctvCount}개</span>
                  <span style={{ color: '#a77a1f' }}>보안등 {route.lightCount}개</span>
                  <span style={{ color: '#9a5656' }}>경찰시설 {route.policeCount}개</span>
                </div>
                {hasScore && (
                  <div style={{ height: 3.5, background: '#edeef0', borderRadius: 2, overflow: 'hidden', marginTop: 5 }}>
                    <div style={{ height: '100%', width: `${route.totalScore}%`, background: level.color, borderRadius: 2, opacity: 0.6 }} />
                  </div>
                )}
              </div>

              {hasScore && (
                <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 3, flexShrink: 0 }}>
                  <span
                    style={{
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: 5,
                      padding: '5px 9px',
                      borderRadius: 99,
                      fontSize: 13,
                      fontWeight: 800,
                      background: level.bg,
                      color: level.color,
                      border: `1.5px solid ${level.border}`,
                    }}
                  >
                    <span style={{ width: 7, height: 7, borderRadius: '50%', background: level.color }} />
                    {level.label}
                  </span>
                  <span style={{ fontSize: 15, fontWeight: 800, color: level.color, fontFamily: 'monospace' }}>
                    {route.totalScore.toFixed(1)}점
                  </span>
                </div>
              )}
            </div>

            {selected && (
              <div className="route-card-extra">
                <button
                  type="button"
                  className="breakdown-toggle"
                  aria-expanded={breakdownOpen}
                  onClick={() => setOpenBreakdownId(breakdownOpen ? null : route.routeId)}
                >
                  점수 내역 {breakdownOpen ? '접기' : '보기'}
                </button>
                {breakdownOpen && <Breakdown breakdown={route.breakdown} />}
                {renderDetail && renderDetail(route)}
              </div>
            )}
          </div>
        );
      })}
    </div>
  );
}
