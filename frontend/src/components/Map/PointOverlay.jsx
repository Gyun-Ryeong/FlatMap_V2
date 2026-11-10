import { useEffect } from 'react';

const COLORS = {
  origin: { bg: '#5a7ab8', label: '출발' },
  dest: { bg: '#b84c4c', label: '도착' },
};

function buildContent({ bg, label }) {
  const wrap = document.createElement('div');
  wrap.className = 'point-marker';
  const tag = document.createElement('div');
  tag.className = 'point-marker-tag';
  tag.style.background = bg;
  tag.textContent = label;
  const stem = document.createElement('div');
  stem.className = 'point-marker-stem';
  stem.style.background = bg;
  const dot = document.createElement('div');
  dot.className = 'point-marker-dot';
  dot.style.background = bg;
  wrap.append(tag, stem, dot);
  return wrap;
}

// 출발/도착 지점 표시. point 가 null 이면 아무것도 그리지 않는다.
export default function PointOverlay({ map, point, type }) {
  useEffect(() => {
    const kakao = window.kakao;
    if (!map || !kakao || !point) return undefined;

    const overlay = new kakao.maps.CustomOverlay({
      position: new kakao.maps.LatLng(point.lat, point.lng),
      content: buildContent(COLORS[type]),
      xAnchor: 0.5,
      yAnchor: 1,
      zIndex: 25,
    });
    overlay.setMap(map);

    return () => overlay.setMap(null);
  }, [map, point, type]);

  return null;
}
