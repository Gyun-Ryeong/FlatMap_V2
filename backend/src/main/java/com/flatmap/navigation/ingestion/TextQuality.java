package com.flatmap.navigation.ingestion;

import com.flatmap.navigation.model.SafetyFacility;

import java.util.List;

/**
 * 인코딩이 깨진 행을 찾는 기준. 한글이 {@code ?} 로 바뀌거나(덤프 문제 F1) 변환 실패 문자(U+FFFD)가 들어간 값,
 * 또는 비어 있는 값을 "나쁜 값"으로 본다. 같은 기준을 SQL 로도 쓴다(SafetyFacilityRepository.badTextCounts,
 * docs/dev/ingestion-check.md) — 둘을 바꿀 때는 함께 바꾼다.
 */
public final class TextQuality {

    /** 소스별 나쁜 행 비율이 이 값을 넘으면 그 동기화는 실패이며 롤백된다. */
    public static final double MAX_BAD_RATIO = 0.05;

    private TextQuality() {}

    public static boolean isBad(String value) {
        if (value == null) return true;
        boolean onlyQuestionOrSpace = true;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '�') return true;
            if (c != '?' && !Character.isWhitespace(c)) onlyQuestionOrSpace = false;
        }
        return onlyQuestionOrSpace; // 빈 문자열·공백·'?'만 있는 값
    }

    public static boolean isBadRow(SafetyFacility f, boolean checkAddress) {
        return isBad(f.getName()) || (checkAddress && isBad(f.getAddress()));
    }

    public static double badRatio(List<SafetyFacility> rows, boolean checkAddress) {
        if (rows.isEmpty()) return 0.0;
        long bad = rows.stream().filter(f -> isBadRow(f, checkAddress)).count();
        return (double) bad / rows.size();
    }

    public static boolean exceedsLimit(long bad, long total) {
        return total > 0 && (double) bad / total > MAX_BAD_RATIO;
    }
}
