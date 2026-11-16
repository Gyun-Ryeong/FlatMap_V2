import axiosInstance from './axiosInstance';

// GET /api/rag/status → { enabled, connected }.
// RAG 는 개발 중이다. 서버가 RAG 연동 지점을 꺼 두면(기본) 이 경로가 404 이므로, 어떤 오류든 null 로 돌려 "없음"으로 취급한다.
export async function fetchRagStatus(signal) {
  try {
    const res = await axiosInstance.get('/rag/status', { signal });
    return res.data;
  } catch {
    return null;
  }
}

// POST /api/rag/ask → { answer, sources[] } (로그인 필요). 실패는 호출한 쪽이 ragErrorMessage 로 안내한다.
export async function askRag(question) {
  const res = await axiosInstance.post('/rag/ask', { question });
  return res.data;
}
