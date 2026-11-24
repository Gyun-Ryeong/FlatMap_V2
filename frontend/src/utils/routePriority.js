export const PRIORITY_OPTIONS = [
  { value: 'safety', label: '안전 우선', description: '점수가 가장 높은 경로' },
  { value: 'balanced', label: '균형', description: '점수와 시간을 같은 비중으로' },
  { value: 'speed', label: '빠른 길', description: '도착 시간을 우선' },
  { value: 'flat', label: '오르막 최소', description: '경사 부담이 가장 작은 경로' },
];

// T Map searchOption 30 = 최단 + 계단 제외. FlatMap 의 "오르막 최소"(FLAT) 옵션이 쓰던 값이다.
const STAIRS_FREE_SEARCH_CODE = 30;

// 균형 기준에서 점수와 시간에 주는 비중(화면 선택 로직용 상수, 서버 점수와 무관)
const BALANCED_SCORE_WEIGHT = 0.5;

// 보행난이도(B축) 점수: 경사 격자에 값이 없는 경로는 B축이 없어 null 이다.
function walkScoreOf(route) {
  const b = route.breakdown?.find((axis) => axis.axis === 'B');
  return b ? b.score : null;
}

// 백엔드가 준 후보 경로 중 선택 기준(안전 우선 / 균형 / 빠른 길 / 오르막 최소)에 맞는 경로를 고른다.
export function pickRouteByPriority(routes, priority) {
  if (!routes || !routes.length) return null;

  const byScore = (a, b) => (b.totalScore ?? 0) - (a.totalScore ?? 0);
  const byTime = (a, b) => a.totalTime - b.totalTime;

  if (priority === 'speed') {
    return [...routes].sort(byTime)[0];
  }

  if (priority === 'flat') {
    // 보행난이도(B축) 점수가 가장 높은(오르막 부담이 가장 작은) 경로. 같으면 총점, 그다음 시간.
    const scored = routes.filter((r) => walkScoreOf(r) != null);
    if (scored.length) {
      return [...scored].sort((a, b) => walkScoreOf(b) - walkScoreOf(a) || byScore(a, b) || byTime(a, b))[0];
    }
    // 경사 데이터가 없으면 FlatMap 원래 옵션(계단 제외 경로)을, 그것도 없으면 서버 추천 경로를 쓴다.
    return routes.find((r) => r.searchOptionCode === STAIRS_FREE_SEARCH_CODE)
      ?? routes.find((r) => r.recommended) ?? [...routes].sort(byScore)[0];
  }

  if (priority === 'balanced') {
    const times = routes.map((r) => r.totalTime);
    const minT = Math.min(...times);
    const span = Math.max(...times) - minT || 1;
    const value = (r) =>
      ((r.totalScore ?? 0) / 100) * BALANCED_SCORE_WEIGHT +
      (1 - (r.totalTime - minT) / span) * (1 - BALANCED_SCORE_WEIGHT);
    return [...routes].sort((a, b) => value(b) - value(a))[0];
  }

  return routes.find((r) => r.recommended) ?? [...routes].sort(byScore)[0];
}
