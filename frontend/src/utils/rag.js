// RAG(질문·답변) 진입점 판단과 입력·오류 문구. RAG 는 개발 중이며 이 저장소에는 RAG 서버 구현이 없다.
//
// 진입점은 서버가 "켜짐(enabled)"이면서 "연결됨(connected)"이라고 알려 줄 때만 보인다.
// 켜져 있어도 미연결이면(= 지금 저장소의 상태) 보이지 않는다 — 아직 없는 기능을 있는 것처럼 보여 주지 않는다.

// 서버(RagController)의 질문 길이 상한과 같은 값. 최종 검증은 서버가 한다.
export const MAX_QUESTION_CHARS = 500;

export function shouldShowRagEntry(status) {
  return status?.enabled === true && status?.connected === true;
}

export function validateQuestion(text) {
  const question = (text ?? '').trim();
  if (!question) return { ok: false, question, message: '질문을 입력해 주세요.' };
  if (question.length > MAX_QUESTION_CHARS) {
    return { ok: false, question, message: `질문은 ${MAX_QUESTION_CHARS}자 이하로 입력해 주세요.` };
  }
  return { ok: true, question, message: '' };
}

// 사용자에게 보여 줄 오류 문구. 서버 내부 메시지는 노출하지 않는다.
export function ragErrorMessage(err) {
  const status = err?.response?.status;
  if (status === 401) return '질문하려면 로그인해 주세요.';
  if (status === 400) return `질문은 1자 이상 ${MAX_QUESTION_CHARS}자 이하로 입력해 주세요.`;
  if (status === 503) return 'RAG 기반 안내는 개발 중이며 아직 연결되지 않았습니다.';
  if (err?.response) return '답변을 가져오지 못했습니다. 잠시 후 다시 시도해 주세요.';
  return '서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.';
}
