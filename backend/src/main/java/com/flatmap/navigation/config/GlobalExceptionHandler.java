package com.flatmap.navigation.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Map;

/**
 * 컨트롤러가 처리하지 못한 예외를 일반화된 응답으로 바꾼다.
 * 표준 MVC 예외(404/405/400 등)는 원래 상태 코드를 유지하되 본문은 일반화된 메시지만 싣는다.
 * 상세 원인은 로그에만 남긴다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        log.warn("MVC 예외 {} → {}", ex.getClass().getSimpleName(), statusCode);
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        String message = status != null && status.is4xxClientError()
                ? "요청을 처리할 수 없습니다."
                : ApiErrors.INTERNAL_MESSAGE;
        Map<String, Object> safe = Map.of("error", message, "code", "HTTP_" + statusCode.value());
        return new ResponseEntity<>(safe, headers, statusCode);
    }

    /** Spring Security 가 처리해야 하는 예외는 그대로 위로 보낸다(401/403 유지). */
    @ExceptionHandler(AccessDeniedException.class)
    public void rethrowAccessDenied(AccessDeniedException e) {
        throw e;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleAny(Exception e) {
        log.error("처리되지 않은 예외", e);
        return ApiErrors.internal();
    }
}
