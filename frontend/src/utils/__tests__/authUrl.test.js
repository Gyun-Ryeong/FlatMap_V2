import { describe, expect, it } from 'vitest';
import { KAKAO_LOGIN_PATH, buildLoginUrl } from '../authUrl';

describe('카카오 로그인 주소', () => {
  it('백엔드 주소를 안 주면 같은 출처의 상대 경로(운영 nginx·개발 프록시가 처리)', () => {
    expect(buildLoginUrl(undefined)).toBe('/api/auth/kakao/login');
    expect(buildLoginUrl('')).toBe('/api/auth/kakao/login');
    expect(buildLoginUrl('   ')).toBe('/api/auth/kakao/login');
    expect(buildLoginUrl(null)).toBe(KAKAO_LOGIN_PATH);
  });

  it('다른 출처의 백엔드를 쓸 때만 절대 주소(끝의 슬래시는 정리)', () => {
    expect(buildLoginUrl('https://api.example.org')).toBe('https://api.example.org/api/auth/kakao/login');
    expect(buildLoginUrl('https://api.example.org/')).toBe('https://api.example.org/api/auth/kakao/login');
    expect(buildLoginUrl('http://10.0.0.5:8080//')).toBe('http://10.0.0.5:8080/api/auth/kakao/login');
  });

  it('localhost 같은 특정 호스트를 기본값으로 만들어 내지 않는다', () => {
    expect(buildLoginUrl(undefined)).not.toMatch(/localhost|127\.0\.0\.1|ngrok/i);
  });
});
