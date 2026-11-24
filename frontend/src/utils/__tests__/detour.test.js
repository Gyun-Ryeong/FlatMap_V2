import { describe, expect, it } from 'vitest';
import { buildDetourRequest, canRequestDetour, detourOptionFor } from '../detour';

describe('우회 요청', () => {
  const route = {
    searchOptionCode: 4,
    riskSections: [
      { lat: 37.41, lng: 127.12, name: 'a', nearestRouteIdx: 12, grade: 11 },
      { lat: 37.42, lng: 127.13, name: 'b', nearestRouteIdx: 40, grade: null },
    ],
  };

  it('후보의 T Map 옵션 코드를 우회 옵션 이름으로 바꾼다', () => {
    expect(detourOptionFor(0)).toBe('SAFE');
    expect(detourOptionFor(4)).toBe('MAIN_ROAD');
    expect(detourOptionFor(10)).toBe('SHORT');
    expect(detourOptionFor(99)).toBe('SAFE');
  });

  it('경고 구간이 있는 경로만 우회 대상', () => {
    expect(canRequestDetour(route)).toBe(true);
    expect(canRequestDetour({ riskSections: [] })).toBe(false);
    expect(canRequestDetour({})).toBe(false);
    expect(canRequestDetour(null)).toBe(false);
  });

  it('요청 본문: 좌표·옵션·회피 지점(등급 필드 없음)', () => {
    const body = buildDetourRequest(route, { lat: 37.4, lng: 127.1 }, { lat: 37.45, lng: 127.15 }, 'WEAK');
    expect(body).toEqual({
      originLat: 37.4, originLng: 127.1, destLat: 37.45, destLng: 127.15,
      option: 'MAIN_ROAD',
      walkMode: 'WEAK',
      avoidPoints: [
        { lat: 37.41, lng: 127.12, nearestRouteIdx: 12 },
        { lat: 37.42, lng: 127.13, nearestRouteIdx: 40 },
      ],
    });
    expect(JSON.stringify(body)).not.toMatch(/riskLevel/);
  });
});
