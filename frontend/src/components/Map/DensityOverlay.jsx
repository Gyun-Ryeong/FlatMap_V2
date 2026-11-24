import { useEffect, useRef } from 'react';
import { fetchDensityCells } from '../../api/mapData';
import { FACILITY_MAX_LEVEL } from '../Panel/LayerPanel';
import { DENSITY_LABELS } from '../../utils/densityCells';

// 시설 밀도 등급별 색. "위험"이 아니라 시설이 얼마나 수집돼 있는지를 나타내므로 한 가지 색의 진하기로만 구분한다.
const FILL = {
  LOW_DENSITY: { color: '#8a9bb5', opacity: 0.38 },
  MEDIUM: { color: '#5f86b8', opacity: 0.22 },
  HIGH_DENSITY: { color: '#2d6aa3', opacity: 0.2 },
};

export default function DensityOverlay({ map, enabled, bounds, zoom }) {
  const shapesRef = useRef([]);
  const labelRef = useRef(null);

  const clearAll = () => {
    shapesRef.current.forEach((s) => s.setMap(null));
    shapesRef.current = [];
    if (labelRef.current) {
      labelRef.current.setMap(null);
      labelRef.current = null;
    }
  };

  useEffect(() => clearAll, []);

  useEffect(() => {
    clearAll();
    const kakao = window.kakao;
    if (!map || !kakao || !enabled || !bounds || !zoom || zoom > FACILITY_MAX_LEVEL) return undefined;

    const controller = new AbortController();
    fetchDensityCells(bounds, controller.signal)
      .then((data) => {
        if (controller.signal.aborted) return;
        data.cells.forEach((cell) => {
          const fill = FILL[cell.densityLevel] ?? FILL.MEDIUM;
          const polygon = new kakao.maps.Polygon({
            path: cell.bounds.map(([lat, lng]) => new kakao.maps.LatLng(lat, lng)),
            strokeWeight: 1,
            strokeColor: fill.color,
            strokeOpacity: 0.5,
            fillColor: fill.color,
            fillOpacity: fill.opacity,
            zIndex: 1,
            map,
          });
          kakao.maps.event.addListener(polygon, 'click', () => {
            if (labelRef.current) labelRef.current.setMap(null);
            const tip = document.createElement('div');
            tip.className = 'facility-tooltip';
            tip.textContent =
              `${DENSITY_LABELS[cell.densityLevel] ?? cell.densityLevel} · 시설 ${cell.facilityCount}개 ` +
              `(CCTV ${cell.cctvCount}, 보안등 ${cell.lightCount}, 경찰 ${cell.policeCount})`;
            labelRef.current = new kakao.maps.CustomOverlay({
              content: tip,
              position: new kakao.maps.LatLng((cell.minLat + cell.maxLat) / 2, (cell.minLng + cell.maxLng) / 2),
              zIndex: 20,
              map,
            });
          });
          shapesRef.current.push(polygon);
        });
      })
      .catch(() => { /* 격자는 비어 있거나 서버 오류일 수 있다 — 표시만 생략한다 */ });

    return () => controller.abort();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [map, enabled, bounds, zoom]);

  return null;
}
