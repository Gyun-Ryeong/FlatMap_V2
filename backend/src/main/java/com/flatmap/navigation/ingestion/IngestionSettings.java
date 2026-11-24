package com.flatmap.navigation.ingestion;

/**
 * 수집기 설정. 값은 환경변수 → application.properties 에서 온다(IngestionConfig).
 * toString 은 키 값을 찍지 않는다.
 */
public record IngestionSettings(
        long pageDelayMs,
        int pageSize,
        int maxPages,
        String seongnamCsvPath,
        String gyeonggiApiKey,
        String publicDataApiKey) {

    /** 페이지 사이 지연(외부 API 에 부담을 주지 않기 위함). */
    public void pause() {
        if (pageDelayMs <= 0) return;
        try {
            Thread.sleep(pageDelayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IngestionException("interrupted");
        }
    }

    @Override
    public String toString() {
        return "IngestionSettings[pageDelayMs=" + pageDelayMs + ", pageSize=" + pageSize
                + ", maxPages=" + maxPages + ", keys=hidden]";
    }
}
