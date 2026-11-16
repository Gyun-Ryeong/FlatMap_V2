import { PRIORITY_OPTIONS } from '../../utils/routePriority';
import { WALK_MODES } from '../../utils/walkMode';

// 백엔드가 준 후보 경로 중 기준에 맞는 경로를 먼저 선택해 보여준다
export default function RouteOptions({ priority, onChangePriority, walkMode, onChangeWalkMode }) {
  return (
    <div style={{ marginTop: 5, padding: '14px', background: '#f7f8fa', border: '1.5px solid #e0e3e8', borderRadius: 12 }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 10 }}>
        <span style={{ fontSize: 16, fontWeight: 800, color: '#30343d' }}>원하는 길을 선택하세요</span>
        <span style={{ fontSize: 12, color: '#69707c', fontWeight: 600 }}>하나 선택</span>
      </div>

      <div className="priority-grid" style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 7 }}>
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
