import { useState } from 'react';
import { getLevelStyle, SCORE_GUIDE_BANDS } from '../../utils/scoreLevel';

// 구간 표시는 utils/scoreLevel.js 의 같은 상수에서 만든다. 가중치·반경 같은 계산 설정값은 서버 설정이 원본이라
// 이 화면에 숫자로 적지 않고, 경로 카드의 "점수 내역"과 서버가 준 설명(scoreModel.note)을 그대로 보여 준다.
const SAMPLE_SCORE = { low: 10, mid: 50, high: 90 };

export default function ScoreGuide({ scoreModel }) {
  const [open, setOpen] = useState(false);

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
              경로 점수는 축별 점수(야간 시설 노출, 날씨 보정)를 설정된 가중치로 평균한 0~100 값입니다.
              경로 카드의 “점수 내역”에서 축별 점수와 가중치를 볼 수 있습니다.
            </p>
            <p>
              <strong>검증되지 않은 설계 파라미터</strong>로 계산한 상대 비교 값이며, 실제 안전도나 위험도를 뜻하지 않습니다.
              위 구간 라벨도 화면 표시용입니다.
            </p>
            <p>
              붕괴위험 지정 급경사지는 점수에 포함하지 않고 지도에 경고로만 표시합니다.
            </p>
            {scoreModel?.note && <p className="score-guide-server-note">서버 안내: {scoreModel.note}</p>}
          </div>
        </div>
      )}
    </div>
  );
}
