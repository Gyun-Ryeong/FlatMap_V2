// 후보 경로(T Map searchOption 코드) → 우회 요청이 쓰는 옵션 이름. 우회 요청은 원경로를 같은 옵션으로 다시 조회해
// 비교하므로, 사용자가 고른 후보와 같은 옵션을 써야 경로 인덱스(nearestRouteIdx)가 맞는다.
const OPTION_BY_SEARCH_CODE = { 0: 'SAFE', 4: 'MAIN_ROAD', 10: 'SHORT', 30: 'FLAT' };

export function detourOptionFor(searchOptionCode) {
  return OPTION_BY_SEARCH_CODE[searchOptionCode] ?? 'SAFE';
}

// 우회 가능 여부: 급경사지(붕괴위험 지정지) 경고 구간이 있는 경로만 우회 대상이다.
export function canRequestDetour(route) {
  return !!route && Array.isArray(route.riskSections) && route.riskSections.length > 0;
}

// walkMode: 원경로 점수를 낸 보행 유형. 우회 경로도 같은 유형으로 점수를 비교해야 한다.
export function buildDetourRequest(route, origin, destination, walkMode) {
  return {
    originLat: origin.lat,
    originLng: origin.lng,
    destLat: destination.lat,
    destLng: destination.lng,
    option: detourOptionFor(route.searchOptionCode),
    walkMode,
    avoidPoints: route.riskSections.map((rs) => ({
      lat: rs.lat,
      lng: rs.lng,
      nearestRouteIdx: rs.nearestRouteIdx,
    })),
  };
}
