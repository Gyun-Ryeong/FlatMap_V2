let kakaoPromise = null;

// 카카오맵 JS SDK 를 한 번만 불러온다. 키는 VITE_KAKAO_API_KEY (값은 frontend/.env, 커밋 금지).
export function loadKakaoSDK() {
  if (kakaoPromise) return kakaoPromise;

  kakaoPromise = new Promise((resolve, reject) => {
    if (window.kakao?.maps) {
      window.kakao.maps.load(() => resolve(window.kakao));
      return;
    }

    const appKey = import.meta.env.VITE_KAKAO_API_KEY;
    if (!appKey) {
      kakaoPromise = null;
      reject(new Error('VITE_KAKAO_API_KEY 가 설정되지 않았습니다.'));
      return;
    }

    const script = document.createElement('script');
    script.src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${appKey}&autoload=false&libraries=services,clusterer`;
    script.async = true;

    script.onload = () => {
      window.kakao.maps.load(() => resolve(window.kakao));
    };

    script.onerror = () => {
      kakaoPromise = null;
      reject(new Error('Kakao Maps SDK 로드 실패'));
    };

    document.head.appendChild(script);
  });

  return kakaoPromise;
}
