import { useEffect } from 'react';

// 즐겨찾기 장소를 지도에 ★ 마커 + 이름 라벨로 표시. 이름은 textContent 로만 넣는다.
export default function FavoriteOverlay({ map, favorites }) {
  useEffect(() => {
    const kakao = window.kakao;
    if (!map || !kakao || !favorites.length) return undefined;

    const overlays = favorites
      .filter((f) => f.latitude != null && f.longitude != null)
      .map((f) => {
        const wrap = document.createElement('div');
        wrap.className = 'fav-map-marker';
        const pin = document.createElement('div');
        pin.className = 'fav-map-pin';
        pin.textContent = '★';
        const label = document.createElement('div');
        label.className = 'fav-map-label';
        label.textContent = f.name || '';
        wrap.append(pin, label);
        return new kakao.maps.CustomOverlay({
          position: new kakao.maps.LatLng(f.latitude, f.longitude),
          content: wrap,
          yAnchor: 1,
          zIndex: 90,
          map,
        });
      });

    return () => overlays.forEach((o) => o.setMap(null));
  }, [map, favorites]);

  return null;
}
