package com.flatmap.navigation.web;

import com.flatmap.navigation.config.AdminApiKeyFilter;
import com.flatmap.navigation.support.IntegrationTest;
import com.flatmap.navigation.walk.DemException;
import com.flatmap.navigation.walk.WalkGridBuildService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** POST /api/walk-difficulty/grid/rebuild — 적재 서비스는 모킹하고 응답 매핑·오류 일반화를 확인한다. */
@IntegrationTest
class WalkDifficultyEndpointTest {

    private static final String URL = "/api/walk-difficulty/grid/rebuild";

    @Autowired
    MockMvc mvc;
    @Value("${admin.api-key}")
    String adminApiKey;

    @MockBean
    WalkGridBuildService buildService;

    @BeforeEach
    void reset() {
        Mockito.reset(buildService);
    }

    @Test
    void successReturnsCounts() throws Exception {
        Mockito.when(buildService.rebuild()).thenReturn(new WalkGridBuildService.BuildResult(416_017, 415_000, 1234));

        mvc.perform(post(URL).header(AdminApiKeyFilter.HEADER, adminApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gridCells").value(416017))
                .andExpect(jsonPath("$.loadedCells").value(415000))
                .andExpect(jsonPath("$.elapsedMillis").value(1234));
    }

    @Test
    void missingDemIs409WithoutRevealingThePath() throws Exception {
        Mockito.when(buildService.rebuild()).thenThrow(new WalkGridBuildService.DemMissingException());

        mvc.perform(post(URL).header(AdminApiKeyFilter.HEADER, adminApiKey))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEM_NOT_FOUND"))
                .andExpect(content().string(not(containsString("tif"))));
    }

    @Test
    void anotherRebuildInProgressIs409() throws Exception {
        Mockito.when(buildService.rebuild()).thenThrow(new WalkGridBuildService.BuildInProgressException());

        mvc.perform(post(URL).header(AdminApiKeyFilter.HEADER, adminApiKey))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUILD_IN_PROGRESS"));
    }

    @Test
    void unreadableDemIs422AndInternalDetailsStayInTheLog() throws Exception {
        Mockito.when(buildService.rebuild()).thenThrow(new DemException("C:\\secret\\dir\\dem.tif 의 타일이 깨졌다"));

        mvc.perform(post(URL).header(AdminApiKeyFilter.HEADER, adminApiKey))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DEM_UNSUPPORTED"))
                .andExpect(content().string(not(containsString("secret"))));
    }

    @Test
    void unexpectedFailureIsAGeneric500() throws Exception {
        Mockito.when(buildService.rebuild()).thenThrow(new RuntimeException("jdbc:postgresql://db/secret password=hunter2"));

        mvc.perform(post(URL).header(AdminApiKeyFilter.HEADER, adminApiKey))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(not(containsString("hunter2"))))
                .andExpect(content().string(not(containsString("jdbc"))));
    }
}
