package com.flatmap.navigation.rag;

/** 연결된 RAG 서버가 없다(개발 중). 컨트롤러가 503 {@code RAG_NOT_CONNECTED} 로 바꾼다. */
public class RagNotConnectedException extends RuntimeException {

    public RagNotConnectedException() {
        super("RAG 서버가 연결되어 있지 않다");
    }
}
