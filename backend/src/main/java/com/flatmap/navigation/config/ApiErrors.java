package com.flatmap.navigation.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 클라이언트에 내보내는 일반화된 오류 응답.
 * 예외 메시지(e.getMessage())에는 외부 API URL·키 조각·SQL·내부 경로가 섞일 수 있으므로 응답에 싣지 않는다.
 * 상세 내용은 서버 로그(log.error)에만 남긴다.
 */
public final class ApiErrors {

    public static final String INTERNAL_MESSAGE = "요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.";

    private ApiErrors() {}

    /** 500 — 일반화된 서버 오류. extra 는 {키, 값, 키, 값 ...} 형태의 안전한 부가 정보(예: source). */
    public static ResponseEntity<Map<String, Object>> internal(Object... extra) {
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", INTERNAL_MESSAGE, extra);
    }

    public static ResponseEntity<Map<String, Object>> body(HttpStatus status, String code, String message,
                                                           Object... extra) {
        Map<String, Object> body = new LinkedHashMap<>();
        for (int i = 0; i + 1 < extra.length; i += 2) {
            body.put(String.valueOf(extra[i]), extra[i + 1]);
        }
        body.put("error", message);
        body.put("code", code);
        return ResponseEntity.status(status).body(body);
    }
}
