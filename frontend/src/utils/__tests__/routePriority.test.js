import { describe, expect, it } from 'vitest';
import { pickRouteByPriority, PRIORITY_OPTIONS } from '../routePriority';

const route = (routeId, totalScore, totalTime, recommended = false) => ({ routeId, totalScore, totalTime, recommended });

const withWalk = (routeId, walkScore, totalScore, totalTime, code = 0) => ({
  routeId, totalScore, totalTime, searchOptionCode: code,
  breakdown: [{ axis: 'A', score: totalScore }, ...(walkScore == null ? [] : [{ axis: 'B', score: walkScore }])],
});

describe('오르막 최소(FlatMap FLAT 옵션 복원)', () => {
  it('선택지에 FlatMap 이름 그대로 "오르막 최소"가 있다', () => {
    expect(PRIORITY_OPTIONS.map((o) => o.label)).toEqual(['안전 우선', '균형', '빠른 길', '오르막 최소']);
  });

  it('보행난이도(B축) 점수가 가장 높은 경로를 고른다(총점이 아니라)', () => {
    const routes = [withWalk('a', 40, 90, 1000), withWalk('b', 85, 50, 1500), withWalk('c', 60, 70, 900, 30)];
    expect(pickRouteByPriority(routes, 'flat').routeId).toBe('b');
    expect(pickRouteByPriority(routes, 'safety').routeId).toBe('a');   // 안전 우선은 총점 기준 그대로
  });

  it('B 점수가 같으면 총점, 그다음 시간이 짧은 쪽', () => {
    expect(pickRouteByPriority([withWalk('a', 70, 50, 900), withWalk('b', 70, 60, 1200)], 'flat').routeId).toBe('b');
    expect(pickRouteByPriority([withWalk('a', 70, 60, 1200), withWalk('b', 70, 60, 900)], 'flat').routeId).toBe('b');
  });

  it('B축이 없는 경로는 후보에서 빼고, 모두 없으면 계단 제외(30) 경로 → 추천 경로 순으로 대신한다', () => {
    const partial = [withWalk('a', null, 95, 900), withWalk('b', 30, 40, 1500)];
    expect(pickRouteByPriority(partial, 'flat').routeId).toBe('b');

    const none = [withWalk('a', null, 95, 900), withWalk('b', null, 40, 1500, 30), withWalk('c', null, 60, 1000)];
    expect(pickRouteByPriority(none, 'flat').routeId).toBe('b');

    const noStairs = [withWalk('a', null, 60, 900), { ...withWalk('b', null, 40, 1500), recommended: true }];
    expect(pickRouteByPriority(noStairs, 'flat').routeId).toBe('b');
  });
});

describe('pickRouteByPriority', () => {
  const routes = [route('a', 60, 1200), route('b', 80, 1800, true), route('c', 40, 900)];

  it('후보가 없으면 null', () => {
    expect(pickRouteByPriority([], 'safety')).toBeNull();
    expect(pickRouteByPriority(null, 'safety')).toBeNull();
  });

  it('안전 우선: 서버가 추천으로 표시한 경로', () => {
    expect(pickRouteByPriority(routes, 'safety').routeId).toBe('b');
  });

  it('안전 우선: 추천 표시가 없으면 총점이 가장 높은 경로', () => {
    const noFlag = [route('a', 60, 1200), route('b', 85, 1800), route('c', 40, 900)];
    expect(pickRouteByPriority(noFlag, 'safety').routeId).toBe('b');
  });

  it('빠른 길: 소요 시간이 가장 짧은 경로', () => {
    expect(pickRouteByPriority(routes, 'speed').routeId).toBe('c');
  });

  it('균형: 점수와 시간을 같은 비중으로 본다', () => {
    // 시간 범위 900~1800(span 900): a = 0.6*0.5 + (1-300/900)*0.5 ≈ 0.633, b = 0.4, c = 0.2 + 0.5 = 0.7
    expect(pickRouteByPriority(routes, 'balanced').routeId).toBe('c');

    // 점수 차이가 시간 차이를 이기는 경우: a = 0.45+0.5 = 0.95, b = 0.1 + 0 = 0.1... 점수 90 vs 20, 시간 1000 vs 1100
    const scoreWins = [route('a', 90, 1000), route('b', 20, 1100)];
    expect(pickRouteByPriority(scoreWins, 'balanced').routeId).toBe('a');
    // 시간 차이가 점수 차이를 이기는 경우: 점수 50 vs 60, 시간 600 vs 1800 → a = 0.25+0.5, b = 0.3
    const timeWins = [route('a', 50, 600), route('b', 60, 1800)];
    expect(pickRouteByPriority(timeWins, 'balanced').routeId).toBe('a');
  });

  it('균형: 모든 경로의 시간이 같아도 0 으로 나누지 않고 점수가 높은 쪽을 고른다', () => {
    const same = [route('a', 50, 1000), route('b', 70, 1000)];
    expect(pickRouteByPriority(same, 'balanced').routeId).toBe('b');
  });

  it('점수가 없는(null) 경로는 0점으로 취급한다', () => {
    const missing = [route('a', null, 1000), route('b', 10, 1100)];
    expect(pickRouteByPriority(missing, 'safety').routeId).toBe('b');
  });

  it('경로 하나뿐이면 어떤 기준이든 그 경로', () => {
    const one = [route('only', 50, 1000)];
    PRIORITY_OPTIONS.forEach((o) => expect(pickRouteByPriority(one, o.value).routeId).toBe('only'));
  });

  it('입력 배열을 바꾸지 않는다', () => {
    const before = routes.map((r) => r.routeId);
    pickRouteByPriority(routes, 'speed');
    expect(routes.map((r) => r.routeId)).toEqual(before);
  });

  it('선택지는 안전 우선 / 균형 / 빠른 길 / 오르막 최소 네 개', () => {
    expect(PRIORITY_OPTIONS.map((o) => o.label)).toEqual(['안전 우선', '균형', '빠른 길', '오르막 최소']);
  });
});
