import { describe, expect, it } from 'vitest';
import { buildWarningRanges, splitPathByWarnings, WARNING_SPREAD } from '../warningRanges';

describe('buildWarningRanges', () => {
  it('경고가 없으면 빈 배열', () => {
    expect(buildWarningRanges([], 100)).toEqual([]);
    expect(buildWarningRanges(null, 100)).toEqual([]);
  });

  it('nearestRouteIdx 앞뒤 SPREAD 개를 경고 구간으로 본다', () => {
    expect(buildWarningRanges([{ nearestRouteIdx: 30 }], 100)).toEqual([
      { start: 30 - WARNING_SPREAD, end: 30 + WARNING_SPREAD },
    ]);
  });

  it('경로 양 끝을 넘지 않는다', () => {
    expect(buildWarningRanges([{ nearestRouteIdx: 2 }, { nearestRouteIdx: 98 }], 100)).toEqual([
      { start: 0, end: 2 + WARNING_SPREAD },
      { start: 98 - WARNING_SPREAD, end: 99 },
    ]);
  });

  it('겹치거나 맞닿은 구간은 합치고 순서와 무관하게 정렬한다', () => {
    const merged = buildWarningRanges(
      [{ nearestRouteIdx: 40 }, { nearestRouteIdx: 20 }, { nearestRouteIdx: 28 }], 100);
    expect(merged).toEqual([{ start: 12, end: 48 }]);
  });

  it('인덱스가 없는 항목은 무시한다', () => {
    expect(buildWarningRanges([{ name: 'x' }], 100)).toEqual([]);
  });
});

describe('splitPathByWarnings', () => {
  const path = Array.from({ length: 30 }, (_, i) => i);

  it('경고 구간이 없으면 한 조각', () => {
    expect(splitPathByWarnings(path, [])).toEqual([{ warning: false, path }]);
  });

  it('경고 앞뒤를 일반 구간으로 나누고 끝점을 공유한다', () => {
    const pieces = splitPathByWarnings(path, [{ start: 10, end: 20 }]);
    expect(pieces.map((p) => p.warning)).toEqual([false, true, false]);
    expect(pieces[0].path.at(-1)).toBe(pieces[1].path[0]);
    expect(pieces[1].path.at(-1)).toBe(pieces[2].path[0]);
    expect(pieces[0].path[0]).toBe(0);
    expect(pieces[2].path.at(-1)).toBe(29);
  });

  it('경로 전체가 경고 구간이면 일반 조각이 없다', () => {
    const pieces = splitPathByWarnings(path, [{ start: 0, end: 29 }]);
    expect(pieces).toHaveLength(1);
    expect(pieces[0].warning).toBe(true);
  });
});
