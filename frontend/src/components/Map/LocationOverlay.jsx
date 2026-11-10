import { useEffect } from 'react';

function buildContent() {
  const wrap = document.createElement('div');
  wrap.className = 'loc-marker';
  const pulse = document.createElement('div');
  pulse.className = 'loc-marker-pulse';
  const dot = document.createElement('div');
  dot.className = 'loc-marker-dot';
  wrap.append(pulse, dot);
  return wrap;
}

// 실시간 내 위치 표시
export default function LocationOverlay({ map, location }) {
  useEffect(() => {
    const kakao = window.kakao;
    if (!map || !kakao || !location) return undefined;

    const overlay = new kakao.maps.CustomOverlay({
      content: buildContent(),
      position: new kakao.maps.LatLng(location.lat, location.lng),
      xAnchor: 0.5,
      yAnchor: 0.5,
      zIndex: 30,
    });
    overlay.setMap(map);

    return () => overlay.setMap(null);
  }, [map, location]);

  return null;
}
