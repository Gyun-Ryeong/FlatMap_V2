package com.flatmap.navigation.controller;

import com.flatmap.navigation.config.ApiErrors;
import com.flatmap.navigation.rag.RagClient;
import com.flatmap.navigation.rag.RagNotConnectedException;
import com.flatmap.navigation.rag.RagUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * RAG Q&amp;A 연동 지점. <b>{@code rag.enabled=false}(기본)이면 이 컨트롤러 자체가 등록되지 않아 두 경로 모두 404 다.</b>
 * 켜더라도 RAG 서버 구현이 이 저장소에 없으면 {@code connected=false} 이고 질문은 503 {@code RAG_NOT_CONNECTED} 다.
 * RAG 는 개발 중이며 화면은 {@code enabled && connected} 일 때만 진입점을 보여 준다.
 */
@RestController
@RequestMapping("/api/rag")
@ConditionalOnProperty(prefix = "rag", name = "enabled", havingValue = "true")
public class RagController {

    private static final Logger log = LoggerFactory.getLogger(RagController.class);

    /** 질문 길이 상한(글자). 프론트의 안내와 같은 값이다. */
    static final int MAX_QUESTION_CHARS = 500;

    private final RagClient ragClient;

    public RagController(RagClient ragClient) {
        this.ragClient = ragClient;
    }

    /** 화면이 진입점을 보일지 정하는 데 쓴다. 공개 조회. */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("enabled", true);
        body.put("connected", ragClient.isConnected());
        return ResponseEntity.ok(body);
    }

    /** 로그인한 사용자만 질문할 수 있다(남용·비용 방지). */
    @PostMapping("/ask")
    public ResponseEntity<?> ask(Authentication authentication, @RequestBody(required = false) Map<String, Object> body) {
        if (authentication == null) {
            return ApiErrors.body(HttpStatus.UNAUTHORIZED, "LOGIN_REQUIRED", "로그인이 필요합니다.");
        }
        Object raw = body == null ? null : body.get("question");
        String question = raw instanceof String s ? s.strip() : "";
        if (question.isEmpty() || question.length() > MAX_QUESTION_CHARS) {
            return ApiErrors.body(HttpStatus.BAD_REQUEST, "INVALID_QUESTION",
                    "질문은 1자 이상 " + MAX_QUESTION_CHARS + "자 이하로 입력해 주세요.");
        }
        try {
            RagClient.RagAnswer answer = ragClient.ask(question);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("answer", answer.answer());
            out.put("sources", answer.sources());
            return ResponseEntity.ok(out);
        } catch (RagNotConnectedException e) {
            return ApiErrors.body(HttpStatus.SERVICE_UNAVAILABLE, "RAG_NOT_CONNECTED",
                    "RAG 기반 안내는 개발 중이며 아직 연결되지 않았습니다.");
        } catch (RagUnavailableException e) {
            // 구현체의 메시지에는 내부 정보가 있을 수 있어 응답에 싣지 않고 종류만 남긴다.
            log.warn("RAG 호출 실패: {}", e.getClass().getSimpleName());
            return ApiErrors.body(HttpStatus.BAD_GATEWAY, "RAG_UNAVAILABLE",
                    "답변을 가져오지 못했습니다. 잠시 후 다시 시도해 주세요.");
        }
    }
}
