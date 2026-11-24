import { describe, expect, it } from 'vitest';
import { searchPlaces, toPlace } from '../kakaoPlaces';

describe('카카오 장소 검색 결과 변환', () => {
  it('좌표는 숫자로, 주소는 도로명 우선', () => {
    expect(toPlace({
      place_name: '서현역', x: '127.1234', y: '37.3845', road_address_name: '도로명', address_name: '지번',
    })).toEqual({ name: '서현역', lat: 37.3845, lng: 127.1234, address: '도로명' });
    expect(toPlace({ place_name: 'a', x: '1', y: '2', address_name: '지번' }).address).toBe('지번');
    expect(toPlace({ place_name: 'a', x: '1', y: '2' }).address).toBe('');
  });

  it('SDK 가 없으면 빈 배열로 끝난다 (예외를 던지지 않는다)', async () => {
    expect(await searchPlaces('서현')).toEqual([]);
    expect(await searchPlaces('   ')).toEqual([]);
  });
});
