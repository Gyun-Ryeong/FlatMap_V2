package com.flatmap.navigation.service;

/**
 * 경로 제공자(T Map) 호출·응답 처리 실패. 메시지는 일반화된 문구라 로그·응답에 써도 안전하다.
 * 원인(cause)에는 외부 응답 내용이 섞일 수 있으므로 응답으로 내보내지 않는다.
 */
public class RouteProviderException extends RuntimeException {

    public RouteProviderException(String message) {
        super(message);
    }

    public RouteProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
