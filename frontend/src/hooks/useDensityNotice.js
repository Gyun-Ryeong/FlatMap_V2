import { useCallback, useEffect, useRef, useState } from 'react';
import { fetchDensityCells } from '../api/mapData';
import { findCellAt, isLowDensity } from '../utils/densityCells';

const FETCH_HALF_SPAN = 0.01; // 내 위치 주변 약 ±1km 의 격자를 한 번에 받아 둔다
const REFETCH_AFTER_DEGREES = 0.006; // 받아 둔 범위의 가장자리에 가까워지면 다시 받는다
const COOLDOWN_MS = 30000;

// 실시간 위치가 "시설 밀도 낮음" 격자에 새로 들어오면 안내용 셀을 돌려준다. 위험 판정이 아니라 데이터가 적다는 안내다.
export default function useDensityNotice(location) {
  const [notice, setNotice] = useState(null);
  const cellsRef = useRef({ cells: [], center: null });
  const insideRef = useRef(false);
  const lastShownRef = useRef(0);
  const fetchingRef = useRef(false);

  useEffect(() => {
    if (!location) return undefined;
    let cancelled = false;

    const evaluate = () => {
      const cell = findCellAt(cellsRef.current.cells, location.lat, location.lng);
      const low = isLowDensity(cell);
      const now = Date.now();
      if (low && !insideRef.current && now - lastShownRef.current > COOLDOWN_MS) {
        setNotice(cell);
        lastShownRef.current = now;
      }
      insideRef.current = low;
    };

    const { center } = cellsRef.current;
    const needFetch =
      !center ||
      Math.abs(center.lat - location.lat) > REFETCH_AFTER_DEGREES ||
      Math.abs(center.lng - location.lng) > REFETCH_AFTER_DEGREES;

    if (needFetch && !fetchingRef.current) {
      fetchingRef.current = true;
      const bounds = {
        lat1: location.lat - FETCH_HALF_SPAN,
        lng1: location.lng - FETCH_HALF_SPAN,
        lat2: location.lat + FETCH_HALF_SPAN,
        lng2: location.lng + FETCH_HALF_SPAN,
      };
      fetchDensityCells(bounds)
        .then((data) => {
          cellsRef.current = { cells: data.cells ?? [], center: { lat: location.lat, lng: location.lng } };
          if (!cancelled) evaluate();
        })
        .catch(() => {
          // 격자를 못 받으면 안내만 생략한다. 같은 자리에서 계속 재시도하지 않도록 중심을 기록해 둔다.
          cellsRef.current = { cells: [], center: { lat: location.lat, lng: location.lng } };
        })
        .finally(() => { fetchingRef.current = false; });
    } else {
      evaluate();
    }

    return () => { cancelled = true; };
  }, [location]);

  const dismiss = useCallback(() => setNotice(null), []);
  return { notice, dismiss };
}
