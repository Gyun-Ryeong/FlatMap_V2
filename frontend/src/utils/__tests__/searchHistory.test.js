import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { addSearchHistory, clearSearchHistory, getSearchHistory, removeSearchHistory } from '../searchHistory';

// node 환경에는 localStorage 가 없어 메모리 구현을 끼워 넣는다.
function installStorage(overrides = {}) {
  const data = {};
  globalThis.localStorage = {
    getItem: (k) => (k in data ? data[k] : null),
    setItem: (k, v) => { data[k] = String(v); },
    removeItem: (k) => { delete data[k]; },
    ...overrides,
  };
  return data;
}

const place = (name, lat = 37.4, lng = 127.1) => ({ name, lat, lng, address: `${name} 주소` });

describe('최근 검색 기록', () => {
  beforeEach(() => installStorage());
  afterEach(() => { delete globalThis.localStorage; });

  it('처음에는 비어 있다', () => {
    expect(getSearchHistory()).toEqual([]);
  });

  it('추가하면 맨 앞에 쌓이고, 같은 장소는 중복 없이 맨 앞으로 옮겨진다', () => {
    addSearchHistory(place('서현역', 37.38, 127.12));
    addSearchHistory(place('야탑역', 37.41, 127.13));
    const next = addSearchHistory(place('서현역', 37.38, 127.12));
    expect(next.map((h) => h.name)).toEqual(['서현역', '야탑역']);
    expect(getSearchHistory()).toHaveLength(2);
  });

  it('같은 이름이라도 좌표가 다르면 다른 장소다', () => {
    addSearchHistory(place('스타벅스', 37.38, 127.12));
    expect(addSearchHistory(place('스타벅스', 37.45, 127.15))).toHaveLength(2);
  });

  it('이름이 없는 장소는 저장하지 않는다', () => {
    expect(addSearchHistory({ lat: 37.4, lng: 127.1 })).toEqual([]);
    expect(addSearchHistory(null)).toEqual([]);
  });

  it('최대 20개까지만 남기고 오래된 것부터 버린다', () => {
    for (let i = 0; i < 25; i += 1) addSearchHistory(place(`장소${i}`, 37 + i / 100, 127));
    const list = getSearchHistory();
    expect(list).toHaveLength(20);
    expect(list[0].name).toBe('장소24');
    expect(list[19].name).toBe('장소5');
  });

  it('하나 지우기와 전체 지우기', () => {
    addSearchHistory(place('a', 37.1, 127.1));
    addSearchHistory(place('b', 37.2, 127.2));
    const id = getSearchHistory()[0].id;
    expect(removeSearchHistory(id).map((h) => h.name)).toEqual(['a']);
    expect(clearSearchHistory()).toEqual([]);
    expect(getSearchHistory()).toEqual([]);
  });

  it('저장소가 깨져 있거나 막혀 있어도 예외 없이 빈 기록으로 동작한다', () => {
    installStorage({ getItem: () => '{not json' });
    expect(getSearchHistory()).toEqual([]);

    installStorage({
      getItem: () => { throw new Error('blocked'); },
      setItem: () => { throw new Error('blocked'); },
      removeItem: () => { throw new Error('blocked'); },
    });
    expect(getSearchHistory()).toEqual([]);
    expect(() => addSearchHistory(place('x'))).not.toThrow();
    expect(() => clearSearchHistory()).not.toThrow();
  });
});
