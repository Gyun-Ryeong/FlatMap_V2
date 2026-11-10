import { useEffect, useRef } from 'react';
import { fetchSteepSlopes } from '../../api/mapData';
import { WARNING_COLOR } from './RouteOverlay';

const RADIUS_METERS = 50; // 표시용 원 크기. 경로 경고 판정 반경(서버 200m)과 다르다.

function addressOf(item) {
  return [item.sigungu, item.eupmyeon, item.jibun].filter(Boolean).join(' ');
}

// 급경사지(붕괴위험 지정지) 경고 레이어. 등급은 없고 경사도(°)는 측정되지 않았을 수 있다.
// 이 데이터는 붕괴위험 지정지만 모은 표본이며 보행난이도 임계값을 적용하지 않는다.
export default function SteepSlopeOverlay({ map, enabled, onLoaded }) {
  const itemsRef = useRef(null);
  const shapesRef = useRef([]);
  const infoRef = useRef(null);

  useEffect(() => {
    const kakao = window.kakao;
    if (!map || !kakao || !enabled) return undefined;
    let cancelled = false;

    const closeInfo = () => {
      if (infoRef.current) {
        infoRef.current.setMap(null);
        infoRef.current = null;
      }
    };

    const showInfo = (item, pos) => {
      closeInfo();
      const box = document.createElement('div');
      box.className = 'layer-info-window';

      const header = document.createElement('div');
      header.className = 'layer-info-header';
      const title = document.createElement('span');
      title.textContent = item.name || '이름 없음';
      const close = document.createElement('button');
      close.type = 'button';
      close.className = 'layer-info-close';
      close.textContent = '×';
      close.addEventListener('click', (ev) => { ev.stopPropagation(); closeInfo(); });
      header.append(title, close);

      const body = document.createElement('div');
      body.className = 'layer-info-body';
      const lines = [
        '붕괴위험 지정지 (경고 레이어)',
        item.isMountain ? '산지형 (산 지번·임야)' : null,
        `경사도: ${item.grade != null ? `${item.grade.toFixed(1)}°` : '미측정'}`,
        addressOf(item) ? `주소: ${addressOf(item)}` : null,
        item.source ? `출처: ${item.source}` : null,
      ].filter(Boolean);
      lines.forEach((text) => {
        const p = document.createElement('p');
        p.textContent = text;
        body.appendChild(p);
      });

      box.append(header, body);
      infoRef.current = new kakao.maps.CustomOverlay({ position: pos, content: box, yAnchor: 1.4, zIndex: 300, map });
    };

    const draw = (items) => {
      items.forEach((item) => {
        if (item.latitude == null || item.longitude == null) return;
        const pos = new kakao.maps.LatLng(item.latitude, item.longitude);

        const circle = new kakao.maps.Circle({
          center: pos, radius: RADIUS_METERS, strokeWeight: 0, strokeOpacity: 0,
          fillColor: WARNING_COLOR, fillOpacity: 0.25, zIndex: 190, map,
        });
        kakao.maps.event.addListener(circle, 'click', () => showInfo(item, pos));

        const dot = document.createElement('div');
        dot.className = 'steep-dot';
        dot.addEventListener('click', (e) => { e.stopPropagation(); showInfo(item, pos); });
        const pin = new kakao.maps.CustomOverlay({ position: pos, content: dot, yAnchor: 0.5, zIndex: 191, map });

        shapesRef.current.push(circle, pin);
      });
    };

    const load = itemsRef.current
      ? Promise.resolve(itemsRef.current)
      : fetchSteepSlopes().then((items) => { itemsRef.current = items; return items; });

    load
      .then((items) => {
        if (cancelled) return;
        onLoaded?.(items.length);
        draw(items);
      })
      .catch(() => { if (!cancelled) onLoaded?.(null); });

    return () => {
      cancelled = true;
      closeInfo();
      shapesRef.current.forEach((s) => s.setMap(null));
      shapesRef.current = [];
    };
    // onLoaded 는 표시용 콜백이라 의존성에서 제외한다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [map, enabled]);

  return null;
}
