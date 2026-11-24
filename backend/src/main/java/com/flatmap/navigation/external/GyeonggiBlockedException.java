package com.flatmap.navigation.external;

/**
 * 경기데이터드림이 이 서버의 요청을 차단했거나(차단 페이지 수신), 차단 감지 후 대기 중이라 호출하지 않았다.
 */
public class GyeonggiBlockedException extends GyeonggiApiException {

    public GyeonggiBlockedException(String message) {
        super(message);
    }
}
