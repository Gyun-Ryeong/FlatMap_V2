import { describe, expect, it } from 'vitest';
import { MAX_QUESTION_CHARS, ragErrorMessage, shouldShowRagEntry, validateQuestion } from '../rag';

describe('RAG 진입점 표시 여부 (RAG 는 개발 중)', () => {
  it('켜짐이면서 연결됨일 때만 보인다', () => {
    expect(shouldShowRagEntry({ enabled: true, connected: true })).toBe(true);
  });

  it('상태를 모르거나(응답 전·꺼짐 404·오류) 꺼짐이면 숨긴다', () => {
    expect(shouldShowRagEntry(null)).toBe(false);
    expect(shouldShowRagEntry(undefined)).toBe(false);
    expect(shouldShowRagEntry({})).toBe(false);
    expect(shouldShowRagEntry({ enabled: false, connected: false })).toBe(false);
    expect(shouldShowRagEntry({ enabled: false, connected: true })).toBe(false);
  });

  it('켜져 있어도 미연결이면 숨긴다 — 서버 구현이 없는 지금 저장소의 상태', () => {
    expect(shouldShowRagEntry({ enabled: true, connected: false })).toBe(false);
  });

  it('값이 참처럼 보이는 문자열·숫자는 참으로 치지 않는다(엄격한 true 만)', () => {
    expect(shouldShowRagEntry({ enabled: 'true', connected: 'true' })).toBe(false);
    expect(shouldShowRagEntry({ enabled: 1, connected: 1 })).toBe(false);
  });
});

describe('질문 검증', () => {
  it('앞뒤 공백을 정리하고 빈 질문은 거부한다', () => {
    expect(validateQuestion('  안녕  ')).toEqual({ ok: true, question: '안녕', message: '' });
    expect(validateQuestion('').ok).toBe(false);
    expect(validateQuestion('   ').ok).toBe(false);
    expect(validateQuestion(null).ok).toBe(false);
    expect(validateQuestion(undefined).message).toBe('질문을 입력해 주세요.');
  });

  it('상한 500자: 500 은 통과, 501 은 거부(서버와 같은 값)', () => {
    expect(MAX_QUESTION_CHARS).toBe(500);
    expect(validateQuestion('가'.repeat(500)).ok).toBe(true);
    const over = validateQuestion('가'.repeat(501));
    expect(over.ok).toBe(false);
    expect(over.message).toContain('500자');
  });
});

describe('RAG 오류 문구', () => {
  it('미연결(503)은 개발 중임을 솔직하게 알린다', () => {
    expect(ragErrorMessage({ response: { status: 503 } })).toBe('RAG 기반 안내는 개발 중이며 아직 연결되지 않았습니다.');
  });

  it('401·400 은 사용자가 할 수 있는 안내', () => {
    expect(ragErrorMessage({ response: { status: 401 } })).toContain('로그인');
    expect(ragErrorMessage({ response: { status: 400 } })).toContain('500자');
  });

  it('그 밖의 서버 오류는 일반화된 안내이고 서버 본문을 싣지 않는다', () => {
    const msg = ragErrorMessage({ response: { status: 502, data: { error: 'https://rag.internal/?apiKey=SECRET' } } });
    expect(msg).toBe('답변을 가져오지 못했습니다. 잠시 후 다시 시도해 주세요.');
    expect(msg).not.toMatch(/internal|SECRET|apiKey/);
  });

  it('응답이 없으면 연결 안내', () => {
    expect(ragErrorMessage(new Error('Network Error'))).toBe('서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.');
  });
});
