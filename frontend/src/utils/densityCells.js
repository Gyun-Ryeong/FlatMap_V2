// 시설 밀도 격자(facility_density_grid) 셀 조회용 순수 함수.
// 셀은 반개구간 [min, max) 이다(백엔드 격자 집계와 같은 규칙).
export const DENSITY_LABELS = {
  LOW_DENSITY: '시설 적음',
  MEDIUM: '보통',
  HIGH_DENSITY: '시설 많음',
};

export function findCellAt(cells, lat, lng) {
  if (!cells || !cells.length) return null;
  return (
    cells.find((c) => lat >= c.minLat && lat < c.maxLat && lng >= c.minLng && lng < c.maxLng) ?? null
  );
}

export function isLowDensity(cell) {
  return !!cell && cell.densityLevel === 'LOW_DENSITY';
}
