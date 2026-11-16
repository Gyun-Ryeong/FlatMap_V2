import axiosInstance from './axiosInstance';

// GET /api/routes/safe → { routes[], scoreModel }. mode: 보행 유형(GENERAL/WEAK, 보행난이도 B축 임계값)
export async function fetchSafeRoutes(origin, destination, walkMode) {
  const res = await axiosInstance.get('/routes/safe', {
    params: {
      startLat: origin.lat,
      startLng: origin.lng,
      endLat: destination.lat,
      endLng: destination.lng,
      mode: walkMode,
    },
  });
  return res.data;
}

// POST /api/route/walk/detour → { coords[], totalDistance, totalDuration, riskSections[], weather,
//   detourApplied, detourMessage, totalScore, breakdown[], originalTotalScore }
export async function fetchDetour(body) {
  const res = await axiosInstance.post('/route/walk/detour', body);
  return res.data;
}

// 사용자에게 보여 줄 오류 문구. 서버 내부 메시지는 노출하지 않는다.
export function routeErrorMessage(err) {
  if (err?.response?.status === 400) return '입력한 위치를 확인해 주세요.';
  if (err?.response) return '경로를 가져오지 못했습니다. 잠시 후 다시 시도해 주세요.';
  return '서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.';
}
