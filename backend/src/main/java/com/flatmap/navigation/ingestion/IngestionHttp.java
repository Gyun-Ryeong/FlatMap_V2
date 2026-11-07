package com.flatmap.navigation.ingestion;

import java.io.IOException;

/**
 * 수집용 HTTP GET. 응답 본문은 항상 UTF-8 로 해석한다.
 * 구현은 타임아웃을 반드시 걸고, 실패 시 URL(키가 들어 있다)을 메시지에 담지 않는다.
 */
public interface IngestionHttp {
    String get(String url) throws IOException;
}
