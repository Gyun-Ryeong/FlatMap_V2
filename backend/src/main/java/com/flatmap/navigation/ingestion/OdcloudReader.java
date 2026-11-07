package com.flatmap.navigation.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flatmap.navigation.model.SafetyFacility;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 공공데이터포털 odcloud(api.odcloud.kr) 리더. 응답: {"data":[...],"totalCount":N,"page":1,"perPage":..}
 * 조도측정 보안등(15110584)과 성남시 생활안전 CCTV(15147955)를 필드 매핑만 달리해 읽는다.
 */
public class OdcloudReader implements FacilityReader {

    private static final String BASE = "https://api.odcloud.kr/api/";

    private final FacilitySource source;
    private final String path;          // "<데이터셋ID>/v1/uddi:<uuid>"
    private final String latField;
    private final String lngField;
    private final String[] addressFields;
    private final String[] nameFields;
    private final String defaultName;
    private final IngestionHttp http;
    private final IngestionSettings settings;
    private final ObjectMapper mapper;

    private OdcloudReader(FacilitySource source, String path, String latField, String lngField,
                          String[] addressFields, String[] nameFields, String defaultName,
                          IngestionHttp http, IngestionSettings settings, ObjectMapper mapper) {
        this.source = source;
        this.path = path;
        this.latField = latField;
        this.lngField = lngField;
        this.addressFields = addressFields;
        this.nameFields = nameFields;
        this.defaultName = defaultName;
        this.http = http;
        this.settings = settings;
        this.mapper = mapper;
    }

    /** 조도측정 보안등 15110584 — 원본에 이름·주소 컬럼이 없다(좌표만). 이름은 "가로등"으로 둔다. */
    public static OdcloudReader illumination(IngestionHttp http, IngestionSettings settings, ObjectMapper mapper) {
        return new OdcloudReader(FacilitySource.ODCLOUD_ILLUMINATION,
                "15110584/v1/uddi:8ccdb2d7-f990-4334-855f-d75ae55fa92a",
                "위도(LATITUDE)", "경도(LONGITUDE)",
                new String[]{"소재지도로명주소", "소재지지번주소", "도로명주소", "지번주소", "주소"},
                new String[]{}, "가로등", http, settings, mapper);
    }

    /** 성남시 생활안전 CCTV 15147955. */
    public static OdcloudReader seongnamCctv(IngestionHttp http, IngestionSettings settings, ObjectMapper mapper) {
        return new OdcloudReader(FacilitySource.ODCLOUD_SEONGNAM_CCTV,
                "15147955/v1/uddi:6134ce56-07cc-45d9-b3d9-ed6fc76d9d4c",
                "위도", "경도",
                new String[]{"도로명주소", "지번주소"},
                new String[]{}, "생활안전CCTV", http, settings, mapper);
    }

    @Override
    public FacilitySource source() {
        return source;
    }

    @Override
    public SourceReading read() throws IOException {
        String key = RowFilter.encodeKey(RowFilter.requireKey(settings.publicDataApiKey()));
        SourceReading.Stats stats = new SourceReading.Stats();
        List<SafetyFacility> accepted = new ArrayList<>();

        for (int page = 1; page <= settings.maxPages(); page++) {
            String url = BASE + path + "?page=" + page + "&perPage=" + settings.pageSize() + "&serviceKey=" + key;
            JsonNode root = mapper.readTree(http.get(url));

            JsonNode data = root.path("data");
            if (!data.isArray()) throw new IngestionException("unexpected response shape");
            if (data.isEmpty()) break;

            for (JsonNode row : data) {
                stats.countRead();
                SafetyFacility f = RowFilter.accept(source,
                        GyeonggiJsonReader.firstText(row, nameFields), defaultName,
                        GyeonggiJsonReader.firstText(row, addressFields),
                        RowFilter.parseDouble(GyeonggiJsonReader.firstText(row, latField)),
                        RowFilter.parseDouble(GyeonggiJsonReader.firstText(row, lngField)), stats);
                if (f != null) accepted.add(f);
            }

            int total = root.path("totalCount").asInt(0);
            if (data.size() < settings.pageSize() || (total > 0 && (long) page * settings.pageSize() >= total)) break;
            settings.pause();
        }
        return stats.build(accepted);
    }
}
