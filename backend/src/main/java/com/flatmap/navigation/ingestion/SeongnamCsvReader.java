package com.flatmap.navigation.ingestion;

import com.flatmap.navigation.model.SafetyFacility;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 성남시 보안등 CSV 리더. 파일은 <b>CP949</b> 이고 약 40MB 라 저장소에 두지 않는다 —
 * 경로는 설정(SEONGNAM_SECURITY_LIGHT_CSV_PATH)으로 받는다.
 * 원본은 경기도 다른 시군(예: 가평군) 행이 섞여 있어 성남 필터가 필수다.
 * 컬럼은 헤더 이름으로 찾는다: 보안등위치명, 소재지도로명주소, 소재지지번주소, 위도, 경도.
 */
public class SeongnamCsvReader implements FacilityReader {

    static final Charset CP949 = Charset.forName("x-windows-949");

    private final IngestionSettings settings;

    public SeongnamCsvReader(IngestionSettings settings) {
        this.settings = settings;
    }

    @Override
    public FacilitySource source() {
        return FacilitySource.SEONGNAM_CSV;
    }

    @Override
    public SourceReading read() throws IOException {
        String configured = settings.seongnamCsvPath();
        if (configured == null || configured.isBlank()) throw new IngestionException("csv path not configured");
        Path path = Paths.get(configured.trim());
        if (!Files.isRegularFile(path)) throw new IngestionException("csv file not found");

        SourceReading.Stats stats = new SourceReading.Stats();
        List<SafetyFacility> accepted = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(Files.newInputStream(path), CP949))) {
            String header = reader.readLine();
            if (header == null) throw new IngestionException("csv is empty");
            if (header.startsWith("﻿")) header = header.substring(1);
            List<String> columns = CsvLine.split(header);
            int nameIdx = indexOf(columns, "보안등위치명");
            int roadIdx = indexOf(columns, "소재지도로명주소");
            int lotIdx = indexOf(columns, "소재지지번주소");
            int latIdx = indexOf(columns, "위도");
            int lngIdx = indexOf(columns, "경도");
            if (nameIdx < 0 || roadIdx < 0 || lotIdx < 0 || latIdx < 0 || lngIdx < 0) {
                // CP949 로 읽었는데 헤더 이름이 맞지 않으면 인코딩이 다른 파일이다.
                throw new IngestionException("csv header mismatch");
            }
            int need = Math.max(Math.max(nameIdx, roadIdx), Math.max(Math.max(lotIdx, latIdx), lngIdx)) + 1;

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                stats.countRead();
                List<String> cols = CsvLine.split(line);
                if (cols.size() < need) {
                    stats.exclude(SourceReading.INVALID_COORDS);
                    continue;
                }
                String road = cols.get(roadIdx).trim();
                String address = road.isEmpty() ? cols.get(lotIdx) : road;
                SafetyFacility f = RowFilter.accept(FacilitySource.SEONGNAM_CSV, cols.get(nameIdx), "보안등", address,
                        RowFilter.parseDouble(cols.get(latIdx)), RowFilter.parseDouble(cols.get(lngIdx)), stats);
                if (f != null) accepted.add(f);
            }
        }
        return stats.build(accepted);
    }

    private static int indexOf(List<String> columns, String name) {
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).trim().equals(name)) return i;
        }
        return -1;
    }
}
