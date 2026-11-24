import { describe, expect, it } from 'vitest';
import {
  REGION_OPTIONS, SORT_OPTIONS, formatSteepAddress, gradeLabel, sortSteepSlopes, summarizeSteep,
} from '../steepList';

const row = (id, name, grade, extra = {}) => ({ id, name, grade, latitude: 37.4, longitude: 127.1, ...extra });

describe('오르막길 구간 목록 (FlatMap 오르막길 탭 복원)', () => {
  it('지역 필터는 원본과 같은 코드 값이고 위험도 필터는 없다', () => {
    expect(REGION_OPTIONS.map((o) => o.value)).toEqual(['', '411', '41131', '41133', '41135']);
    expect(JSON.stringify([REGION_OPTIONS, SORT_OPTIONS])).not.toMatch(/위험|HIGH|MEDIUM|LOW|양호|주의/);
  });

  it('경사도(°)는 소수 첫째 자리, 측정되지 않았으면 "미측정"(0°·양호로 보이지 않게)', () => {
    expect(gradeLabel(12.34)).toBe('경사 12.3°');
    expect(gradeLabel(0)).toBe('경사 0.0°');
    expect(gradeLabel(null)).toBe('경사 미측정');
    expect(gradeLabel(undefined)).toBe('경사 미측정');
  });

  it('주소: 시군구·읍면동·지번, 없으면 지역코드', () => {
    expect(formatSteepAddress({ sigungu: '성남시 수정구', eupmyeon: '신흥동', jibun: '산 1-1' })).toBe('성남시 수정구 신흥동 산 1-1');
    expect(formatSteepAddress({ regionCode: '41131' })).toBe('지역코드 41131');
    expect(formatSteepAddress({})).toBe('');
  });

  it('요약: 전체·산지형·경사도 미측정 건수', () => {
    const items = [row(1, 'a', 10, { isMountain: true }), row(2, 'b', null), row(3, 'c', null, { isMountain: true })];
    expect(summarizeSteep(items)).toEqual({ total: 3, mountain: 2, unmeasured: 2 });
    expect(summarizeSteep([])).toEqual({ total: 0, mountain: 0, unmeasured: 0 });
  });

  it('경사 큰 순: 측정값이 큰 것부터, 미측정은 맨 뒤, 같으면 이름 순, 원본은 그대로', () => {
    const items = [row(1, '다', 5), row(2, '가', null), row(3, '나', 12), row(4, '라', 5), row(5, '마', null)];
    const sorted = sortSteepSlopes(items, 'grade');
    expect(sorted.map((i) => i.id)).toEqual([3, 1, 4, 2, 5]);
    expect(items.map((i) => i.id)).toEqual([1, 2, 3, 4, 5]);
  });

  it('이름 순', () => {
    const items = [row(1, '다', 5), row(2, '가', null), row(3, '나', 12)];
    expect(sortSteepSlopes(items, 'name').map((i) => i.name)).toEqual(['가', '나', '다']);
  });
});
