package com.flatmap.navigation.ingestion;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * 응답에 내보내는 소스별 결과. 소스 이름(코드값)·성공/실패·건수만 담고 실패 원인은 담지 않는다(원인은 서버 로그).
 *
 * @param read     소스에서 읽은 행 수
 * @param excluded 필터로 제외한 행 수(성남 밖, 소방서 등)
 * @param inserted 새로 삽입된 행 수 (이미 있던 좌표는 건너뛰므로 2회차에는 0)
 */
public record SourceResult(String source, String status, int read, int excluded, int inserted) {

    public static final String SUCCESS = "SUCCESS";
    public static final String FAILED = "FAILED";

    public static SourceResult success(FacilitySource source, SourceReading reading, int inserted) {
        return new SourceResult(source.code(), SUCCESS, reading.read(), reading.excluded(), inserted);
    }

    public static SourceResult failed(FacilitySource source) {
        return new SourceResult(source.code(), FAILED, 0, 0, 0);
    }

    @JsonIgnore // 응답 필드는 source/status/read/excluded/inserted 뿐이다
    public boolean isSuccess() {
        return SUCCESS.equals(status);
    }
}
