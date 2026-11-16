import { describe, expect, it } from 'vitest';
import {
  ZONE_KEYS, ZONE_LAYERS, maxMarkersForLevel, visibleZoneItems, zoneInfoLines, zoneMetaText,
} from '../zoneLayers';

describe('FlatMap 안전정보 레이어 설정', () => {
  it('원본 FlatMap 의 다섯 레이어(이름·색·엔드포인트)를 그대로 가진다', () => {
    expect(ZONE_LAYERS.map((l) => l.label)).toEqual(
      ['사고다발지', '교통약자 보호구역', '장애인복지시설', '그늘막', '노인복지관']);
    expect(ZONE_LAYERS.map((l) => l.color)).toEqual(['#F44336', '#4CAF50', '#2196F3', '#FF9800', '#9C27B0']);
    expect(ZONE_LAYERS.map((l) => l.endpoint)).toEqual([
      '/safety/accident-zones', '/safety/protected-zones', '/safety/welfare-facilities',
      '/safety/shade-shelters', '/safety/senior-centers',
    ]);
    expect(ZONE_KEYS).toEqual(['ACCIDENT', 'PROTECTED', 'WELFARE', 'SHELTER', 'SENIOR']);
  });

  it('위험도·등급 표현을 쓰지 않는다("교통약자 보호구역"은 법정 명칭)', () => {
    const text = JSON.stringify(ZONE_LAYERS.map(({ label, meta }) => [label, meta]));
    expect(text).not.toMatch(/위험|안전 구간|양호|주의 구간/);
  });
});

describe('줌에 따른 마커 수 상한(원본과 같은 구간)', () => {
  it('레벨별 상한', () => {
    expect(maxMarkersForLevel(1)).toBe(Infinity);
    expect(maxMarkersForLevel(3)).toBe(Infinity);
    expect(maxMarkersForLevel(4)).toBe(500);
    expect(maxMarkersForLevel(5)).toBe(500);
    expect(maxMarkersForLevel(6)).toBe(300);
    expect(maxMarkersForLevel(7)).toBe(100);
    expect(maxMarkersForLevel(8)).toBe(30);
    expect(maxMarkersForLevel(14)).toBe(30);
    expect(maxMarkersForLevel(null)).toBe(200);
  });
});

describe('visibleZoneItems', () => {
  const at = (i, lat, lng) => ({ id: i, name: `n${i}`, latitude: lat, longitude: lng });
  const bounds = { lat1: 37.40, lng1: 127.10, lat2: 37.45, lng2: 127.15 };

  it('좌표가 없는 항목은 그리지 않는다', () => {
    const items = [at(1, 37.41, 127.11), { id: 2, name: 'x', latitude: null, longitude: 127.1 }, { id: 3, name: 'y' }];
    expect(visibleZoneItems(items, bounds, 3).map((i) => i.id)).toEqual([1]);
    expect(visibleZoneItems(undefined, bounds, 3)).toEqual([]);
  });

  it('현재 지도 영역 안의 항목만(경계 포함)', () => {
    const items = [at(1, 37.41, 127.11), at(2, 37.50, 127.11), at(3, 37.41, 127.20), at(4, 37.40, 127.10)];
    expect(visibleZoneItems(items, bounds, 3).map((i) => i.id)).toEqual([1, 4]);
  });

  it('상한은 화면 안 항목에만 센다(화면 밖 항목이 자리를 차지하지 않는다)', () => {
    const outside = Array.from({ length: 40 }, (_, k) => at(`o${k}`, 37.9, 127.9));
    const inside = Array.from({ length: 40 }, (_, k) => at(`i${k}`, 37.41, 127.11));
    const shown = visibleZoneItems([...outside, ...inside], bounds, 8);   // 상한 30
    expect(shown).toHaveLength(30);
    expect(shown.every((i) => String(i.id).startsWith('i'))).toBe(true);
  });

  it('충분히 확대하면 상한이 없다, 영역을 모르면 영역 필터 없이 상한만', () => {
    const many = Array.from({ length: 700 }, (_, k) => at(k, 37.41, 127.11));
    expect(visibleZoneItems(many, bounds, 2)).toHaveLength(700);
    expect(visibleZoneItems(many, null, 6)).toHaveLength(300);
  });
});

describe('정보 창·타일 문구', () => {
  it('주소·전화·유형만 있는 줄을 만든다', () => {
    expect(zoneInfoLines({ name: 'a', address: '성남시 수정구', phone: '031-1', type: '어린이' }))
      .toEqual(['성남시 수정구', '전화: 031-1', '유형: 어린이']);
    expect(zoneInfoLines({ name: 'a' })).toEqual([]);
  });

  it('타일 보조 문구: 숨김/불러오는 중/실패/없음/개수', () => {
    const layer = ZONE_LAYERS[0];
    expect(zoneMetaText(layer, false, 5)).toBe('현재 숨김');
    expect(zoneMetaText(layer, true, undefined)).toBe('불러오는 중');
    expect(zoneMetaText(layer, true, null)).toBe('불러오지 못했습니다');
    expect(zoneMetaText(layer, true, 0)).toBe('등록된 데이터 없음');
    expect(zoneMetaText(layer, true, 12)).toBe(`12곳 · ${layer.meta}`);
  });
});
