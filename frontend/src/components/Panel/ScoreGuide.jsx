import { useState } from 'react';
import { getLevelStyle, SCORE_GUIDE_BANDS } from '../../utils/scoreLevel';
import { WALK_MODES } from '../../utils/walkMode';

// 구간 표시는 utils/scoreLevel.js 의 같은 상수에서 만든다. 가중치·반경 같은 계산 설정값은 서버 설정이 원본이라
// 이 화면에 숫자로 적지 않고, 경로 카드의 "점수 내역"과 서버가 준 설명(scoreModel.note)을 그대로 보여 준다.
const SAMPLE_SCORE = { low: 10, mid: 50, high: 90 };

// 서버가 내려 준 유형별 임계값(각도 °)을 문구로. 값이 없으면 문구를 만들지 않는다.
function describeThresholds(walkThresholds) {
  if (!walkThresholds) return null;
  const parts = WALK_MODES.map(({ value, label }) => {
    const t = walkThresholds[value];
    return t ? `${label} ${t.goodMaxDeg}° 이하 양호 · ${t.cautionMaxDeg}° 이하 주의` : null;
  }).filter(Boolean);
  return parts.length ? parts.join(', ') : null;
}

export default function ScoreGuide({ scoreModel }) {
  const [open, setOpen] = useState(false);
  const thresholds = describeThresholds(scoreModel?.walkThresholds);

  return (
    <div style={{ padding: '0 16px 16px' }}>
      <button className="score-guide-toggle" onClick={() => setOpen((v) => !v)} aria-expanded={open}>
        <span>경로 점수 기준과 산정 방식</span>
        <span style={{ fontSize: 10 }}>{open ? '▲' : '▼'}</span>
      </button>

      {open && (
        <div className="score-guide-body">
          <div style={{ display: 'flex', gap: 7, marginBottom: 10 }}>
            {SCORE_GUIDE_BANDS.map(({ level, range }) => {
              const style = getLevelStyle(SAMPLE_SCORE[level]);
              return (
                <div
                  key={level}
                  style={{ flex: 1, padding: '9px 4px', borderRadius: 8, background: style.bg, border: `1.5px solid ${style.border}`, textAlign: 'center' }}
                >
                  <div style={{ fontSize: 14, fontWeight: 800, color: style.color }}>{style.label}</div>
                  <div style={{ fontSize: 12, color: '#68707c', fontFamily: 'monospace', marginTop: 2 }}>{range}점</div>
                </div>
              );
            })}
          </div>
          <div className="score-guide-text">
            <p>
              경로 점수는 축별 점수(야간 시설 노출, 보행난이도, 날씨 보정)를 설정된 가중치로 평균한 0~100 값입니다.
              경로 카드의 “점수 내역”에서 축별 점수와 가중치를 볼 수 있습니다.
            </p>
            <p>
              <strong>검증되지 않은 설계 파라미터</strong>로 계산한 상대 비교 값이며, 실제 안전도나 위험도를 뜻하지 않습니다.
              위 구간 라벨도 화면 표시용입니다.
            </p>
            <p>
              보행난이도는 경로를 따라 읽은 경사(각도 °)를 보행 유형별 기준으로 양호·주의·위험 구간으로 나눈 비율에서 계산합니다.
              {thresholds && ` 현재 기준: ${thresholds}.`}
              경사 데이터는 약 30m 격자 DEM 이라 실제 경사보다 낮게 나오는 경향이 있습니다.
            </p>
            <p>
              붕괴위험 지정 급경사지는 점수에 포함하지 않고 지도에 경고로만 표시합니다. 보행난이도 기준과는 별개입니다.
            </p>
            {scoreModel?.note && <p className="score-guide-server-note">서버 안내: {scoreModel.note}</p>}
          </div>
        </div>
      )}
    </div>
  );
}
