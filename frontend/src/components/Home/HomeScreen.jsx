import { useState, useRef, useEffect } from 'react';
import KakaoMap from '../KakaoMap';
import { SERVICE_NAME, SERVICE_TAGLINE } from '../../config/branding';

// 구현된 기능만 적는다. (RAG 기반 안내는 개발 중이라 단계 설명에 넣지 않는다.)
const STEPS = [
  { number: '1', title: '장소 입력', text: '출발지와 목적지를 검색하거나 지도에서 직접 고릅니다.', detail: '현재 위치를 출발지로 바로 사용할 수 있어요.' },
  { number: '2', title: '이동 기준 선택', text: '안전 우선, 균형, 빠른 길, 오르막 최소 중에서 고르고, 보행 유형(일반 / 보행약자)을 정합니다.', detail: '후보 경로는 서버가 4종류를 한 번에 계산해 줍니다.' },
  { number: '3', title: '경로 점수 비교', text: '야간 시설(CCTV·보안등·경찰시설), 경로 경사(보행난이도), 날씨를 반영한 점수로 비교합니다.', detail: '점수는 설계 파라미터 기반의 상대 비교 값이며, 축별 내역을 함께 보여 줍니다.' },
  { number: '4', title: '경고 확인과 우회', text: '경로 주변의 붕괴위험 지정 급경사지를 지도에서 확인합니다.', detail: '경고 구간이 있으면 피해 가는 우회 경로를 요청할 수 있어요.' },
];

// 홈 화면 미리보기용 예시 경로 (서현역 일대, 실제 경로 데이터가 아님)
const PREVIEW_CENTER = [37.3819, 127.1264]; // 경로 중앙이 하단 카드에 가리지 않도록 살짝 남쪽을 중심으로 둔다
const PREVIEW_PATH = [
  [37.3812, 127.1289], [37.3825, 127.1276], [37.3839, 127.1261], [37.3853, 127.1246], [37.3867, 127.1231],
];

function pointElement(bg, label) {
  const wrap = document.createElement('div');
  wrap.style.cssText = 'display:flex;flex-direction:column;align-items:center;gap:2px';
  const tag = document.createElement('div');
  tag.textContent = label;
  tag.style.cssText = `background:${bg};color:white;font-size:10px;font-weight:700;padding:3px 8px;border-radius:6px;white-space:nowrap;box-shadow:0 2px 8px rgba(0,0,0,0.25)`;
  const stem = document.createElement('div');
  stem.style.cssText = `width:2px;height:5px;background:${bg}`;
  const dot = document.createElement('div');
  dot.style.cssText = `width:6px;height:6px;border-radius:50%;background:${bg}`;
  wrap.append(tag, stem, dot);
  return wrap;
}

function HomeMapPreview() {
  const [map, setMap] = useState(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    const kakao = window.kakao;
    if (!map || !kakao) return undefined;

    const toLatLng = ([lat, lng]) => new kakao.maps.LatLng(lat, lng);
    const items = [];

    items.push(
      new kakao.maps.Polyline({
        path: PREVIEW_PATH.map(toLatLng),
        strokeWeight: 7,
        strokeColor: '#2d6aa3',
        strokeOpacity: 0.9,
        strokeStyle: 'solid',
        map,
      })
    );

    [
      [PREVIEW_PATH[0], pointElement('#5a7ab8', '출발')],
      [PREVIEW_PATH[PREVIEW_PATH.length - 1], pointElement('#b84c4c', '도착')],
    ].forEach(([pos, content]) => {
      items.push(
        new kakao.maps.CustomOverlay({
          position: toLatLng(pos),
          content,
          map,
          xAnchor: 0.5,
          yAnchor: 1,
          zIndex: 4,
        })
      );
    });

    return () => items.forEach((item) => item.setMap(null));
  }, [map]);

  return (
    <div className="home-kakao-preview">
      <div className="home-kakao-map">
        <KakaoMap center={PREVIEW_CENTER} level={5} interactive={false} onMapReady={setMap} onError={() => setFailed(true)} />
      </div>
      {!map && (
        <div className="home-kakao-loading">
          {failed ? '지도를 불러오지 못했습니다' : '카카오맵을 불러오는 중입니다'}
        </div>
      )}
    </div>
  );
}

