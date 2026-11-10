import { describe, expect, it } from 'vitest';
import { DENSITY_LABELS, findCellAt, isLowDensity } from '../densityCells';

const cell = (id, minLat, minLng, level) => ({
  id, minLat, minLng, maxLat: minLat + 0.01, maxLng: minLng + 0.01, densityLevel: level,
});

describe('시설 밀도 격자 셀', () => {
  const cells = [cell('a', 37.40, 127.10, 'LOW_DENSITY'), cell('b', 37.41, 127.10, 'HIGH_DENSITY')];

  it('좌표가 속한 셀을 찾는다', () => {
    expect(findCellAt(cells, 37.405, 127.105).id).toBe('a');
    expect(findCellAt(cells, 37.415, 127.105).id).toBe('b');
  });

  it('경계는 반개구간: 하한은 포함, 상한은 다음 셀', () => {
    expect(findCellAt(cells, 37.40, 127.10).id).toBe('a');
    expect(findCellAt(cells, 37.41, 127.10).id).toBe('b');
  });

  it('셀이 없는 곳은 null', () => {
    expect(findCellAt(cells, 37.50, 127.10)).toBeNull();
    expect(findCellAt([], 37.4, 127.1)).toBeNull();
    expect(findCellAt(null, 37.4, 127.1)).toBeNull();
  });

  it('시설이 적은 셀만 안내 대상', () => {
    expect(isLowDensity(cells[0])).toBe(true);
    expect(isLowDensity(cells[1])).toBe(false);
    expect(isLowDensity(null)).toBe(false);
  });

  it('밀도 등급 표기에 "위험" 표현이 없다', () => {
    Object.values(DENSITY_LABELS).forEach((label) => expect(label).not.toMatch(/위험/));
    expect(Object.keys(DENSITY_LABELS)).toEqual(['LOW_DENSITY', 'MEDIUM', 'HIGH_DENSITY']);
  });
});
