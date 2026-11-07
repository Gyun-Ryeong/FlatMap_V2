package com.flatmap.navigation;

import com.flatmap.navigation.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 컨텍스트가 뜬다는 것은 (1) Flyway 마이그레이션이 적용되고 (2) Hibernate ddl-auto=validate 가
 * 남아 있는 JPA 엔티티와 스키마의 일치를 확인했다는 뜻이다.
 */
@IntegrationTest
class ApplicationContextTest {

    @Autowired
    ApplicationContext context;

    @Test
    void contextLoads() {
        assertThat(context.getBean(DataSource.class)).isNotNull();
    }
}
