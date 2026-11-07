// 경로 검색 최근 기록 (localStorage 기반, 로그인 불필요)
const KEY = 'flatmap_search_history';
const MAX = 20;

export function getSearchHistory() {
  try {
    const raw = localStorage.getItem(KEY);
    return raw ? JSON.parse(raw) : [];
  } catch {
    return [];
  }
}

export function addSearchHistory(place) {
  if (!place || !place.name) return getSearchHistory();
  const entry = {
    id: `${place.name}|${place.lat}|${place.lng}`,
    name: place.name,
    lat: place.lat,
    lng: place.lng,
    address: place.address || '',
  };
  const prev = getSearchHistory().filter((h) => h.id !== entry.id);
  const next = [entry, ...prev].slice(0, MAX);
  try { localStorage.setItem(KEY, JSON.stringify(next)); } catch { /* 저장 실패 무시 */ }
  return next;
}

export function removeSearchHistory(id) {
  const next = getSearchHistory().filter((h) => h.id !== id);
  try { localStorage.setItem(KEY, JSON.stringify(next)); } catch { /* 저장 실패 무시 */ }
  return next;
}

export function clearSearchHistory() {
  try { localStorage.removeItem(KEY); } catch { /* 삭제 실패 무시 */ }
  return [];
}