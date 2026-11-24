package com.flatmap.navigation.walk;

/** DEM 파일을 읽을 수 없거나 지원하지 않는 형식일 때. 메시지는 로그용이며 응답에 싣지 않는다. */
public class DemException extends RuntimeException {

    public DemException(String message) {
        super(message);
    }

    public DemException(String message, Throwable cause) {
        super(message, cause);
    }
}
