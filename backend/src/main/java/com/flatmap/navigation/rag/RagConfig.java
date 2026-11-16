package com.flatmap.navigation.rag;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RAG 연동 지점 설정. {@code rag.enabled} 의 기본값은 false 이며, false 이면 이 패키지의 빈과 엔드포인트가 하나도 등록되지 않는다.
 * true 로 켜도 RAG 서버 구현이 이 저장소에 없으므로 "미연결" 구현체가 등록될 뿐이다. RAG 는 개발 중이다.
 *
 * <p>실제 구현체를 붙일 때는 {@link RagClient} 구현 빈을 {@code @Primary} 로 등록한다. 그러면 아래 기본 빈이 같이 있어도
 * 구현체가 주입된다(빈 등록 순서에 의존하지 않는다).
 */
@Configuration
@ConditionalOnProperty(prefix = "rag", name = "enabled", havingValue = "true")
public class RagConfig {

    /** 기본 "미연결" 구현체. 외부 호출을 하지 않는다. 실제 구현체가 @Primary 로 등록되면 그쪽이 쓰인다. */
    @Bean
    RagClient notConnectedRagClient() {
        return new NotConnectedRagClient();
    }
}
