import { useEffect, useRef } from 'react';
import { loadKakaoSDK } from '../utils/kakaoLoader';

const DEFAULT_CENTER = [37.3837, 127.1264]; // 서현역

export default function KakaoMap({
  onMapReady,
  onMapClick,
  onError,
  clickMode,
  center = DEFAULT_CENTER,
  level = 4,
  interactive = true,
}) {
  const containerRef = useRef(null);
  const mapRef = useRef(null);

  useEffect(() => {
    let cancelled = false;

    loadKakaoSDK()
      .then((kakao) => {
        if (cancelled || mapRef.current) return;
        const map = new kakao.maps.Map(containerRef.current, {
          center: new kakao.maps.LatLng(center[0], center[1]),
          level,
        });

        if (interactive) {
          map.addControl(new kakao.maps.ZoomControl(), kakao.maps.ControlPosition.RIGHT);
        } else {
          map.setDraggable(false);
          map.setZoomable(false);
        }

        mapRef.current = map;
        if (onMapReady) onMapReady(map);
      })
      .catch((err) => {
        if (!cancelled && onError) onError(err);
      });

    return () => {
      cancelled = true;
    };
    // 지도는 마운트될 때 한 번만 만든다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // 패널 크기 변화(창 크기 조절, 모바일 회전)에 맞춰 지도 레이아웃 갱신
  useEffect(() => {
    const relayout = () => mapRef.current?.relayout();
    window.addEventListener('resize', relayout);
    return () => window.removeEventListener('resize', relayout);
  }, []);

  useEffect(() => {
    const kakao = window.kakao;
    const map = mapRef.current;
    if (!kakao || !map || !onMapClick || !clickMode) return undefined;

    const listener = (mouseEvent) => {
      onMapClick(mouseEvent.latLng.getLat(), mouseEvent.latLng.getLng());
    };

    kakao.maps.event.addListener(map, 'click', listener);
    return () => kakao.maps.event.removeListener(map, 'click', listener);
  }, [onMapClick, clickMode]);

  return (
    <div
      ref={containerRef}
      style={{
        width: '100%',
        height: '100%',
        position: 'absolute',
        top: 0,
        left: 0,
        cursor: clickMode ? 'crosshair' : 'default',
      }}
    />
  );
}
