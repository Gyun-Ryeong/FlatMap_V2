import { useEffect, useRef } from 'react';
import { getLevelColor } from '../../utils/scoreLevel';
import { buildWarningRanges, splitPathByWarnings } from '../../utils/warningRanges';

export const WARNING_COLOR = '#e0701f'; // 붕괴위험 지정지 경고 구간(점수와 무관한 별도 표시)
export const DETOUR_COLOR = '#2196F3';

// detour: { coords: [{lat,lng}], riskSections } | null — 우회 경로(파란 점선). 있으면 원 경로는 흐리게 그린다.
export default function RouteOverlay({ map, routes, selectedRouteId, detour }) {
  const polylinesRef = useRef([]);

  useEffect(() => {
    polylinesRef.current.forEach((pl) => pl.setMap(null));
    polylinesRef.current = [];

    const kakao = window.kakao;
    if (!map || !routes.length || !kakao) return;

    const add = (options) => {
      const polyline = new kakao.maps.Polyline(options);
      polyline.setMap(map);
      polylinesRef.current.push(polyline);
    };

    const toPath = (route) =>
      route.coordinates.map(([lat, lng]) => new kakao.maps.LatLng(lat, lng));

    // 선택되지 않은 경로는 회색 점선으로 아래에 먼저 그린다
    routes.forEach((route) => {
      if (route.routeId === selectedRouteId) return;
      add({
        path: toPath(route),
        strokeColor: '#c4c8d0',
        strokeWeight: 3,
        strokeOpacity: 0.6,
        strokeStyle: 'shortdash',
      });
    });

    // 선택된 경로는 점수 구간 색상으로 굵게 위에 그린다. 붕괴위험 지정지 경고 구간은 색을 바꿔 덧그린다.
    const selected = routes.find((route) => route.routeId === selectedRouteId);
    if (selected) {
      const path = toPath(selected);
      const dimmed = !!detour;
      add({ path, strokeColor: '#000', strokeWeight: 10, strokeOpacity: dimmed ? 0.08 : 0.2, strokeStyle: 'solid' });

      const ranges = buildWarningRanges(selected.riskSections, path.length);
      splitPathByWarnings(path, ranges).forEach((piece) => {
        add({
          path: piece.path,
          strokeColor: piece.warning ? WARNING_COLOR : getLevelColor(selected.totalScore),
          strokeWeight: piece.warning ? 8 : 7,
          strokeOpacity: dimmed ? 0.4 : 0.95,
          strokeStyle: 'solid',
        });
      });
    }

    if (detour && detour.coords && detour.coords.length > 1) {
      const path = detour.coords.map((c) => new kakao.maps.LatLng(c.lat, c.lng));
      const ranges = buildWarningRanges(detour.riskSections, path.length);
      splitPathByWarnings(path, ranges).forEach((piece) => {
        add({
          path: piece.path,
          strokeColor: piece.warning ? WARNING_COLOR : DETOUR_COLOR,
          strokeWeight: 6,
          strokeOpacity: 0.95,
          strokeStyle: 'shortdash',
        });
      });
    }

    fitBounds(map, routes, detour);
  }, [map, routes, selectedRouteId, detour]);

  useEffect(() => () => {
    polylinesRef.current.forEach((pl) => pl.setMap(null));
    polylinesRef.current = [];
  }, []);

  return null;
}

function fitBounds(map, routes, detour) {
  const kakao = window.kakao;
  const bounds = new kakao.maps.LatLngBounds();

  routes.forEach((route) => {
    route.coordinates.forEach(([lat, lng]) => {
      bounds.extend(new kakao.maps.LatLng(lat, lng));
    });
  });
  if (detour && detour.coords) {
    detour.coords.forEach((c) => bounds.extend(new kakao.maps.LatLng(c.lat, c.lng)));
  }

  // 데스크톱은 왼쪽 패널, 모바일은 아래 시트에 가려지지 않도록 여백을 둔다
  const desktop = window.innerWidth > 640;
  const top = 80;
  const right = 60;
  const bottom = desktop ? 60 : Math.round(window.innerHeight * 0.45);
  const left = desktop ? 460 : 40;
  map.setBounds(bounds, top, right, bottom, left);
}
