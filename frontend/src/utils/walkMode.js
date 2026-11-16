// 보행 유형(보행약자 / 일반). 서버가 보행난이도(B축) 임계값을 유형별로 다르게 적용한다.
// 임계값 숫자는 서버 설정이 원본이라 여기에 적지 않는다 — 경로 응답의 scoreModel.walkThresholds 를 화면에 보여 준다.
// UI 용어는 "교통약자"가 아니라 "보행약자"다.

export const WALK_MODES = [
  { value: 'GENERAL', label: '일반', description: '일반 보행자 기준' },
  { value: 'WEAK', label: '보행약자', description: '더 엄격한 경사 기준' },
];

export const DEFAULT_WALK_MODE = 'GENERAL';

const STORAGE_KEY = 'flatmap_walk_mode';

export function normalizeWalkMode(value) {
  return WALK_MODES.some((m) => m.value === value) ? value : DEFAULT_WALK_MODE;
}

export function walkModeLabel(value) {
  return WALK_MODES.find((m) => m.value === value)?.label ?? '';
}

// 저장소는 비공개 창·차단 설정에서 없거나 예외를 던질 수 있다 — 항상 기본값으로 돌아간다.
function defaultStorage() {
  try {
    return globalThis.localStorage ?? null;
  } catch {
    return null;
  }
}

export function loadWalkMode(storage = defaultStorage()) {
  try {
    return normalizeWalkMode(storage?.getItem(STORAGE_KEY));
  } catch {
    return DEFAULT_WALK_MODE;
  }
}

export function saveWalkMode(value, storage = defaultStorage()) {
  try {
    storage?.setItem(STORAGE_KEY, normalizeWalkMode(value));
  } catch {
    /* 저장 실패는 무시한다 — 선택은 이번 화면에서만 유지된다 */
  }
}

// B축 breakdown.detail → 화면 문구. 비율은 서버가 합이 100% 가 되게 내려 준다. 경사 단위는 각도(°).
export function describeWalkDetail(detail) {
  if (!detail || detail.goodPercent == null) return null;
  return {
    ratios: `양호 ${detail.goodPercent}% · 주의 ${detail.cautionPercent}% · 위험 ${detail.dangerPercent}%`,
    criteria: `${walkModeLabel(detail.mode)} 기준: ${detail.goodMaxDeg}° 이하 양호, ${detail.cautionMaxDeg}° 이하 주의, 그보다 크면 위험`,
    slopes: `평균 경사 ${detail.meanSlopeDeg}° · 최대 ${detail.maxSlopeDeg}°`,
  };
}
