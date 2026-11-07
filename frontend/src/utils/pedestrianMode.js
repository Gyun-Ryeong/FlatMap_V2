import { useEffect, useState } from 'react';

/**
 * 보행자 유형 설정.
 *
 * 기본은 일반 보행자이며, 보행약자(휠체어·유아차·고령자) 모드를 켜면
 * 더 낮은 경사도부터 위험으로 분류한다. 판정 기준은 백엔드가 갖고 있고,
 * 프론트는 선택값만 보관해 요청에 실어 보낸다.
 */
const KEY = 'flatmap_pedestrian_mode';
const EVENT = 'flatmap-pedestrian-mode-change';

export const GENERAL = 'GENERAL';
export const WEAK = 'WEAK';

export function getPedestrianMode() {
  try {
    return localStorage.getItem(KEY) === WEAK ? WEAK : GENERAL;
  } catch {
    return GENERAL;
  }
}

export function setPedestrianMode(mode) {
  const next = mode === WEAK ? WEAK : GENERAL;
  try {
    localStorage.setItem(KEY, next);
  } catch {
    /* 저장에 실패해도 이번 세션 동안은 이벤트로 동작시킨다 */
  }
  window.dispatchEvent(new CustomEvent(EVENT, { detail: next }));
  return next;
}

/** 현재 모드와 토글 함수를 반환한다. 다른 컴포넌트가 바꾸면 함께 갱신된다. */
export function usePedestrianMode() {
  const [mode, setMode] = useState(getPedestrianMode);

  useEffect(() => {
    const onChange = () => setMode(getPedestrianMode());
    window.addEventListener(EVENT, onChange);
    window.addEventListener('storage', onChange);
    return () => {
      window.removeEventListener(EVENT, onChange);
      window.removeEventListener('storage', onChange);
    };
  }, []);

  return [mode, (m) => setPedestrianMode(m)];
}

export const isWeak = (mode) => mode === WEAK;

/** 화면 표기용 기준 설명 */
export const MODE_LABEL = {
  [GENERAL]: '일반 보행자',
  [WEAK]: '보행약자',
};
export const MODE_CRITERIA = {
  [GENERAL]: '위험 20° 이상 · 주의 10° 이상',
  [WEAK]: '위험 10° 이상 · 주의 5° 이상',
};
