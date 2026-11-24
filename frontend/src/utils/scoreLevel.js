// 경로 총점(0~100)의 화면 표시용 구간. 점수는 설정된 가중치·구간으로 계산한 "설계 파라미터 기반 상대 비교 값"이며
// 검증된 안전도·위험도가 아니므로 라벨에 "안전/위험" 같은 단정 표현을 쓰지 않는다. (기준값은 표시용 상수다.)
export const SCORE_HIGH_MIN = 71;
export const SCORE_MID_MIN = 41;

const LEVEL_STYLE = {
  high: { label: '높은 편', color: '#2d6aa3', bg: '#eef4fb', border: '#b9d0ea' },
  mid: { label: '보통', color: '#566b88', bg: '#f1f4f8', border: '#cbd5e1' },
  low: { label: '낮은 편', color: '#6b7280', bg: '#f4f5f7', border: '#d9dde4' },
  unknown: { label: '-', color: '#6b7280', bg: '#f4f5f7', border: '#d9dde4' },
};

export function getLevel(score) {
  if (score == null || Number.isNaN(score)) return 'unknown';
  if (score >= SCORE_HIGH_MIN) return 'high';
  if (score >= SCORE_MID_MIN) return 'mid';
  return 'low';
}

export function getLevelStyle(score) {
  return LEVEL_STYLE[getLevel(score)];
}

export function getLevelColor(score) {
  return getLevelStyle(score).color;
}

export function getLevelLabel(score) {
  return getLevelStyle(score).label;
}

// 점수 가이드에 보여 줄 구간 목록 (표시 기준과 같은 상수에서 만든다)
export const SCORE_GUIDE_BANDS = [
  { level: 'low', range: `0–${SCORE_MID_MIN - 1}` },
  { level: 'mid', range: `${SCORE_MID_MIN}–${SCORE_HIGH_MIN - 1}` },
  { level: 'high', range: `${SCORE_HIGH_MIN}–100` },
];
