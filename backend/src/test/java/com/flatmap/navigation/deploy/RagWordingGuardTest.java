package com.flatmap.navigation.deploy;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 문구 규칙(CLAUDE.md 규칙 6, DECISIONS §6) 가드: RAG 는 개발 중이며 아직 연동되지 않았다.
 * 사용자가 읽는 문서·설정 파일과 화면(컴포넌트·페이지)에서 "RAG" 를 언급하는 모든 줄은 "개발 중"·"미연결"·"저장소에 없" 중 하나를 함께 말해야 한다.
 * 실제 RAG 서버 구현이 이 저장소에 들어오기 전에 "있는 기능"처럼 쓰이는 문장이 생기면 이 테스트가 실패한다.
 * (변경 기록(CHANGELOG)·로컬 작업 문서(docs/dev)는 제외한다.)
 */
class RagWordingGuardTest {

    private static final Path ROOT = Path.of("..");
    private static final Pattern RAG = Pattern.compile("\\bRAG\\b");
    private static final List<String> HONEST = List.of("개발 중", "미연결", "저장소에 없");

    private static List<Path> userFacingFiles() throws IOException {
        List<Path> files = new ArrayList<>();
        for (String f : List.of("README.md", "docs/ENVIRONMENT.md", "docs/DEPLOY.md", ".env.example", "frontend/.env.example")) {
            if (Files.exists(ROOT.resolve(f))) files.add(ROOT.resolve(f));
        }
        // 화면(컴포넌트·페이지)과 설정 파일. 내부 코드의 주석·로그·예외 메시지는 사용자에게 보이지 않아 제외한다
        // (API 응답 문구의 "개발 중" 은 RagEnabledNotConnectedTest 가 고정한다).
        for (String dir : List.of("frontend/src/components", "frontend/src/pages", "backend/src/main/resources")) {
            try (Stream<Path> walk = Files.walk(ROOT.resolve(dir))) {
                walk.filter(Files::isRegularFile)
                        .filter(p -> p.toString().matches(".*\\.(jsx?|properties|md)$"))
                        .filter(p -> !p.toString().replace('\\', '/').contains("/__tests__/"))
                        .forEach(files::add);
            }
        }
        return files;
    }

    @Test
    void everyMentionOfRagSaysItIsUnderDevelopmentOrNotConnected() throws IOException {
        List<String> violations = new ArrayList<>();
        int mentions = 0;
        for (Path file : userFacingFiles()) {
            List<String> lines = Files.readAllLines(file);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (!RAG.matcher(line).find()) continue;
                mentions++;
                // 같은 줄, 또는 문장이 다음 줄로 이어지는 주석/문단(다음 줄)에서 솔직하게 말해야 한다
                String context = line + (i + 1 < lines.size() ? " " + lines.get(i + 1) : "");
                if (HONEST.stream().noneMatch(context::contains)) {
                    violations.add(ROOT.relativize(file) + ":" + (i + 1) + "  " + line.strip());
                }
            }
        }
        assertThat(mentions).as("RAG 언급을 거의 못 찾았다면 검사 대상 경로가 틀린 것").isGreaterThan(8);
        assertThat(violations).as("'개발 중/미연결' 없이 RAG 를 언급한 줄").isEmpty();
    }

    @Test
    void readmeNeverDescribesRagAsAnAvailableFeature() throws IOException {
        String readme = Files.readString(ROOT.resolve("README.md"));
        // "구현된 기능" 절에는 RAG 가 없어야 한다(개발 중 절에만 있다)
        int implemented = readme.indexOf("## 구현된 기능");
        int underDevelopment = readme.indexOf("**개발 중 (아직 연동되지 않음)**");
        assertThat(implemented).isPositive();
        assertThat(underDevelopment).isGreaterThan(implemented);
        assertThat(readme.substring(implemented, underDevelopment)).doesNotContain("RAG");
        // 기능을 약속하는 표현이 없다
        assertThat(readme).doesNotContainPattern("RAG[^\\n]*(지원합니다|제공합니다|사용할 수 있습니다|가능합니다|답변해 줍니다)");
    }
}
