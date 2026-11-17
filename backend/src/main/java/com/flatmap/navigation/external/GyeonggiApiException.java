package com.flatmap.navigation.external;

/**
 * 경기데이터드림 호출 실패. 메시지에는 URL·키를 넣지 않는다(서비스 이름·결과 코드 정도만).
 */
public class GyeonggiApiException extends RuntimeException {

    public GyeonggiApiException(String message) {
        super(message);
    }

    public GyeonggiApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
