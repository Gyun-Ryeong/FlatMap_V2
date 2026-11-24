import { describe, expect, it } from 'vitest';
import {
  DEFAULT_WALK_MODE, WALK_MODES, describeWalkDetail, loadWalkMode, normalizeWalkMode, saveWalkMode, walkModeLabel,
} from '../walkMode';

function memoryStorage(initial = {}) {
  const data = { ...initial };
  return {
    data,
    getItem: (k) => (k in data ? data[k] : null),
    setItem: (k, v) => { data[k] = String(v); },
  };
}

describe('보행 유형', () => {
  it('보행약자/일반 두 가지이고 "교통약자"라는 표현을 쓰지 않는다', () => {
    expect(WALK_MODES.map((m) => m.value)).toEqual(['GENERAL', 'WEAK']);
    expect(WALK_MODES.map((m) => m.label)).toEqual(['일반', '보행약자']);
    expect(JSON.stringify(WALK_MODES)).not.toContain('교통약자');
    expect(walkModeLabel('WEAK')).toBe('보행약자');
    expect(walkModeLabel('nope')).toBe('');
  });

  it('알 수 없는 값은 기본값(일반)으로 돌린다', () => {
    expect(normalizeWalkMode('WEAK')).toBe('WEAK');
    expect(normalizeWalkMode('weak')).toBe(DEFAULT_WALK_MODE);
    expect(normalizeWalkMode(undefined)).toBe('GENERAL');
    expect(normalizeWalkMode('turbo')).toBe('GENERAL');
  });

  it('저장한 선택을 다시 읽는다', () => {
    const storage = memoryStorage();
    expect(loadWalkMode(storage)).toBe('GENERAL');
    saveWalkMode('WEAK', storage);
    expect(loadWalkMode(storage)).toBe('WEAK');
    saveWalkMode('garbage', storage);
    expect(loadWalkMode(storage)).toBe('GENERAL');
  });

  it('저장소가 없거나 예외를 던져도 기본값으로 동작한다', () => {
    const broken = {
      getItem: () => { throw new Error('blocked'); },
      setItem: () => { throw new Error('blocked'); },
    };
    expect(loadWalkMode(broken)).toBe('GENERAL');
    expect(() => saveWalkMode('WEAK', broken)).not.toThrow();
    expect(loadWalkMode(null)).toBe('GENERAL');
    expect(() => saveWalkMode('WEAK', null)).not.toThrow();
  });

  it('B축 detail 이 없거나 비율이 없으면 문구를 만들지 않는다', () => {
    expect(describeWalkDetail(undefined)).toBeNull();
    expect(describeWalkDetail({})).toBeNull();
  });

  it('B축 detail → 비율·기준·경사 문구(각도 °)', () => {
    const d = describeWalkDetail({
      mode: 'GENERAL', goodMaxDeg: 4.76, cautionMaxDeg: 7.13,
      goodPercent: 70, cautionPercent: 20, dangerPercent: 10, meanSlopeDeg: 3.3, maxSlopeDeg: 9,
    });
    expect(d.ratios).toBe('양호 70% · 주의 20% · 위험 10%');
    expect(d.criteria).toBe('일반 기준: 4.76° 이하 양호, 7.13° 이하 주의, 그보다 크면 위험');
    expect(d.slopes).toBe('평균 경사 3.3° · 최대 9°');
    expect(JSON.stringify(d)).not.toMatch(/%\)|퍼센트 경사/);
  });
});
