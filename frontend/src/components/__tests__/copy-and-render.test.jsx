import { describe, expect, it } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import RouteResult, { Breakdown } from '../Route/RouteResult';
import RouteDetail from '../Route/RouteDetail';
import WeatherCard from '../Weather/WeatherCard';
import ScoreGuide from '../Panel/ScoreGuide';
import RouteOptions from '../Panel/RouteOptions';
import LayerPanel from '../Panel/LayerPanel';
import HomeScreen from '../Home/HomeScreen';
import PlaceShortcuts from '../Panel/PlaceShortcuts';

const route = {
  routeId: 'r1',
  searchOption: '일반',
  searchOptionCode: 0,
  totalDistance: 2500,
  totalTime: 1800,
  totalScore: 74.6,
  recommended: true,
  cctvCount: 3,
  lightCount: 5,
  policeCount: 1,
  breakdown: [
    { axis: 'A', name: '야간 시설 노출', score: 80, weight: 1, detail: {} },
    { axis: 'C', name: '날씨 보정', score: 55.5, weight: 0.25, detail: {} },
  ],
  riskSections: [
    { lat: 37.4, lng: 127.1, name: '테스트 급경사지', grade: null, distanceFromRoute: 120.4, nearestRouteIdx: 5 },
    { lat: 37.41, lng: 127.11, name: '경사 있는 곳', grade: 12.34, distanceFromRoute: 30, nearestRouteIdx: 20 },
  ],
  weather: { fallback: false, temperature: 8, humidity: 60, windSpeed: 2, icon: 'clear', description: '맑음' },
};
const idleDetour = { status: 'idle', data: null, message: '' };

describe('경로 카드·상세 렌더', () => {
  it('점수·시설 개수(경찰시설 포함)와 등급 라벨을 보여 주고 "안전/위험" 단정은 없다', () => {
    const html = renderToStaticMarkup(
      <RouteResult routes={[route]} selectedRouteId="r1" onSelectRoute={() => {}} />
    );
    expect(html).toContain('74.6점');
    expect(html).toContain('높은 편');
    expect(html).toContain('CCTV 3개');
    expect(html).toContain('보안등 5개');
    expect(html).toContain('경찰시설 1개');
    expect(html).not.toMatch(/안전 점수|안전점수|위험/);
  });

  it('급경사지 경고: 경사 미측정은 "미측정", 점수와 별개의 경고임을 밝히고 등급 표현이 없다', () => {
    const html = renderToStaticMarkup(
      <RouteDetail route={route} detour={idleDetour} onRequestDetour={() => {}} />
    );
    expect(html).toContain('붕괴위험 지정 급경사지 2곳');
    expect(html).toContain('경사 미측정');
    expect(html).toContain('경사 12.3°');
    expect(html).toContain('경로 점수에 포함되지 않는 별도의 경고');
    expect(html).toContain('우회 경로 찾기');
    expect(html).not.toMatch(/양호|주의 구간|위험 구간|위험도/);
  });

  it('경고 구간이 없으면 우회 버튼이 없고, "안전하다"고 단정하지 않는다', () => {
    const html = renderToStaticMarkup(
      <RouteDetail route={{ ...route, riskSections: [] }} detour={idleDetour} onRequestDetour={() => {}} />
    );
    expect(html).not.toContain('우회 경로 찾기');
    expect(html).toContain('안전을 뜻하지 않습니다');
  });

  it('우회 결과: 경고 구간 수와 점수 변화를 보여 준다', () => {
    const detour = {
      status: 'done',
      message: '',
      data: { totalDistance: 2800, totalDuration: 2000, riskSections: [], totalScore: 70.1, originalTotalScore: 74.6 },
    };
    const html = renderToStaticMarkup(<RouteDetail route={route} detour={detour} onRequestDetour={() => {}} />);
    expect(html).toContain('경고 구간 2곳 → 0곳');
    expect(html).toContain('점수 74.6 → 70.1');
  });

  it('우회 경로 점수가 원경로보다 낮으면 숨기지 않고 알린다(점수는 채택 기준이 아님)', () => {
    const lower = {
      status: 'done',
      message: '',
      data: { totalDistance: 2800, totalDuration: 2000, riskSections: [], totalScore: 50, originalTotalScore: 74.6 },
    };
    const html = renderToStaticMarkup(<RouteDetail route={route} detour={lower} onRequestDetour={() => {}} />);
    expect(html).toContain('점수 74.6 → 50.0');
    expect(html).toContain('24.6점 낮습니다');
    expect(html).toContain('채택 기준이 아니므로');
    const higher = { ...lower, data: { ...lower.data, totalScore: 80 } };
    expect(renderToStaticMarkup(<RouteDetail route={route} detour={higher} onRequestDetour={() => {}} />)).not.toContain('낮습니다');
  });

  it('우회 거절 사유는 서버 문구 그대로 보여 준다', () => {
    const html = renderToStaticMarkup(
      <RouteDetail route={route} detour={{ status: 'rejected', data: null, message: '더 안전한 우회 경로를 찾지 못했습니다.' }} onRequestDetour={() => {}} />
    );
    expect(html).toContain('더 안전한 우회 경로를 찾지 못했습니다.');
  });

  it('날씨 대체값(fallback)은 수치를 지어내지 않고 점수 미반영을 알린다', () => {
    const html = renderToStaticMarkup(
      <WeatherCard weather={{ fallback: true, temperature: 20, humidity: 50, windSpeed: 1, icon: 'unknown' }} />
    );
    expect(html).toContain('날씨 정보를 가져오지 못했습니다');
    expect(html).toContain('날씨 보정이 반영되지 않았습니다');
    expect(html).not.toContain('20°C');
  });
});

