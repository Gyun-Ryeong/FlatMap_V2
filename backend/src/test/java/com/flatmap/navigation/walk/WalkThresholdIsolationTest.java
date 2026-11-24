package com.flatmap.navigation.walk;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 보행난이도 임계값(3.18° / 4.76° / 7.13°)이 급경사지(붕괴위험 지정지) 데이터에 적용되는 코드 경로가 없음을
 * 소스를 읽어 확인한다(DECISIONS §3·§4, CLAUDE.md "알려진 도메인 주의사항"). 두 축은 별개다.
 */
class WalkThresholdIsolationTest {

    private static final Path MAIN = Path.of("src/main/java");
    private static final Path RESOURCES = Path.of("src/main/resources");

    private static List<Path> javaFiles() throws IOException {
        try (Stream<Path> s = Files.walk(MAIN)) {
            return s.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    private static boolean isWalk(Path p) {
        return p.toString().replace('\\', '/').contains("/navigation/walk/");
    }

    @Test
    void steepSlopeCodeNeverTouchesTheWalkPackageOrThresholds() throws IOException {
        List<Path> steepSlopeSide = javaFiles().stream()
                .filter(p -> {
                    String n = p.getFileName().toString();
                    return n.startsWith("SteepSlope") || n.equals("RiskSection.java") || n.equals("ElevationService.java")
                            || n.equals("DetourService.java") || n.equals("DetourRouteService.java");
                }).toList();
        assertThat(steepSlopeSide).as("검사 대상 파일을 찾았다").hasSizeGreaterThanOrEqualTo(6);

        for (Path p : steepSlopeSide) {
            String src = Files.readString(p);
            String code = stripComments(src);
            assertThat(code).as("%s 가 walk 패키지를 쓴다", p.getFileName())
                    .doesNotContain("navigation.walk.WalkModel")
                    .doesNotContain("navigation.walk.WalkAssessor")
                    .doesNotContain("navigation.walk.SlopeClass")
                    .doesNotContain("walk.threshold");
            assertThat(code).as("%s 에 보행 임계값 상수가 있다", p.getFileName())
                    .doesNotContain("3.18").doesNotContain("4.76").doesNotContain("7.13");
        }
    }

    @Test
    void walkCodeNeverReadsTheSteepSlopeTable() throws IOException {
        for (Path p : javaFiles().stream().filter(WalkThresholdIsolationTest::isWalk).toList()) {
            String code = stripComments(Files.readString(p));
            assertThat(code).as("%s 가 급경사지를 참조한다", p.getFileName())
                    .doesNotContain("steep_slope").doesNotContain("SteepSlope").doesNotContain("RiskSection");
        }
    }

    @Test
    void thresholdsLiveOnlyInConfigurationAndTheLegacyThresholdHelperIsGone() throws IOException {
        // 임계값 숫자는 설정 파일 한 곳에만 있다(코드에 하드코딩하지 않는다)
        for (Path p : javaFiles()) {
            String code = stripComments(Files.readString(p));
            assertThat(code).as("%s 에 임계값이 하드코딩돼 있다", p.getFileName())
                    .doesNotContain("3.18").doesNotContain("7.13");
        }
        assertThat(Files.exists(MAIN.resolve("com/flatmap/navigation/util/SlopeRisk.java")))
                .as("급경사지 등급에 보행 임계값을 쓰던 옛 도우미는 삭제됐다").isFalse();
        String props = Files.readString(RESOURCES.resolve("application.properties"));
        assertThat(props).contains("walk.threshold.weak.good-max-deg=3.18")
                .contains("walk.threshold.general.caution-max-deg=7.13");
    }

    /** 줄 주석·블록 주석을 지운다(문서의 설명이 검사에 걸리지 않도록). 문자열 안의 // 는 고려하지 않는 단순 처리. */
    private static String stripComments(String src) {
        return src.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\n]*", "");
    }
}
