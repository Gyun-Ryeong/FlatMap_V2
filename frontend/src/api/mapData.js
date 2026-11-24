import axiosInstance from './axiosInstance';

// GET /api/safety/facilities — 지도 영역 안의 시설 { count, limit, truncated, facilities[] }
export async function fetchFacilities(bounds, types, signal, limit = 500) {
  const res = await axiosInstance.get('/safety/facilities', {
    params: {
      minLat: bounds.lat1,
      minLng: bounds.lng1,
      maxLat: bounds.lat2,
      maxLng: bounds.lng2,
      type: types.join(','),
      limit,
    },
    signal,
  });
  return res.data;
}

// GET /api/density-grid — 시설 밀도 격자 { note, count, cells[] }
export async function fetchDensityCells(bounds, signal) {
  const res = await axiosInstance.get('/density-grid', {
    params: { minLat: bounds.lat1, minLng: bounds.lng1, maxLat: bounds.lat2, maxLng: bounds.lng2 },
    signal,
  });
  return res.data;
}

// GET /api/steep-slope — 급경사지(붕괴위험 지정지). 등급 없음: grade(°)는 nullable.
export async function fetchSteepSlopes(regionCode = '411') {
  const res = await axiosInstance.get('/steep-slope', { params: regionCode ? { regionCode } : {} });
  return res.data;
}

// GET /api/safety/{accident-zones|protected-zones|welfare-facilities|shade-shelters|senior-centers}
// → [{ id, name, latitude, longitude, type?, address?, phone?, regionCode }]. 위치 안내용 레이어이며 점수에 쓰지 않는다.
export async function fetchZoneItems(endpoint, signal) {
  const res = await axiosInstance.get(endpoint, { signal });
  return res.data;
}

// GET /api/weather — 실패하면 null (화면은 날씨 없이도 동작한다)
export async function fetchWeather(lat, lng) {
  try {
    const res = await axiosInstance.get('/weather', { params: { lat, lng } });
    return res.data;
  } catch {
    return null;
  }
}
