package com.flatmap.navigation.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flatmap.navigation.model.SafetyFacility;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 경기데이터드림(openapi.gg.go.kr) JSON 서비스 리더 — CCTV, 보안등(SECRTLGT).
 * 응답 구조: {"서비스명":[{"head":[{"list_total_count":N},{"RESULT":{"CODE":..}}]},{"row":[...]}]}
 * 데이터가 없거나 키가 잘못되면 최상위에 {"RESULT":{"CODE":"INFO-200"|...}} 만 온다.
 */
public class GyeonggiJsonReader implements FacilityReader {

    static final String BASE = "https://openapi.gg.go.kr";

    private final FacilitySource source;
    private final String service;
    private final String defaultName;
    private final String[] nameFields;
    private final IngestionHttp http;
    private final IngestionSettings settings;
    private final ObjectMapper mapper;

    private GyeonggiJsonReader(FacilitySource source, String service, String defaultName, String[] nameFields,
                               IngestionHttp http, IngestionSettings settings, ObjectMapper mapper) {
        this.source = source;
        this.service = service;
        this.defaultName = defaultName;
        this.nameFields = nameFields;
        this.http = http;
        this.settings = settings;
        this.mapper = mapper;
    }

    public static GyeonggiJsonReader cctv(IngestionHttp http, IngestionSettings settings, ObjectMapper mapper) {
        return new GyeonggiJsonReader(FacilitySource.GG_CCTV, "CCTV", "CCTV",
                new String[]{"INSTL_PUPRS_DIV_NM", "CCTV_INSTL_PURPS_NM", "MANAGE_INST_NM"}, http, settings, mapper);
    }

    public static GyeonggiJsonReader securityLight(IngestionHttp http, IngestionSettings settings, ObjectMapper mapper) {
        return new GyeonggiJsonReader(FacilitySource.GG_SECURITY_LIGHT, "SECRTLGT", "보안등",
                new String[]{"SECRT_LGT_DVSN_NM", "INST_NM", "SECRTLGT_NM", "MANAGE_INST_NM"}, http, settings, mapper);
    }

    @Override
    public FacilitySource source() {
        return source;
    }

    @Override
    public SourceReading read() throws IOException {
        String key = RowFilter.encodeKey(RowFilter.requireKey(settings.gyeonggiApiKey()));
        SourceReading.Stats stats = new SourceReading.Stats();
        List<SafetyFacility> accepted = new ArrayList<>();

        for (int page = 1; page <= settings.maxPages(); page++) {
            String url = BASE + "/" + service + "?KEY=" + key + "&Type=json&pIndex=" + page
                    + "&pSize=" + settings.pageSize();
            JsonNode root = mapper.readTree(http.get(url));

            JsonNode topResult = root.path("RESULT");
            if (!topResult.isMissingNode()) {
                String code = topResult.path("CODE").asText("");
                if ("INFO-200".equals(code)) break; // 해당 페이지에 데이터 없음 = 끝
                throw new IngestionException("API result code " + code);
            }

            JsonNode svc = root.path(service);
            if (!svc.isArray() || svc.size() < 2) throw new IngestionException("unexpected response shape");
            JsonNode rows = svc.get(1).path("row");
            if (!rows.isArray() || rows.isEmpty()) break;

            for (JsonNode row : rows) {
                stats.countRead();
                SafetyFacility f = RowFilter.accept(source, firstText(row, nameFields), defaultName,
                        firstText(row, "REFINE_ROADNM_ADDR", "REFINE_LOTNO_ADDR"),
                        RowFilter.parseDouble(firstText(row, "REFINE_WGS84_LAT")),
                        RowFilter.parseDouble(firstText(row, "REFINE_WGS84_LOGT")), stats);
                if (f != null) accepted.add(f);
            }

            int total = svc.get(0).path("head").path(0).path("list_total_count").asInt(0);
            if (rows.size() < settings.pageSize() || (total > 0 && (long) page * settings.pageSize() >= total)) break;
            settings.pause();
        }
        return stats.build(accepted);
    }

    static String firstText(JsonNode row, String... fields) {
        for (String f : fields) {
            JsonNode n = row.path(f);
            if (!n.isMissingNode() && !n.isNull()) {
                String v = n.asText().trim();
                if (!v.isEmpty()) return v;
            }
        }
        return null;
    }
}
