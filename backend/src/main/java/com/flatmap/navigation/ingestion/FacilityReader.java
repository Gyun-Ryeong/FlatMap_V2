package com.flatmap.navigation.ingestion;

/** 한 소스에서 성남시 안전시설 후보를 읽어 온다. DB 에는 쓰지 않는다(적재는 FacilityIngestionService). */
public interface FacilityReader {

    FacilitySource source();

    /** @throws IngestionException 소스 접근·파싱 실패(메시지에 URL·키 금지) */
    SourceReading read() throws Exception;
}
