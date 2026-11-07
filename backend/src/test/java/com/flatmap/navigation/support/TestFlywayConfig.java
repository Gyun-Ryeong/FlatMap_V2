package com.flatmap.navigation.support;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 테스트 컨텍스트가 뜰 때마다 DB 를 비우고(clean) 마이그레이션을 처음부터 적용한다.
 * 개발 DB 를 실수로 지우지 않도록 DB 이름이 _test 로 끝나지 않으면 거부한다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestFlywayConfig {

    @Bean
    FlywayMigrationStrategy cleanThenMigrate(@Value("${spring.datasource.url}") String url) {
        String dbName = databaseName(url);
        if (!dbName.endsWith("_test")) {
            throw new IllegalStateException(
                    "테스트 DB 이름은 _test 로 끝나야 합니다(개발 DB 보호). 현재 DB 이름: " + dbName);
        }
        return flyway -> {
            flyway.clean();
            flyway.migrate();
        };
    }

    static String databaseName(String jdbcUrl) {
        String s = jdbcUrl;
        int q = s.indexOf('?');
        if (q >= 0) s = s.substring(0, q);
        return s.substring(s.lastIndexOf('/') + 1);
    }
}
