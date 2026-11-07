package com.flatmap.navigation.web;

import com.flatmap.navigation.config.AdminApiKeyFilter;
import com.flatmap.navigation.config.ApiErrors;
import com.flatmap.navigation.service.ElevationService;
import com.flatmap.navigation.service.GyeonggiDataService;
import com.flatmap.navigation.service.PublicDataService;
import com.flatmap.navigation.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 컨트롤러 실패 시 예외 메시지(내부 URL·키·경로 등)가 응답에 노출되지 않는지 확인한다. */
@IntegrationTest
class ErrorResponseTest {

    private static final String LEAKY_DETAIL =
            "jdbc:postgresql://internal-host:5432/prod?password=hunter2 /srv/app/dem/elevations.json KEY=abc123";

    @Autowired
    MockMvc mvc;

    @Value("${admin.api-key}")
    String adminApiKey;

    @MockBean
    ElevationService elevationService;

    @MockBean
    GyeonggiDataService gyeonggiDataService;

    /** /fetch/all 이 실제 외부 API 를 호출하지 않도록 막는다(0건을 돌려준다). */
    @MockBean
    PublicDataService publicDataService;

    @Test
    void failingControllerReturnsGenericErrorWithoutExceptionMessage() throws Exception {
        when(elevationService.updateGradesFromElevation()).thenThrow(new RuntimeException(LEAKY_DETAIL));

        MvcResult result = mvc.perform(post("/api/steep-slope/update-grade/elevation")
                        .header(AdminApiKeyFilter.HEADER, adminApiKey))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value(ApiErrors.INTERNAL_MESSAGE))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("hunter2", "internal-host", "elevations.json", "abc123", "RuntimeException");
    }

    @Test
    void aggregateFetchReportsOnlyFailedSourceNamesNotCauses() throws Exception {
        when(gyeonggiDataService.fetchAndSaveSecurityLights()).thenThrow(new RuntimeException(LEAKY_DETAIL));

        MvcResult result = mvc.perform(post("/api/safety/fetch/all")
                        .header(AdminApiKeyFilter.HEADER, adminApiKey)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failedSources[0]").value("보안등"))
                .andReturn();

        String body = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(body).doesNotContain("hunter2", "internal-host", "abc123", "RuntimeException");
    }

    @Test
    void malformedRequestBodyReturnsGenericBadRequest() throws Exception {
        MvcResult result = mvc.perform(post("/api/steep-slope/update-grade/values")
                        .header(AdminApiKeyFilter.HEADER, adminApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("JsonParseException", "Unexpected");
    }
}
