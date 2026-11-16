package com.flatmap.navigation.rag;

import java.util.List;

/**
 * RAG(검색 증강 생성) 서버 연동 지점. <b>RAG 는 개발 중이며 이 저장소에는 서버도 구현체도 없다.</b>
 * 나중에 RAG 서버를 붙일 때 이 인터페이스를 구현한 빈을 {@code @Primary} 로 등록하면 된다(기본 "미연결" 구현체보다 우선한다).
 *
 * <p>구현체가 지켜야 할 규칙(실제 호출이 생기는 경우):
 * <ul>
 *   <li>외부 호출에는 연결·읽기 타임아웃을 둔다.</li>
 *   <li>실패하면 내부 메시지(URL·키·응답 본문)를 담지 않은 {@link RagUnavailableException} 을 던진다.</li>
 *   <li>로그에도 URL·키·응답 본문을 남기지 않는다.</li>
 * </ul>
 */
public interface RagClient {

    /** 이 클라이언트가 실제 RAG 서버에 연결돼 있는지. 미연결 구현체는 false 다. */
    boolean isConnected();

    /**
     * 질문에 답한다.
     *
     * @throws RagNotConnectedException 연결된 RAG 서버가 없을 때
     * @throws RagUnavailableException  서버는 있으나 호출에 실패했을 때(일반화된 메시지)
     */
    RagAnswer ask(String question);

    /** 답변과 근거 출처. */
    record RagAnswer(String answer, List<String> sources) {
        public RagAnswer {
            sources = sources == null ? List.of() : List.copyOf(sources);
        }
    }
}
