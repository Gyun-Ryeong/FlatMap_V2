package com.flatmap.navigation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flatmap.navigation.config.ApiKeyConfig;
import com.flatmap.navigation.dto.RouteResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** 외부 호출은 MockRestServiceServer 로 모킹한다 — 실제 T Map 을 부르지 않는다. */
class TMapRouteServiceTest {

    private static final String URL = "https://apis.openapi.sk.com/tmap/routes/pedestrian?version=1";

    private static final String OK_BODY = """
            {"features":[
              {"geometry":{"type":"Point","coordinates":[127.1,37.4]},
               "properties":{"totalDistance":900,"totalTime":700}},
              {"geometry":{"type":"LineString","coordinates":[[127.1,37.4],[127.1,37.41]]},"properties":{}}
            ]}""";

    private MockRestServiceServer server;
    private TMapRouteService service;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        ApiKeyConfig keys = Mockito.mock(ApiKeyConfig.class);
        Mockito.when(keys.getTmapAppKey()).thenReturn("test-only-key");
        service = new TMapRouteService(restTemplate, keys, new ObjectMapper());
    }

    private void expectOption(int option, org.springframework.test.web.client.ResponseCreator response) throws Exception {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("appKey", "test-only-key"))
                .andExpect(jsonPath("$.searchOption").value(String.valueOf(option)))
                .andRespond(response);
    }

    @Test
    void requestsThreeCandidatesWithSearchOptions0_4_10InOrder() throws Exception {
        expectOption(0, withSuccess(OK_BODY, MediaType.APPLICATION_JSON));
        expectOption(4, withSuccess(OK_BODY, MediaType.APPLICATION_JSON));
        expectOption(10, withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        List<TMapRouteService.CandidateRoute> result = service.getCandidateRoutes(37.4, 127.1, 37.41, 127.1);

        server.verify();
        assertThat(result).extracting(TMapRouteService.CandidateRoute::searchOption).containsExactly(0, 4, 10);
        assertThat(result).extracting(TMapRouteService.CandidateRoute::name).containsExactly("일반", "대로우선", "최단");
        RouteResponse first = result.get(0).route();
        assertThat(first.getTotalDistance()).isEqualTo(900);
        assertThat(first.getTotalDuration()).isEqualTo(700);
        assertThat(first.getCoords()).hasSize(2);
        assertThat(first.getCoords().get(0).getLat()).isEqualTo(37.4);   // [경도, 위도] → (위도, 경도)
        assertThat(first.getCoords().get(0).getLng()).isEqualTo(127.1);
    }

    @Test
    void oneFailingCandidateDoesNotStopTheOthers() throws Exception {
        expectOption(0, withSuccess(OK_BODY, MediaType.APPLICATION_JSON));
        expectOption(4, withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        expectOption(10, withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        List<TMapRouteService.CandidateRoute> result = service.getCandidateRoutes(37.4, 127.1, 37.41, 127.1);

        server.verify();
        assertThat(result).extracting(TMapRouteService.CandidateRoute::searchOption).containsExactly(0, 10);
    }

    @Test
    void unparsableOrEmptyResponsesAreSkippedToo() throws Exception {
        expectOption(0, withSuccess("not json at all", MediaType.APPLICATION_JSON));
        expectOption(4, withSuccess("{\"features\":[]}", MediaType.APPLICATION_JSON));
        expectOption(10, withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        List<TMapRouteService.CandidateRoute> result = service.getCandidateRoutes(37.4, 127.1, 37.41, 127.1);

        assertThat(result).extracting(TMapRouteService.CandidateRoute::searchOption).containsExactly(10);
    }

    @Test
    void allFailingReturnsEmptyListInsteadOfThrowing() throws Exception {
        expectOption(0, withStatus(HttpStatus.UNAUTHORIZED));
        expectOption(4, withStatus(HttpStatus.UNAUTHORIZED));
        expectOption(10, withStatus(HttpStatus.UNAUTHORIZED));

        assertThat(service.getCandidateRoutes(37.4, 127.1, 37.41, 127.1)).isEmpty();
    }

    @Test
    void singleRouteKeepsTheOptionMappingAndFailsWithAGenericMessage() throws Exception {
        server.expect(requestTo(URL)).andExpect(jsonPath("$.searchOption").value("10"))
                .andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));
        assertThat(service.getWalkingRoute(127.1, 37.4, 127.1, 37.41, "SHORT").getTotalDistance()).isEqualTo(900);

        server.reset();
        server.expect(requestTo(URL)).andExpect(jsonPath("$.searchOption").value("4"))
                .andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));
        assertThat(service.getWalkingRoute(127.1, 37.4, 127.1, 37.41, "MAIN_ROAD").getTotalDistance()).isEqualTo(900);

        server.reset();
        server.expect(requestTo(URL)).andExpect(jsonPath("$.passList").value("127.1,37.405"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).body("secret-internal-detail").contentType(MediaType.TEXT_PLAIN));
        assertThatThrownBy(() -> service.getWalkingRoute(127.1, 37.4, 127.1, 37.41, "SAFE", "127.1,37.405"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("경로 검색에 실패했습니다.")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("secret-internal-detail"));
    }
}