export default function HomeScreen({ onStart }) {
  const [activeStep, setActiveStep] = useState(0);
  const touchStartX = useRef(null);

  function moveStep(direction) {
    setActiveStep((current) => Math.min(STEPS.length - 1, Math.max(0, current + direction)));
  }

  return (
    <main className="home-screen">
      <div className="home-orb home-orb-one" />
      <div className="home-orb home-orb-two" />

      <header className="home-header">
        <div className="home-brand">
          <div className="home-brand-mark">
            <svg width={23} height={23} viewBox="0 0 14 14" fill="none" aria-hidden="true">
              <path d="M7 1C4.8 1 3 2.8 3 5C3 8 7 13 7 13S11 8 11 5C11 2.8 9.2 1 7 1Z" fill="white" />
              <circle cx={7} cy={5} r={1.5} fill="#3f7655" />
            </svg>
          </div>
          <span>{SERVICE_NAME}</span>
        </div>
        <span className="home-header-note">{SERVICE_TAGLINE}</span>
      </header>

      <section className="home-hero">
        <div className="home-copy">
          <div className="home-eyebrow"><span />성남시 CCTV·보안등·경찰시설 데이터 기반</div>
          <h1>집으로 가는 길,<br /><strong>시설 정보를 보고 고르세요</strong></h1>
          <p>
            후보 경로를 야간 안전시설과 날씨로 점수화해 비교하고,
            경로 주변의 붕괴위험 지정 급경사지를 지도에서 확인할 수 있습니다.
          </p>
          <button className="home-start-button" onClick={onStart}>
            길 찾기 시작
            <span aria-hidden="true">→</span>
          </button>
          <div className="home-assurance">
            <span className="home-check">i</span>
            점수는 검증되지 않은 설계 파라미터 기반의 상대 비교 값이며, 실제 안전을 보장하지 않습니다
          </div>
        </div>

        <div className="home-preview" aria-label="서비스 화면 미리보기">
          <HomeMapPreview />
          <div className="home-preview-status">
            <span />
            카카오맵 기반 경로 미리보기
          </div>
          <div className="preview-card">
            <div>
              <span className="preview-label">예시 화면 (실제 경로·점수가 아님)</span>
              <strong>추천 경로 · 34분</strong>
            </div>
            <span className="preview-score"><b>72점</b> 높은 편</span>
          </div>
        </div>
      </section>

      <section className="home-how">
        <div className="home-section-heading">
          <span>서비스 이용 방법</span>
          <h2>이렇게 작동해요</h2>
          <p>네 단계만 거치면 후보 경로를 점수와 함께 비교할 수 있습니다.</p>
        </div>

        <div
          className="home-steps"
          onTouchStart={(event) => { touchStartX.current = event.touches[0].clientX; }}
          onTouchEnd={(event) => {
            if (touchStartX.current === null) return;
            const distance = event.changedTouches[0].clientX - touchStartX.current;
            if (Math.abs(distance) > 45) moveStep(distance < 0 ? 1 : -1);
            touchStartX.current = null;
          }}
        >
          {STEPS.map((step, index) => (
            <article className={`home-step ${index === activeStep ? 'home-step-active' : ''}`} key={step.number}>
              <div className="home-step-top">
                <span className="home-step-number">{step.number}</span>
                {index < STEPS.length - 1 && <span className="home-step-line" aria-hidden="true" />}
              </div>
              <h3>{step.title}</h3>
              <p>{step.text}</p>
              <span className="home-step-detail">{step.detail}</span>
            </article>
          ))}
        </div>
        <div className="home-step-controls">
          <button onClick={() => moveStep(-1)} disabled={activeStep === 0} aria-label="이전 이용 단계">‹</button>
          <div className="home-step-progress" aria-label={`${STEPS.length}단계 중 ${activeStep + 1}단계`}>
            {STEPS.map((step, index) => (
              <button
                key={step.number}
                className={index === activeStep ? 'active' : ''}
                onClick={() => setActiveStep(index)}
                aria-label={`${step.number}단계 ${step.title}`}
              />
            ))}
            <span>{activeStep + 1} / {STEPS.length}</span>
          </div>
          <button onClick={() => moveStep(1)} disabled={activeStep === STEPS.length - 1} aria-label="다음 이용 단계">›</button>
        </div>
        <p className="home-dev-note">
          개발 중(아직 제공하지 않음): RAG 기반 안내.
        </p>
      </section>
    </main>
  );
}
