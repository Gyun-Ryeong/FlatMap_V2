// 지도에 겹쳐 볼 수 있는 레이어. facility 는 /api/safety/facilities, density 는 /api/density-grid 에서 가져온다.
export const FACILITY_LAYERS = [
  { key: 'CCTV', label: 'CCTV', meta: '방범용 카메라', color: '#5a7ab8', symbol: '●' },
  { key: 'SECURITY_LIGHT', label: '보안등', meta: '야간 조명', color: '#c8942a', symbol: '▲' },
  { key: 'POLICE', label: '경찰시설', meta: '파출소·지구대', color: '#b84c4c', symbol: '警' },
];

export const AREA_LAYERS = [
  { key: 'DENSITY', label: '시설 밀도 격자', meta: '시설이 적은 곳 확인', color: '#4f6f9c', symbol: '▦' },
  { key: 'STEEP', label: '급경사지 경고', meta: '붕괴위험 지정지', color: '#e0701f', symbol: '!' },
];

// 카카오 지도 레벨 5 이하(확대)에서만 시설이 불러와진다 — FacilityOverlay 와 같은 기준
export const FACILITY_MAX_LEVEL = 5;

function Tile({ label, meta, color, symbol, active, onClick }) {
  return (
    <button
      onClick={onClick}
      aria-pressed={active}
      style={{
        minHeight: 68,
        padding: '10px',
        borderRadius: 10,
        border: active ? `2px solid ${color}88` : '1.5px solid #d9dde4',
        background: active ? `${color}0d` : 'white',
        color: active ? color : '#666d78',
        cursor: 'pointer',
        fontFamily: 'inherit',
        display: 'flex',
        alignItems: 'center',
        gap: 10,
        textAlign: 'left',
      }}
    >
      <span
        style={{
          width: 38,
          height: 38,
          borderRadius: 9,
          background: active ? color : '#aeb4be',
          color: 'white',
          display: 'inline-flex',
          alignItems: 'center',
          justifyContent: 'center',
          fontSize: symbol.length > 1 ? 14 : 18,
          fontWeight: 800,
          flexShrink: 0,
        }}
      >
        {symbol}
      </span>
      <span style={{ minWidth: 0 }}>
        <span style={{ display: 'block', fontSize: 14, fontWeight: 800 }}>{label}</span>
        <span style={{ display: 'block', marginTop: 2, fontSize: 12, color: active ? '#626a75' : '#858b95' }}>
          {active ? meta : '현재 숨김'}
        </span>
      </span>
    </button>
  );
}

export default function LayerPanel({ layers, onToggle, onToggleAllFacilities, zoom, facilityTruncated, steepCount }) {
  const allFacilitiesOn = FACILITY_LAYERS.every((f) => layers[f.key]);
  const anyFacilityOn = FACILITY_LAYERS.some((f) => layers[f.key]);
  const tooFar = (anyFacilityOn || layers.DENSITY) && zoom != null && zoom > FACILITY_MAX_LEVEL;

  return (
    <div style={{ padding: '14px 16px', display: 'flex', flexDirection: 'column', gap: 10 }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 10 }}>
        <div>
          <div style={{ fontSize: 16, fontWeight: 800, color: '#30343d' }}>지도 레이어</div>
          <div style={{ fontSize: 13, color: '#656c77', marginTop: 3 }}>보고 싶은 정보를 누르면 지도에 표시됩니다</div>
        </div>
        <button className="layer-all-button" data-on={allFacilitiesOn} onClick={onToggleAllFacilities}>
          {allFacilitiesOn ? '시설 모두 숨김' : '시설 모두 보기'}
        </button>
      </div>

      {tooFar && (
        <div className="layer-notice">지도를 더 확대하면 시설과 격자가 표시됩니다</div>
      )}
      {facilityTruncated && !tooFar && (
        <div className="layer-notice">이 영역에는 시설이 많아 일부만 표시합니다. 지도를 더 확대해 보세요.</div>
      )}

      <div className="facility-grid" style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 6 }}>
        {FACILITY_LAYERS.map((f) => (
          <Tile key={f.key} label={f.label} meta={f.meta} color={f.color} symbol={f.symbol} active={!!layers[f.key]} onClick={() => onToggle(f.key)} />
        ))}
        {AREA_LAYERS.map((f) => (
          <Tile key={f.key} label={f.label} meta={f.meta} color={f.color} symbol={f.symbol} active={!!layers[f.key]} onClick={() => onToggle(f.key)} />
        ))}
      </div>
      {layers.STEEP && (
        <p className="layer-footnote">
          급경사지 경고는 붕괴위험으로 지정된 곳만 모은 표본{steepCount != null ? ` ${steepCount}건` : ''}이며, 보행 난이도와는 별개입니다.
        </p>
      )}
    </div>
  );
}
