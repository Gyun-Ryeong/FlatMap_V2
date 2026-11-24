import { useEffect, useState } from 'react';
import { fetchSteepSlopes } from '../../api/mapData';
import {
  REGION_OPTIONS, SORT_OPTIONS, formatSteepAddress, gradeLabel, sortSteepSlopes, summarizeSteep,
} from '../../utils/steepList';

// 목록 본문(데이터를 이미 받은 상태). 렌더 테스트를 위해 상태 없는 컴포넌트로 분리했다.
export function SteepSlopeListBody({
  items, loading, error, region, onRegionChange, sort, onSortChange, onPick,
}) {
  const { total, mountain, unmeasured } = summarizeSteep(items);
  const sorted = sortSteepSlopes(items, sort);

  return (
    <div className="steep-list-body">
      <p className="steep-list-desc">
        붕괴위험으로 지정된 성남시 급경사지 표본입니다({total}건
        {mountain > 0 && <span className="steep-list-terrain"> · 산지형 {mountain}건</span>}
        {unmeasured > 0 && <span className="steep-list-warn"> · 경사도 미측정 {unmeasured}건</span>}).
        위험·주의·양호 같은 등급은 없고, 경로의 보행난이도와는 별개입니다.
      </p>

      <div className="steep-filter-row">
        <select className="steep-filter-select" value={region} onChange={(e) => onRegionChange(e.target.value)} aria-label="지역">
          {REGION_OPTIONS.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
        </select>
        <select className="steep-filter-select" value={sort} onChange={(e) => onSortChange(e.target.value)} aria-label="정렬">
          {SORT_OPTIONS.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
        </select>
      </div>

      {loading && <p className="steep-list-note">데이터를 불러오는 중...</p>}
      {error && <p className="route-error">{error}</p>}

      <div className="steep-segment-list">
        {sorted.map((item, i) => (
          <button
            type="button"
            key={item.id ?? i}
            className="steep-segment-item"
            onClick={() => onPick?.(item)}
            disabled={item.latitude == null || item.longitude == null}
          >
            <span className="steep-segment-info">
              <span className="steep-segment-name">
                {item.isMountain && <span className="steep-terrain-tag" title="산 지번(임야) — 산지형 급경사지">산지형</span>}
                {item.name || '이름 없음'}
              </span>
              <span className="steep-segment-city">{formatSteepAddress(item)}</span>
            </span>
            <span className={`steep-grade-badge${item.grade == null ? ' unknown' : ''}`}>{gradeLabel(item.grade)}</span>
          </button>
        ))}
        {!loading && !error && sorted.length === 0 && (
          <p className="steep-list-note">조건에 맞는 데이터가 없습니다. 급경사지 데이터를 아직 수집하지 않았을 수 있습니다.</p>
        )}
      </div>
    </div>
  );
}

// 오르막길 구간(FlatMap 오르막길 탭 복원). 펼칠 때 한 번 불러오고 지역을 바꾸면 다시 불러온다.
// 항목을 누르면 지도에서 그 위치로 이동하고 급경사지 경고 레이어를 켠다(onPick).
export default function SteepSlopeList({ onPick }) {
  const [open, setOpen] = useState(false);
  const [region, setRegion] = useState('411');
  const [sort, setSort] = useState('grade');
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    if (!open) return undefined;
    let cancelled = false;
    setLoading(true);
    setError(null);
    fetchSteepSlopes(region)
      .then((data) => { if (!cancelled) setItems(Array.isArray(data) ? data : []); })
      .catch(() => {
        if (cancelled) return;
        setItems([]);
        setError('급경사지 목록을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.');
      })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [open, region]);

  return (
    <div style={{ padding: '0 16px 12px' }}>
      <button className="score-guide-toggle" onClick={() => setOpen((v) => !v)} aria-expanded={open}>
        <span>⛰️ 오르막길 구간 (급경사지 목록)</span>
        <span style={{ fontSize: 10 }}>{open ? '▲' : '▼'}</span>
      </button>
      {open && (
        <SteepSlopeListBody
          items={items} loading={loading} error={error}
          region={region} onRegionChange={setRegion} sort={sort} onSortChange={setSort}
          onPick={onPick}
        />
      )}
    </div>
  );
}
