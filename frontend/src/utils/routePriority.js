export const PRIORITY_OPTIONS = [
  { value: 'safety', label: '안전 우선', description: '점수가 가장 높은 경로' },
  { value: 'balanced', label: '균형', description: '점수와 시간을 같은 비중으로' },
  { value: 'speed', label: '빠른 길', description: '도착 시간을 우선' },
];

// 균형 기준에서 점수와 시간에 주는 비중(화면 선택 로직용 상수, 서버 점수와 무관)
const BALANCED_SCORE_WEIGHT = 0.5;

// 백엔드가 준 후보 경로 중 선택 기준(안전 우선 / 균형 / 빠른 길)에 맞는 경로를 고른다.
export function pickRouteByPriority(routes, priority) {
  if (!routes || !routes.length) return null;

  const byScore = (a, b) => (b.totalScore ?? 0) - (a.totalScore ?? 0);
  const byTime = (a, b) => a.totalTime - b.totalTime;

  if (priority === 'speed') {
    return [...routes].sort(byTime)[0];
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