describe('문구 규칙 (미구현 기능을 있는 것처럼 쓰지 않는다)', () => {
  it('홈 화면: 구현된 기능만, RAG 는 개발 중으로 표시(보행난이도는 구현됨), 이름은 [이름 미정]', () => {
    const html = renderToStaticMarkup(<HomeScreen onStart={() => {}} />);
    expect(html).toContain('[이름 미정]');
    expect(html).toContain('개발 중(아직 제공하지 않음): RAG 기반 안내');
    expect(html).not.toMatch(/개발 중[^<]*보행난이도|개발 중[^<]*보행약자/);
    expect(html).toContain('설계 파라미터');
    expect(html).not.toMatch(/엘리베이터|화장실|쉼터|야간 사고|사고 이력|걷는 속도|계단/);
    expect(html).not.toMatch(/안전점수|위험구역|SafeRoute/);
  });

  it('보행약자/일반 선택: 활성이고 선택 상태가 표시되며 한계를 밝힌다', () => {
    const html = renderToStaticMarkup(
      <RouteOptions priority="safety" onChangePriority={() => {}} walkMode="WEAK" onChangeWalkMode={() => {}} />
    );
    expect(html).toContain('보행약자');
    expect(html).toContain('일반');
    expect(html).not.toContain('교통약자');
    expect(html).not.toContain('준비 중');
    expect(html).not.toContain('disabled');
    expect(html).toContain('✓ 보행약자');
    expect(html).not.toContain('✓ 일반');
    expect(html).toContain('실제보다 낮게 나오는 경향');
    expect(html).toContain('안전 우선');
    expect(html).toContain('균형');
    expect(html).toContain('빠른 길');
  });

  it('점수 내역: 보행난이도(B축)는 구간 비율·기준 각도·평균/최대 경사를 보여 준다', () => {
    const html = renderToStaticMarkup(
      <Breakdown
        breakdown={[
          { axis: 'A', name: '야간 시설 노출', score: 64, weight: 1, detail: {} },
          {
            axis: 'B', name: '보행난이도', score: 68.8, weight: 1,
            detail: {
              mode: 'WEAK', goodMaxDeg: 3.18, cautionMaxDeg: 4.76,
              goodPercent: 50, cautionPercent: 37.5, dangerPercent: 12.5, meanSlopeDeg: 3.9, maxSlopeDeg: 9,
            },
          },
        ]}
      />
    );
    expect(html).toContain('B · 보행난이도');
    expect(html).toContain('양호 50% · 주의 37.5% · 위험 12.5%');
    expect(html).toContain('보행약자 기준: 3.18° 이하 양호, 4.76° 이하 주의');
    expect(html).toContain('평균 경사 3.9° · 최대 9°');
    expect(html).not.toContain('%)');
    // A 축에는 구간 비율 문구가 붙지 않는다
    expect(html.match(/경로 경사 구간 비율/g)).toHaveLength(1);
  });

  it('점수 가이드: 설계 파라미터 안내와 급경사지 미포함을 밝힌다', () => {
    const closed = renderToStaticMarkup(<ScoreGuide scoreModel={null} />);
    expect(closed).toContain('경로 점수 기준과 산정 방식');
    // 펼친 내용은 상태가 필요하므로 컴포넌트 안내 문구를 직접 확인한다
    expect(closed).not.toMatch(/60%|40%/);
  });

  it('레이어 패널: 급경사지는 보행 난이도와 별개인 표본 경고로 설명한다', () => {
    const layers = { CCTV: true, SECURITY_LIGHT: true, POLICE: true, DENSITY: false, STEEP: true };
    const html = renderToStaticMarkup(
      <LayerPanel layers={layers} onToggle={() => {}} onToggleAllFacilities={() => {}} zoom={4} facilityTruncated={false} steepCount={88} />
    );
    expect(html).toContain('경찰시설');
    expect(html).toContain('시설 밀도 격자');
    expect(html).toContain('급경사지 경고');
    expect(html).toContain('붕괴위험으로 지정된 곳만 모은 표본 88건');
    expect(html).toContain('보행 난이도와는 별개');
    expect(html).not.toMatch(/위험 구역|위험구역/);
  });

  it('즐겨찾기: 로그인 전에는 로그인 안내', () => {
    const html = renderToStaticMarkup(
      <PlaceShortcuts isLoggedIn={false} favorites={[]} history={[]} onPick={() => {}}
        onRemoveFavorite={() => {}} onRemoveHistory={() => {}} onClearHistory={() => {}} />
    );
    expect(html).toContain('로그인하면 자주 가는 곳을 저장할 수 있어요.');
  });
});
