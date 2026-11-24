package com.flatmap.navigation.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flatmap.navigation.config.AdminApiKeyFilter;
import com.flatmap.navigation.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /api/safety/facilities/sync — 외부 HTTP 는 모킹한다.
 * 응답에는 소스 이름별 성공/실패와 건수만 있어야 하고, 내부 메시지(URL·키·예외)는 없어야 한다.
 */
@IntegrationTest
@TestPropertySource(properties = {
        "gyeonggi.api-key=test-gg-key-should-never-leak",
        "public-data.api-key=test-od-key-should-never-leak",
        "ingestion.page-delay-ms=0",
        "ingestion.seongnam-csv-path="
})
class FacilitySyncEndpointTest {

    private static final String LEAKY = "boom KEY=test-gg-key-should-never-leak internal-host.example jdbc:postgresql";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ObjectMapper mapper;

    @Value("${admin.api-key}")
    String adminApiKey;

    @MockBean
    IngestionHttp http;

    @BeforeEach
    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM safety_facility");
    }

    private void stubHttp() throws IOException {
        String securityLights = "{\"SECRTLGT\":[{\"head\":[{\"list_total_count\":1},{\"RESULT\":{\"CODE\":\"INFO-000\"}}]},"
                + "{\"row\":[{\"SECRT_LGT_DVSN_NM\":\"LED 보안등\",\"REFINE_ROADNM_ADDR\":\"경기도 성남시 분당구 1\","
                + "\"REFINE_WGS84_LAT\":\"37.3650\",\"REFINE_WGS84_LOGT\":\"127.1080\"}]}]}";
        when(http.get(anyString())).thenAnswer(inv -> {
            String url = inv.getArgument(0);
            if (url.contains("/SECRTLGT?")) return securityLights;
            throw new IOException(LEAKY); // CCTV·치안·odcloud 는 실패
        });
    }

    @Test
    void reportsPerSourceStatusAndCountsOnlyWithoutInternalDetails() throws Exception {
        stubHttp();

        MvcResult result = mvc.perform(post("/api/safety/facilities/sync")
                        .header(AdminApiKeyFilter.HEADER, adminApiKey))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).doesNotContain("test-gg-key", "test-od-key", "internal-host", "jdbc:", "boom",
                "IOException", "Exception", "openapi.gg.go.kr", "odcloud");

        JsonNode results = mapper.readTree(body).path("results");
        assertThat(results).hasSize(FacilitySource.values().length);
        List<String> sources = new ArrayList<>();
        for (JsonNode r : results) {
            sources.add(r.path("source").asText());
            // 필드는 이 다섯 개뿐이다
            List<String> fields = new ArrayList<>();
            Iterator<String> it = r.fieldNames();
            it.forEachRemaining(fields::add);
            assertThat(fields).containsExactlyInAnyOrder("source", "status", "read", "excluded", "inserted");
        }
        assertThat(sources).containsExactlyInAnyOrder("GG_CCTV", "GG_SECURITY_LIGHT", "GG_POLICE",
                "ODCLOUD_ILLUMINATION", "ODCLOUD_SEONGNAM_CCTV", "SEONGNAM_CSV");

        for (JsonNode r : results) {
            if (r.path("source").asText().equals("GG_SECURITY_LIGHT")) {
                assertThat(r.path("status").asText()).isEqualTo("SUCCESS");
                assertThat(r.path("inserted").asInt()).isEqualTo(1);
            } else {
                assertThat(r.path("status").asText()).isEqualTo("FAILED");
                assertThat(r.path("inserted").asInt()).isZero();
            }
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM safety_facility", Long.class)).isEqualTo(1);
    }

    @Test
    void secondRunInsertsNothing() throws Exception {
        stubHttp();
        mvc.perform(post("/api/safety/facilities/sync?source=GG_SECURITY_LIGHT")
                .header(AdminApiKeyFilter.HEADER, adminApiKey)).andExpect(status().isOk());

        MvcResult second = mvc.perform(post("/api/safety/facilities/sync?source=gg_security_light")
                        .header(AdminApiKeyFilter.HEADER, adminApiKey))
                .andExpect(status().isOk()).andReturn();

        JsonNode results = mapper.readTree(second.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("results");
        assertThat(results).hasSize(1);
        assertThat(results.get(0).path("status").asText()).isEqualTo("SUCCESS");
        assertThat(results.get(0).path("inserted").asInt()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM safety_facility", Long.class)).isEqualTo(1);
    }

    @Test
    void unknownSourceIsBadRequestWithoutEchoingInput() throws Exception {
        MvcResult result = mvc.perform(post("/api/safety/facilities/sync?source=DROP_TABLE_users")
                        .header(AdminApiKeyFilter.HEADER, adminApiKey))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).doesNotContain("DROP_TABLE");
    }

    @Test
    void withoutApiKeyIsUnauthorizedAndNothingRuns() throws Exception {
        mvc.perform(post("/api/safety/facilities/sync")).andExpect(status().isUnauthorized());
        org.mockito.Mockito.verifyNoInteractions(http);
    }
}
