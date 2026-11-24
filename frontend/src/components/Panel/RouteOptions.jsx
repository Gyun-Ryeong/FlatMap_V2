import { PRIORITY_OPTIONS } from '../../utils/routePriority';
import { WALK_MODES } from '../../utils/walkMode';

// 이동 기준별 은은한 색(안전=초록, 균형=파랑, 빠른 길=노랑, 오르막 최소=보라).
// 선택 여부는 색만이 아니라 ✓ 와 굵은 테두리로도 구분한다. 글자색은 배경 대비 4.5:1 이상으로 맞췄다.
const PRIORITY_TONES = {
  safety: { border: '#cadacf', selBorder: '#80a18d', bg: '#f3f6f4', selBg: '#e5eee8', text: '#405c4b', desc: '#556a5e' },
  balanced: { border: '#cfd8e3', selBorder: '#889ab6', bg: '#f1f4f8', selBg: '#e6ecf3', text: '#384a67', desc: '#58667d' },
  speed: { border: '#e5dcc3', selBorder: '#c4ab7e', bg: '#f9f6ee', selBg: '#f3ecda', text: '#725c3c', desc: '#706145' },
  flat: { border: '#d6d0e1', selBorder: '#a59bbe', bg: '#f4f2f8', selBg: '#eae6f1', text: '#584d77', desc: '#685f80' },
};
const FALLBACK_TONE = PRIORITY_TONES.balanced;

// 백엔드가 준 후보 경로 중 기준에 맞는 경로를 먼저 선택해 보여준다
export default function RouteOptions({ priority, onChangePriority, walkMode, onChangeWalkMode }) {
  return (
    <div style={{ marginTop: 5, padding: '14px', background: '#f7f8f9', border: '1.5px solid #e1e3e6', borderRadius: 12 }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 10 }}>
        <span style={{ fontSize: 16, fontWeight: 800, color: '#31343a' }}>원하는 길을 선택하세요</span>
        <span style={{ fontSize: 12, color: '#6b7078', fontWeight: 600 }}>하나 선택</span>
      </div>

      <div className="priority-grid" style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 7 }}>
        {PRIORITY_OPTIONS.map((option) => {
          const selected = priority === option.value;
          const tone = PRIORITY_TONES[option.value] ?? FALLBACK_TONE;
          return (
            <button
              key={option.value}
              onClick={() => onChangePriority(option.value)}
              aria-pressed={selected}
              style={{
                minHeight: 72,
                padding: '10px 5px',
                borderRadius: 10,
                border: selected ? `2px solid ${tone.selBorder}` : `1.5px solid ${tone.border}`,
                background: selected ? tone.selBg : tone.bg,
                color: tone.text,
                fontSize: 15,
                fontWeight: 800,
                cursor: 'pointer',
                fontFamily: 'inherit',
                lineHeight: 1.3,
              }}
            >
              <span style={{ display: 'block' }}>{selected ? '✓ ' : ''}{option.label}</span>
              <span style={{ display: 'block', marginTop: 4, fontSize: 11, fontWeight: 500, color: tone.desc }}>
                {option.description}
              </span>
            </button>
          );
        })}
      </div>

      <div className="walk-mode">
        <div className="walk-mode-head">
          <span className="walk-mode-title">보행 유형</span>
          <span className="walk-mode-badge">경로 점수에 반영</span>
        </div>
        <div className="walk-mode-buttons" role="group" aria-label="보행 유형">
          {WALK_MODES.map((mode) => {
            const selected = walkMode === mode.value;
            return (
              <button
                key={mode.value}
                type="button"
                onClick={() => onChangeWalkMode?.(mode.value)}
                aria-pressed={selected}
                className={selected ? 'selected' : ''}
              >
                <span>{selected ? '✓ ' : ''}{mode.label}</span>
                <span className="walk-mode-desc">{mode.description}</span>
              </button>
            );
          })}
        </div>
        <p className="walk-mode-note">
          경로의 경사(DEM 약 30m 격자)로 오르막 정도를 평가해 점수의 보행난이도 항목에 반영합니다. 보행약자 기준이 더 엄격합니다.
          경사는 실제보다 낮게 나오는 경향이 있고, 성남시 범위 밖이거나 경사 데이터가 없는 경로는 이 항목을 점수에 넣지 않습니다.
        </p>
      </div>
    </div>
  );
}
