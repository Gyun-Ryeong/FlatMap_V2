package com.flatmap.navigation.deploy;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 환경변수 문서(docs/ENVIRONMENT.md)가 .env.example 들과, 그리고 코드·compose 가 실제로 읽는 환경변수와 어긋나지 않는지 확인한다.
 * 새 환경변수를 추가하고 문서를 빼먹으면 이 테스트가 실패한다. 값은 어디에도 없다 — 키 이름만 비교한다.
 */
class EnvDocsConsistencyTest {

    private static final Path ROOT = Path.of("..");

    private static String read(String relative) throws IOException {
        return Files.readString(ROOT.resolve(relative));
    }

    /** .env.example 의 키: "KEY=" 로 시작하는 줄과 주석 처리된 "# KEY=" 줄(선택 항목의 예시). */
    private static Set<String> exampleKeys(String relative) throws IOException {
        Set<String> keys = new TreeSet<>();
        Matcher m = Pattern.compile("(?m)^#?\\s?([A-Z][A-Z0-9_]+)=").matcher(read(relative));
        while (m.find()) keys.add(m.group(1));
        return keys;
    }

    private static Set<String> documentedKeys() throws IOException {
        Set<String> keys = new TreeSet<>();
        Matcher m = Pattern.compile("(?m)^\\| `([A-Z][A-Z0-9_]+)` \\|").matcher(read("docs/ENVIRONMENT.md"));
        while (m.find()) keys.add(m.group(1));
        return keys;
    }

    @Test
    void theDocumentListsExactlyTheKeysOfTheTwoExampleFiles() throws IOException {
        Set<String> examples = new TreeSet<>(exampleKeys(".env.example"));
        examples.addAll(exampleKeys("frontend/.env.example"));
        Set<String> documented = documentedKeys();

        Set<String> missingInDoc = new TreeSet<>(examples);
        missingInDoc.removeAll(documented);
        Set<String> missingInExample = new TreeSet<>(documented);
        missingInExample.removeAll(examples);

        assertThat(missingInDoc).as(".env.example 에는 있고 ENVIRONMENT.md 에는 없는 키").isEmpty();
        assertThat(missingInExample).as("ENVIRONMENT.md 에는 있고 .env.example 에는 없는 키").isEmpty();
        assertThat(examples).hasSizeGreaterThan(30);
    }

    @Test
    void everyEnvironmentVariableTheComposeFilesReadIsDocumented() throws IOException {
        Set<String> documented = documentedKeys();
        for (String file : List.of("docker-compose.yml", "docker-compose.prod.yml")) {
            Matcher m = Pattern.compile("(?<!\\$)\\$\\{([A-Z][A-Z0-9_]*)").matcher(read(file));
            while (m.find()) {
                assertThat(documented).as(file + " 가 읽는 " + m.group(1)).contains(m.group(1));
            }
        }
    }

    @Test
    void everyEnvironmentVariableTheApplicationPropertiesReadIsDocumented() throws IOException {
        Set<String> documented = documentedKeys();
        for (String file : List.of("backend/src/main/resources/application.properties",
                "backend/src/main/resources/application-prod.properties")) {
            Matcher m = Pattern.compile("\\$\\{([A-Z][A-Z0-9_]*)[:}]").matcher(read(file));
            while (m.find()) {
                assertThat(documented).as(file + " 가 읽는 " + m.group(1)).contains(m.group(1));
            }
        }
    }

    @Test
    void theDocumentNeverContainsValuesOnlyNames() throws IOException {
        // 키=값 형태(대문자 키 다음에 = 와 값)가 문서에 없어야 한다
        assertThat(read("docs/ENVIRONMENT.md")).doesNotContainPattern("\\b[A-Z][A-Z0-9_]{3,}=[^\\s|`]{6,}");
    }

    @Test
    void requiredKeysAreMarkedRequired() throws IOException {
        String doc = read("docs/ENVIRONMENT.md");
        for (String key : List.of("DB_NAME", "DB_USERNAME", "DB_PASSWORD", "DB_URL", "JWT_SECRET")) {
            assertThat(doc).containsPattern("\\| `" + key + "` \\|[^\\n]*\\*\\*필수\\*\\*");
        }
        for (String key : List.of("ADMIN_API_KEY", "FRONTEND_URL", "KAKAO_OAUTH_REDIRECT_URI")) {
            assertThat(doc).containsPattern("\\| `" + key + "` \\|[^\\n]*\\*\\*운영 필수\\*\\*");
        }
    }
}
