package com.flatmap.navigation.deploy;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 배포 산출물(Dockerfile, compose, nginx 설정)의 구조 검사. Docker 가 없는 환경(이 프로젝트 개발 PC, CI 의 backend 잡)에서도
 * 돌 수 있는 정적 검사다 — 이미지를 실제로 빌드·실행하는 검증은 아니다.
 * 운영에 위험한 설정(DB 포트 공개, 비밀값 리터럴, root 실행, ngrok 의존)이 다시 들어오는 것을 막는다.
 */
class DeployFilesTest {

    private static final Path ROOT = Path.of("..");

    private static String read(String relative) throws IOException {
        return Files.readString(ROOT.resolve(relative));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> yaml(String relative) throws IOException {
        return (Map<String, Object>) new Yaml().load(read(relative));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> service(Map<String, Object> compose, String name) {
        return (Map<String, Object>) ((Map<String, Object>) compose.get("services")).get(name);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> env(Map<String, Object> service) {
        Object e = service.get("environment");
        return e == null ? Map.of() : (Map<String, Object>) e;
    }

    // ---- 운영 compose ----

    @Test
    void prodComposeHasExactlyDbBackendFrontend() throws IOException {
        Map<String, Object> compose = yaml("docker-compose.prod.yml");
        @SuppressWarnings("unchecked")
        Map<String, Object> services = (Map<String, Object>) compose.get("services");
        assertThat(services.keySet()).containsExactlyInAnyOrder("db", "backend", "frontend");
    }

    @Test
    void prodDbIsNeverPublishedToTheHostAndUsesAPinnedPostgisImage() throws IOException {
        Map<String, Object> db = service(yaml("docker-compose.prod.yml"), "db");
        assertThat(db).doesNotContainKey("ports");
        String image = (String) db.get("image");
        assertThat(image).startsWith("postgis/postgis:").doesNotEndWith(":latest");
        assertThat(db).containsKey("healthcheck");
        assertThat(db.get("volumes").toString()).contains("/var/lib/postgresql/data");
        // 개발용 초기화 스크립트(테스트 DB 생성)를 운영 DB 에 마운트하지 않는다
        assertThat(db.get("volumes").toString()).doesNotContain("initdb");
    }

    @Test
    @SuppressWarnings("unchecked")
    void prodBackendIsOnlyReachableFromLocalhostAndRunsWithTheProdProfile() throws IOException {
        Map<String, Object> backend = service(yaml("docker-compose.prod.yml"), "backend");
        for (String port : (List<String>) backend.get("ports")) {
            assertThat(port).as("백엔드 포트는 호스트의 127.0.0.1 에만 연다").startsWith("127.0.0.1:");
        }
        assertThat(env(backend).get("SPRING_PROFILES_ACTIVE")).isEqualTo("prod");
        assertThat(backend.get("env_file").toString()).contains(".env");
        assertThat(backend.get("depends_on").toString()).contains("service_healthy");
        assertThat(backend.get("cap_drop").toString()).contains("ALL");
        assertThat(backend.get("security_opt").toString()).contains("no-new-privileges:true");
        // 데이터 파일은 읽기 전용으로만 마운트한다
        assertThat(backend.get("volumes").toString()).contains(":/data:ro");
        // 컨테이너 안의 DB 주소는 내부 네트워크 이름을 쓴다(.env 의 호스트용 주소가 아님)
        assertThat((String) env(backend).get("DB_URL")).contains("jdbc:postgresql://db:5432/");
    }

    @Test
    @SuppressWarnings("unchecked")
    void prodFrontendIsTheOnlyPublicEntryPoint() throws IOException {
        Map<String, Object> frontend = service(yaml("docker-compose.prod.yml"), "frontend");
        assertThat((List<String>) frontend.get("ports")).hasSize(1).allSatisfy(p -> assertThat(p).endsWith(":8080"));
        assertThat((String) env(frontend).get("BACKEND_URL")).isEqualTo("http://backend:8080");
        // 빌드 인자는 변수 참조이거나 빈 값이다(리터럴 키를 파일에 쓰지 않는다)
        Map<String, Object> args = (Map<String, Object>) ((Map<String, Object>) frontend.get("build")).get("args");
        args.forEach((k, v) -> assertThat(String.valueOf(v)).as("build arg " + k).matches("(\\$\\{.+\\})?"));
    }

    @Test
    void prodComposeHoldsNoSecretLiterals() throws IOException {
        Map<String, Object> compose = yaml("docker-compose.prod.yml");
        List<String> literals = new ArrayList<>();
        for (String name : List.of("db", "backend", "frontend")) {
            env(service(compose, name)).forEach((key, value) -> {
                String v = value == null ? "" : String.valueOf(value);
                boolean reference = v.contains("${");
                boolean allowed = switch (key) {
                    case "SPRING_PROFILES_ACTIVE" -> v.equals("prod");
                    case "BACKEND_URL" -> v.equals("http://backend:8080");
                    case "WALK_DEM_PATH", "SEONGNAM_SECURITY_LIGHT_CSV_PATH" -> v.startsWith("/data/");
                    default -> false;
                };
                if (!reference && !allowed) literals.add(name + "." + key);
            });
        }
        assertThat(literals).as("환경변수는 ${...} 참조여야 한다(리터럴 값 금지)").isEmpty();
    }

    @Test
    void prodComposeRequiresTheSettingsThatHaveNoSafeDefault() throws IOException {
        String text = read("docker-compose.prod.yml");
        for (String required : List.of("DB_NAME", "DB_USERNAME", "DB_PASSWORD", "FRONTEND_URL", "KAKAO_OAUTH_REDIRECT_URI",
                "JWT_SECRET", "ADMIN_API_KEY", "VITE_KAKAO_API_KEY")) {
            assertThat(text).as(required + " 는 없으면 시작하지 않아야 한다").contains("${" + required + ":?");
        }
        // 빈 값이 기본값을 덮는 것을 막기 위해 Secure 쿠키 기본값을 compose 에서 채운다
        assertThat(text).contains("COOKIE_SECURE: ${COOKIE_SECURE:-true}");
    }

    // ---- 개발 compose ----

    @Test
    @SuppressWarnings("unchecked")
    void devComposeStartsOnlyTheDatabaseUnlessTheStackProfileIsEnabled() throws IOException {
        Map<String, Object> compose = yaml("docker-compose.yml");
        assertThat(service(compose, "db")).doesNotContainKey("profiles");
        assertThat((List<String>) service(compose, "backend").get("profiles")).containsExactly("stack");
        assertThat((List<String>) service(compose, "frontend").get("profiles")).containsExactly("stack");
        // 이미지는 운영 프로필이 기본이라 개발 스택은 프로필을 비워 개발 설정을 쓴다
        assertThat(env(service(compose, "backend")).get("SPRING_PROFILES_ACTIVE")).isEqualTo("");
    }

    @Test
    void devStackServicesDoNotForceRequiredVariablesBecauseTheWholeFileIsInterpolated() throws IOException {
        String text = read("docker-compose.yml");
        int stackStart = text.indexOf("# ---- 전체 스택");
        assertThat(stackStart).isPositive();
        assertThat(text.substring(stackStart)).doesNotContain(":?");
    }

    // ---- Dockerfile ----

    private static List<String> lines(String relative) throws IOException {
        return Files.readAllLines(ROOT.resolve(relative));
    }

    private static List<String> instructions(String relative, String keyword) throws IOException {
        return lines(relative).stream().map(String::strip).filter(l -> l.toUpperCase().startsWith(keyword + " ")).toList();
    }

    @Test
    void dockerfilesAreMultiStageAndTheBackendRunsAsANonRootUser() throws IOException {
        for (String f : List.of("backend/Dockerfile", "frontend/Dockerfile")) {
            assertThat(instructions(f, "FROM")).as(f + " 멀티스테이지").hasSizeGreaterThanOrEqualTo(2);
            assertThat(instructions(f, "COPY")).as(f).anyMatch(l -> l.contains("--from=build"));
            assertThat(instructions(f, "HEALTHCHECK")).as(f + " 헬스체크").isNotEmpty();
            assertThat(instructions(f, "EXPOSE")).as(f).isNotEmpty();
        }
        List<String> users = instructions("backend/Dockerfile", "USER");
        assertThat(users).hasSize(1);
        assertThat(users.get(0).substring(5).strip()).isNotIn("root", "0");
        // 프론트의 실행 이미지는 비루트 nginx 이다(8080)
        List<String> from = instructions("frontend/Dockerfile", "FROM");
        assertThat(from.get(from.size() - 1)).contains("nginx-unprivileged");
        assertThat(instructions("backend/Dockerfile", "FROM").get(0)).contains("eclipse-temurin-17");   // 프로젝트 목표 Java 17
    }

    @Test
    void dockerfilesHaveOnlyValidInstructionsAndNoBrokenEscapes() throws IOException {
        List<String> valid = List.of("FROM", "RUN", "COPY", "ADD", "ENV", "ARG", "WORKDIR", "USER", "EXPOSE", "HEALTHCHECK",
                "ENTRYPOINT", "CMD", "LABEL", "VOLUME", "SHELL", "STOPSIGNAL", "ONBUILD");
        for (String f : List.of("backend/Dockerfile", "frontend/Dockerfile")) {
            // 줄 끝의 \ 로 이어진 줄을 한 명령으로 합친다
            String joined = String.join("\n", lines(f)).replaceAll("\\\\[ \\t]*\\n", " ");
            for (String logical : joined.split("\n")) {
                String line = logical.strip();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String keyword = line.split("\\s+")[0].toUpperCase();
                assertThat(valid).as(f + " 알 수 없는 명령: " + line).contains(keyword);
                // 이스케이프가 깨져 \n 글자가 그대로 들어간 경우(줄바꿈이 아님)
                assertThat(line).as(f + " 깨진 줄바꿈: " + line).doesNotContain("\\n");
            }
        }
    }

    @Test
    void dockerfilesNeverBakeSecretsOrCopyEnvFiles() throws IOException {
        Pattern secretName = Pattern.compile("(?i)(PASSWORD|SECRET|TOKEN|API_?KEY|APP_?KEY)");
        for (String f : List.of("backend/Dockerfile", "frontend/Dockerfile")) {
            for (String l : lines(f)) {
                String line = l.strip();
                if (line.startsWith("#")) continue;
                if (line.toUpperCase().startsWith("COPY ") || line.toUpperCase().startsWith("ADD ")) {
                    assertThat(line).as(f + ": " + line).doesNotContain(".env");
                }
            }
            // ENV·ARG 의 값: 비밀스러운 이름에는 리터럴 값(기본값 포함)을 두지 않는다. ARG 이름만(값 없음), 또는 다른 변수 참조만 허용.
            String joined = String.join("\n", lines(f)).replace("\\\n", " ");
            Matcher m = Pattern.compile("(?m)^\\s*(ENV|ARG)\\s+(.+)$").matcher(joined);
            while (m.find()) {
                for (String pair : m.group(2).trim().split("\\s+(?=[A-Za-z_][A-Za-z0-9_]*=)")) {
                    String[] kv = pair.split("=", 2);
                    String key = kv[0].trim();
                    if (!secretName.matcher(key).find() || kv.length < 2) continue;
                    String value = kv[1].trim().replaceAll("\\\\$", "").trim();
                    assertThat(value).as(f + " " + key).matches("(\\$[A-Za-z_][A-Za-z0-9_]*|\\$\\{[^}]+\\})?");
                }
            }
        }
    }

    @Test
    void dockerIgnoreFilesKeepEnvFilesAndLocalSettingsOutOfTheBuildContext() throws IOException {
        for (String f : List.of("backend/.dockerignore", "frontend/.dockerignore")) {
            List<String> l = lines(f).stream().map(String::strip).toList();
            assertThat(l).as(f).contains(".env", "**/.env");
        }
        assertThat(lines("backend/.dockerignore")).contains("**/application-local.*", "dem/", "target/");
        assertThat(lines("frontend/.dockerignore")).contains("node_modules/");
    }

    // ---- nginx ----

    @Test
    void nginxProxiesApiSameOriginAndKeepsAdminPathsOffThePublicAddress() throws IOException {
        String conf = read("frontend/nginx/default.conf.template");
        // 백엔드 주소는 요청 때마다 다시 해석한다(컨테이너 IP 가 바뀌어도 502 가 나지 않게)
        assertThat(conf).contains("resolver ${NGINX_RESOLVER}").contains("set $backend_upstream ${BACKEND_URL};");
        assertThat(conf).contains("proxy_pass $backend_upstream;").doesNotContain("proxy_pass ${BACKEND_URL}");
        // 관리용 경로는 공개 주소로 열지 않는다
        assertThat(conf).contains("^/api/(data|walk-difficulty)(/|$)").contains("return 404;");
        assertThat(conf).contains("^/api/(safety|steep-slope|density-grid)(/|$)").contains("limit_except GET HEAD");
        assertThat(conf).contains("try_files $uri $uri/ /index.html;");        // SPA
        assertThat(conf).contains("X-Content-Type-Options");
        assertThat(conf).doesNotContainIgnoringCase("localhost").doesNotContainIgnoringCase("ngrok");
        // 템플릿의 환경변수 치환은 BACKEND_URL, NGINX_RESOLVER 뿐이어야 한다(그 밖의 ${...} 는 nginx 설정이 아니다)
        Matcher m = Pattern.compile("\\$\\{([A-Za-z_]+)\\}").matcher(conf);
        List<String> vars = new ArrayList<>();
        while (m.find()) vars.add(m.group(1));
        assertThat(vars).containsOnly("BACKEND_URL", "NGINX_RESOLVER");
    }

    // ---- ngrok·하드코딩 호스트 ----

    @Test
    void noNgrokDependencyAnywhereInTheRepositoryFiles() throws IOException {
        List<Path> roots = List.of(Path.of("src"), ROOT.resolve("frontend/src"), ROOT.resolve("docs"), ROOT.resolve(".github"));
        List<Path> single = List.of(ROOT.resolve("README.md"), ROOT.resolve("docker-compose.yml"), ROOT.resolve("docker-compose.prod.yml"),
                ROOT.resolve(".env.example"), ROOT.resolve("frontend/.env.example"), ROOT.resolve("frontend/vite.config.js"),
                ROOT.resolve("frontend/index.html"), ROOT.resolve("frontend/package.json"));
        List<Path> files = new ArrayList<>(single.stream().filter(Files::exists).toList());
        for (Path root : roots) {
            if (!Files.exists(root)) continue;
            try (Stream<Path> walk = Files.walk(root)) {
                walk.filter(Files::isRegularFile)
                        .filter(p -> !p.toString().replace('\\', '/').contains("/docs/dev/"))
                        .filter(p -> !p.getFileName().toString().equals("DeployFilesTest.java"))   // 이 검사 자신
                        .filter(p -> p.toString().matches(".*\\.(java|js|jsx|json|md|yml|yaml|properties|html|css|conf|template|sh|sql)$"))
                        .forEach(files::add);
            }
        }
        List<String> hits = new ArrayList<>();
        for (Path p : files) {
            if (Files.readString(p).toLowerCase().contains("ngrok")) hits.add(p.toString());
        }
        assertThat(hits).as("ngrok 참조가 있는 파일").isEmpty();
    }

    @Test
    void frontendSourceHardCodesNoHost() throws IOException {
        try (Stream<Path> walk = Files.walk(ROOT.resolve("frontend/src"))) {
            List<String> hits = new ArrayList<>();
            for (Path p : walk.filter(Files::isRegularFile).filter(p -> p.toString().matches(".*\\.(js|jsx)$"))
                    .filter(p -> !p.toString().replace('\\', '/').contains("/__tests__/")).toList()) {
                String text = Files.readString(p);
                if (text.contains("http://localhost") || text.contains("https://localhost") || text.contains("127.0.0.1")) {
                    hits.add(p.toString());
                }
            }
            assertThat(hits).as("프론트 소스에 하드코딩된 호스트").isEmpty();
        }
    }
}
