import { useEffect, useRef, useState } from 'react';
import { fetchZoneItems } from '../../api/mapData';
import { ZONE_LAYERS, visibleZoneItems, zoneInfoLines } from '../../utils/zoneLayers';

// FlatMap 안전정보 레이어(사고다발지·교통약자 보호구역·장애인복지시설·그늘막·노인복지관).
// 켜진 레이어만 한 번 불러와 캐시하고, 현재 지도 영역 안의 항목을 줌에 따른 상한까지 원형 마커로 그린다.
// 시설 이름·주소는 외부 데이터이므로 HTML 문자열이 아니라 textContent 로만 넣는다.
// 위치 안내용이며 경로 점수에는 반영되지 않는다.
export default function ZoneOverlay({ map, layers, bounds, zoom, onLoaded }) {
  const cacheRef = useRef({});          // key → 항목 배열
  const inFlightRef = useRef(new Set());
  const markersRef = useRef([]);
  const infoRef = useRef(null);
  const [version, setVersion] = useState(0);   // 데이터가 도착하면 다시 그린다

  const closeInfo = () => {
    if (infoRef.current) {
      infoRef.current.setMap(null);
      infoRef.current = null;
    }
  };

  const clearMarkers = () => {
    markersRef.current.forEach((m) => m.setMap(null));
    markersRef.current = [];
    closeInfo();
  };

  useEffect(() => clearMarkers, []);

  // 켜진 레이어 중 아직 없는 것만 불러온다. 실패하면 캐시하지 않아 다시 켤 때 재시도한다.
  useEffect(() => {
    ZONE_LAYERS.filter((l) => layers[l.key]).forEach((layer) => {
      if (cacheRef.current[layer.key] || inFlightRef.current.has(layer.key)) return;
      inFlightRef.current.add(layer.key);
      fetchZoneItems(layer.endpoint)
        .then((items) => {
          cacheRef.current[layer.key] = Array.isArray(items) ? items : [];
          onLoaded?.(layer.key, cacheRef.current[layer.key].length);
          setVersion((v) => v + 1);
        })
        .catch(() => onLoaded?.(layer.key, null))
        .finally(() => inFlightRef.current.delete(layer.key));
    });
    // onLoaded 는 표시용 콜백이라 의존성에서 제외한다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [layers]);

  useEffect(() => {
    clearMarkers();
    const kakao = window.kakao;
    if (!map || !kakao) return;

    ZONE_LAYERS.filter((l) => layers[l.key] && cacheRef.current[l.key]).forEach((layer) => {
      visibleZoneItems(cacheRef.current[layer.key], bounds, zoom).forEach((item) => {
        const pos = new kakao.maps.LatLng(item.latitude, item.longitude);

        const wrap = document.createElement('div');
        wrap.className = 'layer-marker-wrap';
        const circle = document.createElement('div');
        circle.className = 'layer-marker-circle';
        circle.style.background = layer.color;
        circle.textContent = layer.symbol;
        wrap.appendChild(circle);
        wrap.addEventListener('click', (e) => {
          e.stopPropagation();
          closeInfo();
          const box = document.createElement('div');
          box.className = 'layer-info-window';
          const header = document.createElement('div');
          header.className = 'layer-info-header zone';
          const title = document.createElement('span');
          title.textContent = `${layer.symbol} ${item.name || '정보 없음'}`;
          const close = document.createElement('button');
          close.type = 'button';
          close.className = 'layer-info-close';
          close.textContent = '×';
          close.addEventListener('click', (ev) => { ev.stopPropagation(); closeInfo(); });
          header.append(title, close);
          const body = document.createElement('div');
          body.className = 'layer-info-body';
          zoneInfoLines(item).forEach((line) => {
            const p = document.createElement('p');
            p.textContent = line;
            body.appendChild(p);
          });
          box.append(header, body);
          infoRef.current = new kakao.maps.CustomOverlay({ position: pos, content: box, yAnchor: 1.4, zIndex: 300, map });
        });

        markersRef.current.push(new kakao.maps.CustomOverlay({ position: pos, content: wrap, yAnchor: 0.5, zIndex: 80, map }));
      });
    });
  }, [map, layers, bounds, zoom, version]);

  return null;
}
