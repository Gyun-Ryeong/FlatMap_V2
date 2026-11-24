// FlatMap 안전정보 레이어: 사고다발지·교통약자 보호구역·장애인복지시설·그늘막·노인복지관.
// 색·이모지·엔드포인트는 FlatMap 원본(MapPage.js LAYER_COLORS / LAYER_CONFIG)과 같다.
// 이 레이어들은 위치 안내용이며 경로 점수에 반영되지 않는다. 등급·위험도 표현은 쓰지 않는다.
// "교통약자 보호구역"은 법정 명칭이라 그대로 둔다(그 외 UI 용어는 "보행약자").

export const ZONE_LAYERS = [
  { key: 'ACCIDENT', label: '사고다발지', meta: '교통사고 다발 지점', color: '#F44336', symbol: '⚠️', endpoint: '/safety/accident-zones' },
  { key: 'PROTECTED', label: '교통약자 보호구역', meta: '법정 보호구역', color: '#4CAF50', symbol: '🚸', endpoint: '/safety/protected-zones' },
  { key: 'WELFARE', label: '장애인복지시설', meta: '복지시설 위치', color: '#2196F3', symbol: '♿', endpoint: '/safety/welfare-facilities' },
  { key: 'SHELTER', label: '그늘막', meta: '더위 쉼터', color: '#FF9800', symbol: '⛱️', endpoint: '/safety/shade-shelters' },
  { key: 'SENIOR', label: '노인복지관', meta: '복지관 위치', color: '#9C27B0', symbol: '🏠', endpoint: '/safety/senior-centers' },
];

export const ZONE_KEYS = ZONE_LAYERS.map((l) => l.key);

// 카카오 지도 레벨(1 가장 확대 ~ 14 가장 축소)별로 한 번에 그리는 마커 수 상한. FlatMap 원본과 같은 구간이다.
export function maxMarkersForLevel(level) {
  if (level == null) return 200;
  if (level <= 3) return Infinity;
  if (level <= 5) return 500;
  if (level <= 6) return 300;
  if (level <= 7) return 100;
  return 30;
}

const hasPosition = (item) => Number.isFinite(item?.latitude) && Number.isFinite(item?.longitude);

// 좌표가 있는 항목 중 현재 지도 영역 안의 것만, 줌에 따른 상한까지.
// (원본은 목록 앞쪽부터 자르기만 해서 화면 밖 마커가 상한을 차지할 수 있었다 — 화면 안 항목을 먼저 센다.)
export function visibleZoneItems(items, bounds, level) {
  const valid = (items ?? []).filter(hasPosition);
  const inView = bounds
    ? valid.filter((i) => i.latitude >= bounds.lat1 && i.latitude <= bounds.lat2
      && i.longitude >= bounds.lng1 && i.longitude <= bounds.lng2)
    : valid;
  const max = maxMarkersForLevel(level);
  return max === Infinity ? inView : inView.slice(0, max);
}

// 정보 창 내용(줄 단위). 외부 데이터이므로 호출한 쪽이 textContent 로만 넣는다.
export function zoneInfoLines(item) {
  return [
    item.address ? item.address : null,
    item.phone ? `전화: ${item.phone}` : null,
    item.type ? `유형: ${item.type}` : null,
  ].filter(Boolean);
}

// 타일 보조 문구. count: undefined = 아직 안 불러옴, null = 불러오기 실패, 숫자 = 항목 수
export function zoneMetaText(layer, active, count) {
  if (!active) return '현재 숨김';
  if (count === undefined) return '불러오는 중';
  if (count === null) return '불러오지 못했습니다';
  if (count === 0) return '등록된 데이터 없음';
  return `${count}곳 · ${layer.meta}`;
}
