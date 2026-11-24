import { PRIORITY_OPTIONS } from '../../utils/routePriority';

// 보행약자 / 일반 모드 선택 자리. 경사 기반 보행난이도(06 단계)가 연결되기 전까지는 동작하지 않는다.
const WALK_MODES = [
  { value: 'WEAK', label: '보행약자' },
  { value: 'GENERAL', label: '일반' },
];

// 백엔드가 준 후보 경로 중 기준에 맞는 경로를 먼저 선택해 보여준다
export default function RouteOptions({ priority, onChangePriority }) {
  return (
    <div style={{ marginTop: 5, padding: '14px', background: '#f7f8fa', border: '1.5px solid #e0e3e8', borderRadius: 12 }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 10 }}>
        <span style={{ fontSize: 16, fontWeight: 800, color: '#30343d' }}>원하는 길을 선택하세요</span>
        <span style={{ fontSize: 12, color: '#69707c', fontWeight: 600 }}>하나 선택</span>
      </div>

      <div className="priority-grid" style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 7 }}>
        {PRIORITY_OPTIONS.map((option) => {
          const selected = priority === option.value;
          return (
            <button
              key={option.value}
              onClick={() => onChangePriority(option.value)}
              aria-pressed={selected}
              style={{
                minHeight: 72,
                padding: '10px 5px',
                borderRadius: 10,
                border: selected ? '2px solid #4f6f9c' : '1.5px solid #d9dde4',
                background: selected ? '#e8eff8' : 'white',
                color: selected ? '#27456f' : '#4f5662',
                fontSize: 15,
                fontWeight: 800,
                cursor: 'pointer',
                fontFamily: 'inherit',
                lineHeight: 1.3,
              }}
            >
              <span style={{ display: 'block' }}>{selected ? '✓ ' : ''}{option.label}</span>
              <span style={{ display: 'block', marginTop: 4, fontSize: 11, fontWeight: 500, color: selected ? '#4c6384' : '#7b818c' }}>
                {option.description}
              </span>
            </button>
          );
        })}
      </div>

      <div className="walk-mode" aria-disabled="true">
        <div className="walk-mode-head">
          <span className="walk-mode-title">보행 유형</span>
          <span className="walk-mode-badge">준비 중</span>
        </div>
        <div className="walk-mode-buttons">
          {WALK_MODES.map((mode) => (
            <button key={mode.value} type="button" disabled>
              {mode.label}
            </button>
          ))}
        </div>
        <p className="walk-mode-note">경사 기반 보행난이도를 반영하면 선택할 수 있게 됩니다. 지금은 경로 점수에 영향을 주지 않습니다.</p>
      </div>
    </div>
  );
}
