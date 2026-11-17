import { SERVICE_NAME, SERVICE_TAGLINE } from '../config/branding';

export default function Header({ mapReady, mapFailed, onHome, onSOS, accountSlot }) {
  const statusColor = mapFailed ? '#b84c4c' : mapReady ? '#4b946d' : '#b57b20';
  const statusText = mapFailed ? '지도 연결 실패' : mapReady ? '카카오맵 연결됨' : '지도 로딩 중...';

  return (
    <header className="app-header">
      <div className="app-brand">
        <div className="app-brand-mark">
          <svg width={19} height={19} viewBox="0 0 14 14" fill="none" aria-hidden="true">
            <path
              d="M7 1C4.8 1 3 2.8 3 5C3 8 7 13 7 13S11 8 11 5C11 2.8 9.2 1 7 1Z"
              fill="white"
              fillOpacity={0.92}
            />
            <circle cx={7} cy={5} r={1.5} fill="#52725d" />
          </svg>
        </div>
        <span className="app-brand-title">{SERVICE_NAME}</span>
        <span className="beta-badge">BETA</span>
        <span className="header-project-summary">
          <span className="header-summary-full">{SERVICE_TAGLINE}</span>
          <span className="header-summary-mobile">안전 보행 내비게이션</span>
        </span>
      </div>

      <div className="app-header-actions">
        <span className="map-status">
          <span className="map-status-dot" style={{ background: statusColor }} />
          {statusText}
        </span>
        {accountSlot}
        <button className="intro-button" onClick={onHome}>
          <span className="intro-label-full">서비스 소개</span>
          <span className="intro-label-mobile">소개</span>
        </button>
        <button className="sos-button" onClick={onSOS}>
          긴급 신고
        </button>
      </div>
    </header>
  );
}
