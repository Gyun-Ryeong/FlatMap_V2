package com.flatmap.navigation.ingestion;

/**
 * 수집 실패. 메시지는 서버 로그에만 남으므로 URL·키·응답 본문을 넣지 않는다.
 */
public class IngestionException extends RuntimeException {
    public IngestionException(String message) {
        super(message);
    }
}
