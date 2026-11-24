package com.flatmap.navigation.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flatmap.navigation.config.ApiKeyConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IngestionConfig {

    @Bean
    IngestionSettings ingestionSettings(
            // CSV 경로는 환경변수(.env 포함)를 직접 읽는다: application.properties 를 건드리지 않고 키 이름만 .env.example 에 둔다.
            @Value("${ingestion.page-delay-ms:300}") long pageDelayMs,
            @Value("${ingestion.page-size:1000}") int pageSize,
            @Value("${ingestion.max-pages:500}") int maxPages,
            @Value("${ingestion.seongnam-csv-path:${SEONGNAM_SECURITY_LIGHT_CSV_PATH:}}") String csvPath,
            @Value("${public-data.api-key:}") String publicDataApiKey,
            ApiKeyConfig apiKeys) {
        return new IngestionSettings(pageDelayMs, pageSize, maxPages, csvPath,
                apiKeys.getGyeonggiApiKey(), publicDataApiKey);
    }

    @Bean
    FacilityReader ggCctvReader(IngestionHttp http, IngestionSettings settings, ObjectMapper mapper) {
        return GyeonggiJsonReader.cctv(http, settings, mapper);
    }

    @Bean
    FacilityReader ggSecurityLightReader(IngestionHttp http, IngestionSettings settings, ObjectMapper mapper) {
        return GyeonggiJsonReader.securityLight(http, settings, mapper);
    }

    @Bean
    FacilityReader ggPoliceReader(IngestionHttp http, IngestionSettings settings) {
        return new PoliceXmlReader(http, settings);
    }

    @Bean
    FacilityReader odcloudIlluminationReader(IngestionHttp http, IngestionSettings settings, ObjectMapper mapper) {
        return OdcloudReader.illumination(http, settings, mapper);
    }

    @Bean
    FacilityReader odcloudSeongnamCctvReader(IngestionHttp http, IngestionSettings settings, ObjectMapper mapper) {
        return OdcloudReader.seongnamCctv(http, settings, mapper);
    }

    @Bean
    FacilityReader seongnamCsvReader(IngestionSettings settings) {
        return new SeongnamCsvReader(settings);
    }
}
