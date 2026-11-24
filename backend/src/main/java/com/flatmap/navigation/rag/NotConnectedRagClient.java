package com.flatmap.navigation.rag;

/**
 * 기본 구현체: RAG 서버가 연결되어 있지 않음을 나타낸다. 어떤 외부 호출도 하지 않는다.
 * {@code rag.enabled=true} 일 때만 등록되며({@link RagConfig}), 실제 구현체가 {@code @Primary} 로 등록되면 그쪽이 우선한다.
 */
public class NotConnectedRagClient implements RagClient {

    @Override
    public boolean isConnected() {
        return false;
    }

    @Override
    public RagAnswer ask(String question) {
        throw new RagNotConnectedException();
    }
}
