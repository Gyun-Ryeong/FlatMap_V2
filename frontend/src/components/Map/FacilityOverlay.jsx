import { useEffect, useRef } from 'react';
import { fetchFacilities } from '../../api/mapData';
import { FACILITY_LAYERS, FACILITY_MAX_LEVEL } from '../Panel/LayerPanel';

const MARKER_STYLE = {
  CCTV: { color: '#5a7ab8', label: 'CCTV', size: 10 },
  SECURITY_LIGHT: { color: '#c8942a', label: '보안등', size: 8 },
  POLICE: { color: '#b84c4c', label: '경찰시설', size: 12 },
};

// layers: { CCTV: bool, SECURITY_LIGHT: bool, POLICE: bool, ... } — 켜진 종류만 지도 영역 기준으로 불러와 표시한다.
// 시설 이름은 외부 데이터이므로 HTML 문자열이 아니라 textContent 로만 넣는다.
export default function FacilityOverlay({ map, layers, bounds, zoom, onTruncated }) {
  const markersRef = useRef([]);
  const tooltipRef = useRef(null);

  const clearAll = () => {
    markersRef.current.forEach((m) => m.setMap(null));
    markersRef.current = [];
    if (tooltipRef.current) {
      tooltipRef.current.setMap(null);
      tooltipRef.current = null;
    }
  };

  useEffect(() => clearAll, []);

  useEffect(() => {
    clearAll();
    onTruncated?.(false);

    const kakao = window.kakao;
    const enabled = FACILITY_LAYERS.map((f) => f.key).filter((key) => layers[key]);
    if (!map || !kakao || !enabled.length || !bounds || !zoom || zoom > FACILITY_MAX_LEVEL) return undefined;

    const controller = new AbortController();

    const showTooltip = (facility, style) => {
      if (tooltipRef.current) tooltipRef.current.setMap(null);
      const tip = document.createElement('div');
      tip.className = 'facility-tooltip';
      tip.textContent = facility.name ? `${style.label} · ${facility.name}` : style.label;
      tooltipRef.current = new kakao.maps.CustomOverlay({
        content: tip,
        position: new kakao.maps.LatLng(facility.latitude, facility.longitude),
        yAnchor: 2.6,
        zIndex: 20,
        map,
      });
    };

    fetchFacilities(bounds, enabled, controller.signal)
      .then((data) => {
        if (controller.signal.aborted) return;
        onTruncated?.(!!data.truncated);
        data.facilities.forEach((f) => {
          const style = MARKER_STYLE[f.facilityType];
          if (!style) return;
          const dot = document.createElement('div');
          dot.className = 'facility-dot';
          dot.style.cssText = `width:${style.size}px;height:${style.size}px;background:${style.color}`;
          dot.addEventListener('click', () => showTooltip(f, style));
          markersRef.current.push(new kakao.maps.CustomOverlay({
            content: dot,
            position: new kakao.maps.LatLng(f.latitude, f.longitude),
            zIndex: f.facilityType === 'POLICE' ? 15 : 10,
            map,
          }));
        });
      })
      .catch(() => { /* 취소되었거나 서버 오류 — 시설 표시만 생략한다 */ });

    return () => controller.abort();
    // clearAll/onTruncated 는 렌더마다 새로 만들어지지만 효과의 의존성은 입력 데이터만이다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [map, layers, bounds, zoom]);

  return null;
}
