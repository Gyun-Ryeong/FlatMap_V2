package com.flatmap.navigation.service;

import com.flatmap.navigation.model.SteepSlopeArea;
import com.flatmap.navigation.repository.SteepSlopeAreaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 성남시 급경사지(붕괴위험 지정지)와 경사도(°)를 번들 CSV 에서 적재한다.
 *
 * <p>원본 FlatMap 이 쓰던 값을 그대로 옮긴다. CSV 는 행정안전부 급경사지 목록에 위·경도를 붙이고
 * Copernicus DEM(30m)에서 읽은 경사를 도(°)로 적은 것이다. 열 {@code 최종경사도(도)} 는
 * 해당 지점 값이고, 지점 값이 비었을 때만 주변 최대값으로 보정했다(값출처 열).
 *
 * <p>이 값은 붕괴위험 경고 레이어의 표시용이다. 보행난이도 임계값(3.18°/4.76°)을 적용하지 않는다.
 *
 * <p>적재는 {@code MOIS_CSV} 출처만 통째로 교체한다(같은 트랜잭션). 파일을 먼저 전부 검증하므로
 * 한 행이라도 잘못되면 DB 는 바뀌지 않는다. 몇 번을 실행해도 결과가 같다.
 */
@Service
public class SteepSlopeGradeImportService {

    private static final Logger log = LoggerFactory.getLogger(SteepSlopeGradeImportService.class);

    static final String RESOURCE_PATH = "/data/성남시_급경사지_경사도.csv";
    static final String SOURCE = "MOIS_CSV";

    private static final Map<String, String> REGION_CODES = Map.of(
            "성남시 수정구", "41131",
            "성남시 중원구", "41133",
            "성남시 분당구", "41135");

    /** "경기도 성남시 수정구 태평동 224-11" → (성남시 수정구, 태평동, 224-11) */
    private static final Pattern ADDRESS = Pattern.compile("^경기도 (성남시 \\S+) (\\S+) (\\S+)$");

    public record Result(int parsed, int saved, int withGrade) {}

    private final SteepSlopeAreaRepository repository;

    public SteepSlopeGradeImportService(SteepSlopeAreaRepository repository) {
        this.repository = repository;
    }

    /** 번들 CSV 를 읽어 {@code MOIS_CSV} 출처를 교체한다. */
    public Result importBundled() {
        List<SteepSlopeArea> areas;
        try (InputStream in = SteepSlopeGradeImportService.class.getResourceAsStream(RESOURCE_PATH)) {
            if (in == null) {
                throw new IllegalStateException("번들 CSV 가 없습니다: " + RESOURCE_PATH);
            }
            areas = parse(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        int saved = repository.replaceSource(SOURCE, areas);
        int withGrade = (int) areas.stream().filter(a -> a.getGrade() != null).count();
        log.info("급경사지 경사도 적재 완료 — 파싱 {}건, 저장 {}건, 경사도 있음 {}건", areas.size(), saved, withGrade);
        return new Result(areas.size(), saved, withGrade);
    }

    /**
     * CSV 를 급경사지 목록으로 바꾼다. 필수 열이 없거나, 행이 비었거나, 좌표·경사도가 올바르지 않으면
     * {@link IllegalArgumentException} 을 던진다(어떤 행도 일부만 반영하지 않는다).
     */
    static List<SteepSlopeArea> parse(Reader reader) {
        List<List<String>> records = readRecords(reader);
        if (records.size() < 2) {
            throw new IllegalArgumentException("CSV 에 데이터 행이 없습니다");
        }
        List<String> header = records.get(0);
        int iName = column(header, "급경사지명");
        int iSigungu = column(header, "시군구");
        int iAddress = column(header, "주소");
        int iMountain = column(header, "산주소여부");
        int iLat = column(header, "위도");
        int iLng = column(header, "경도");
        int iGrade = column(header, "최종경사도");

        List<SteepSlopeArea> areas = new ArrayList<>();
        for (int n = 1; n < records.size(); n++) {
            List<String> r = records.get(n);
            int line = n + 1;
            if (r.size() <= Math.max(iGrade, Math.max(iLat, iLng))) {
                throw new IllegalArgumentException(line + "행: 열 수가 부족합니다");
            }
            String name = r.get(iName).trim();
            if (name.isEmpty()) {
                throw new IllegalArgumentException(line + "행: 급경사지명이 비었습니다");
            }

            Matcher m = ADDRESS.matcher(r.get(iAddress).trim());
            if (!m.matches()) {
                throw new IllegalArgumentException(line + "행: 주소 형식을 해석할 수 없습니다 (" + name + ")");
            }
            String sigungu = m.group(1);
            String regionCode = REGION_CODES.get(sigungu);
            if (regionCode == null || !sigungu.equals("성남시 " + r.get(iSigungu).trim())) {
                throw new IllegalArgumentException(line + "행: 시군구가 주소와 맞지 않습니다 (" + name + ")");
            }

            double lat = number(r.get(iLat), line, "위도");
            double lng = number(r.get(iLng), line, "경도");
            double grade = number(r.get(iGrade), line, "최종경사도");
            if (grade < 0 || grade >= 90) {
                throw new IllegalArgumentException(line + "행: 경사도는 0° 이상 90° 미만이어야 합니다 (" + grade + ")");
            }

            SteepSlopeArea a = new SteepSlopeArea(name, lat, lng, grade, SOURCE, regionCode);
            a.setSigungu(sigungu);
            a.setEupmyeon(m.group(2));
            a.setJibun(m.group(3));
            a.setIsMountain("Y".equalsIgnoreCase(r.get(iMountain).trim()));
            areas.add(a);
        }
        return areas;
    }

    private static int column(List<String> header, String prefix) {
        for (int i = 0; i < header.size(); i++) {
            if (header.get(i).trim().startsWith(prefix)) return i;
        }
        throw new IllegalArgumentException("필수 열이 없습니다: " + prefix);
    }

    private static double number(String raw, int line, String what) {
        try {
            double v = Double.parseDouble(raw.trim());
            if (Double.isNaN(v) || Double.isInfinite(v)) throw new NumberFormatException();
            return v;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(line + "행: " + what + " 값이 숫자가 아닙니다");
        }
    }

    /** 따옴표로 감싼 필드와 이중 따옴표를 처리하는 최소한의 CSV 읽기. BOM 은 버린다. */
    private static List<List<String>> readRecords(Reader reader) {
        String text;
        try {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[8192];
            int n;
            while ((n = reader.read(buf)) != -1) sb.append(buf, 0, n);
            text = sb.toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') text = text.substring(1);

        List<List<String>> records = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    cur.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                row.add(cur.toString());
                cur.setLength(0);
            } else if (c == '\n') {
                endRow(records, row, cur);
                row = new ArrayList<>();
            } else if (c != '\r') {
                cur.append(c);
            }
        }
        if (cur.length() > 0 || !row.isEmpty()) endRow(records, row, cur);
        return records;
    }

    private static void endRow(List<List<String>> records, List<String> row, StringBuilder cur) {
        row.add(cur.toString());
        cur.setLength(0);
        if (row.size() > 1 || !row.get(0).isBlank()) records.add(row);
    }
}
