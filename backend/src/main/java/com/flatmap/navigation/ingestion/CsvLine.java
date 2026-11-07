package com.flatmap.navigation.ingestion;

import java.util.ArrayList;
import java.util.List;

/**
 * 한 줄짜리 CSV 분해(쉼표 구분, 큰따옴표 인용, "" 이스케이프).
 * 줄바꿈이 들어간 인용 필드는 지원하지 않는다 — 대상 CSV 에는 없다.
 */
final class CsvLine {

    private CsvLine() {}

    static List<String> split(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
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
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out;
    }
}
