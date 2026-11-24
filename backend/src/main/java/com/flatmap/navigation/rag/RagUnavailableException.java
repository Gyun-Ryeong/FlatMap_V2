package com.flatmap.navigation.rag;

/**
 * RAG 서버 호출 실패. 구현체는 URL·키·응답 본문 같은 내부 정보를 메시지에 담지 않는다.
 * 컨트롤러는 메시지를 응답에 싣지 않고 일반화된 502 {@code RAG_UNAVAILABLE} 만 돌려준다.
 */
public class RagUnavailableException extends RuntimeException {

    public RagUnavailableException(String message) {
        super(message);
    }

    public RagUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
