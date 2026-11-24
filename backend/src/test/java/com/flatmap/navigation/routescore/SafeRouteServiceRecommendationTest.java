package com.flatmap.navigation.routescore;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SafeRouteServiceRecommendationTest {

    private static ScoredRoute route(String id, double score, int time) {
        return new ScoredRoute(id, List.of(), 100, time, 0, "일반", score, List.of(), 0, 0, 0, null, false);
    }

    @Test
    void highestTotalScoreIsRecommended() {
        List<ScoredRoute> out = SafeRouteService.markRecommended(
                List.of(route("a", 40, 600), route("b", 70, 900), route("c", 55, 500)));
        assertThat(out).extracting(ScoredRoute::recommended).containsExactly(false, true, false);
    }

    @Test
    void tieGoesToTheFasterRoute() {
        List<ScoredRoute> out = SafeRouteService.markRecommended(
                List.of(route("a", 50, 900), route("b", 50, 600)));
        assertThat(out).extracting(ScoredRoute::recommended).containsExactly(false, true);
    }

    @Test
    void fullTieKeepsTheFirstAndExactlyOneIsRecommended() {
        List<ScoredRoute> out = SafeRouteService.markRecommended(
                List.of(route("a", 50, 600), route("b", 50, 600)));
        assertThat(out).extracting(ScoredRoute::recommended).containsExactly(true, false);
    }

    @Test
    void emptyStaysEmpty() {
        assertThat(SafeRouteService.markRecommended(List.of())).isEmpty();
    }
}
