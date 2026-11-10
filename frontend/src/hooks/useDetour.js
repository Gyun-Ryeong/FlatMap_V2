import { useState, useCallback, useEffect } from 'react';
import { fetchDetour, routeErrorMessage } from '../api/routes';
import { buildDetourRequest, canRequestDetour } from '../utils/detour';

const IDLE = { status: 'idle', data: null, message: '' };

// 선택한 후보 경로에 대한 우회 경로 요청 상태. 경로가 바뀌면 초기화한다.
// 서버가 우회를 채택하지 않으면(detourApplied=false) 원경로가 돌아오고 이유 문구가 담긴다.
export default function useDetour(route, origin, destination) {
  const [state, setState] = useState(IDLE);
  const routeId = route?.routeId ?? null;

  useEffect(() => {
    setState(IDLE);
  }, [routeId]);

  const request = useCallback(async () => {
    if (!canRequestDetour(route) || !origin || !destination) return;
    setState({ status: 'loading', data: null, message: '' });
    try {
      const data = await fetchDetour(buildDetourRequest(route, origin, destination));
      if (data.detourApplied === false) {
        setState({ status: 'rejected', data: null, message: data.detourMessage || '더 나은 우회 경로를 찾지 못했습니다.' });
      } else {
        setState({ status: 'done', data, message: '' });
      }
    } catch (err) {
      setState({ status: 'failed', data: null, message: routeErrorMessage(err) });
    }
  }, [route, origin, destination]);

  return { detour: state, requestDetour: request };
}
