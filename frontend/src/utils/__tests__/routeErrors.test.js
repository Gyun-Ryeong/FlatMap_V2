import { describe, expect, it } from 'vitest';
import { routeErrorMessage } from '../../api/routes';

// 서버 내부 메시지·상태 코드 세부는 화면에 노출하지 않고, 사용자가 할 수 있는 안내만 보여 준다.
describe('경로 오류 문구', () => {
  it('400 은 입력 확인 안내', () => {
    expect(routeErrorMessage({ response: { status: 400, data: { error: 'x' } } })).toBe('입력한 위치를 확인해 주세요.');
  });

  it('그 밖의 서버 오류(502 포함)는 일반화된 재시도 안내이고 서버 본문을 싣지 않는다', () => {
    const msg = routeErrorMessage({
      response: { status: 502, data: { error: 'jdbc:postgresql://secret', code: 'ROUTE_PROVIDER_UNAVAILABLE' } },
    });
    expect(msg).toBe('경로를 가져오지 못했습니다. 잠시 후 다시 시도해 주세요.');
    expect(msg).not.toMatch(/jdbc|secret|ROUTE_PROVIDER/);
    expect(routeErrorMessage({ response: { status: 500 } })).toBe(msg);
  });

  it('응답이 없으면(네트워크 오류·타임아웃) 연결 안내', () => {
    expect(routeErrorMessage(new Error('Network Error'))).toBe('서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.');
    expect(routeErrorMessage(undefined)).toBe('서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.');
  });
});
