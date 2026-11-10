// 카카오 장소 검색(JS SDK services 라이브러리) 결과를 화면에서 쓰는 장소 형태로 바꾼다.
export function toPlace(doc) {
  return {
    name: doc.place_name,
    lat: parseFloat(doc.y),
    lng: parseFloat(doc.x),
    address: doc.road_address_name || doc.address_name || '',
  };
}

// 키워드 검색. SDK 가 아직 없거나 결과가 없으면 빈 배열. 최대 5건.
export function searchPlaces(keyword) {
  return new Promise((resolve) => {
    const services = globalThis.kakao?.maps?.services;
    if (!keyword.trim() || !services) {
      resolve([]);
      return;
    }
    new services.Places().keywordSearch(keyword, (data, status) => {
      resolve(status === services.Status.OK ? data.slice(0, 5).map(toPlace) : []);
    });
  });
}
