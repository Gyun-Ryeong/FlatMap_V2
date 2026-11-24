import { useEffect, useState } from 'react';
import { fetchRagStatus } from '../api/rag';

// 서버의 RAG 연동 지점 상태. 처음에는 null(= 진입점 숨김)이고, 응답이 오기 전·꺼짐(404)·오류에서도 null 이다.
export default function useRagStatus() {
  const [status, setStatus] = useState(null);

  useEffect(() => {
    const controller = new AbortController();
    fetchRagStatus(controller.signal).then((s) => {
      if (!controller.signal.aborted) setStatus(s);
    });
    return () => controller.abort();
  }, []);

  return status;
}
