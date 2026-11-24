// 카카오 로그인 시작 주소. 전체 페이지 이동은 axios baseURL 을 타지 않으므로 여기서 직접 만든다.
//
// backendUrl 이 비어 있으면(기본값) 같은 출처의 상대 경로(/api/...)를 쓴다:
//  - 운영: 프론트 컨테이너(nginx)가 /api 를 백엔드로 넘기므로 같은 출처로 충분하다.
//  - 개발: Vite dev 서버가 /api 를 백엔드로 프록시한다.
// 백엔드가 다른 출처에 있을 때만 VITE_BACKEND_URL 로 절대 주소를 준다(예: https://api.example.org).
// 특정 호스트(localhost, 터널 주소 등)를 코드에 박아 두지 않는다.
export const KAKAO_LOGIN_PATH = '/api/auth/kakao/login';

export function buildLoginUrl(backendUrl) {
  const base = (backendUrl ?? '').trim().replace(/\/+$/, '');
  return `${base}${KAKAO_LOGIN_PATH}`;
}
