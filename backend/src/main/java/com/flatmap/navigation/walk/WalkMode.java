package com.flatmap.navigation.walk;

import java.util.Locale;

/** 보행 유형. 모드에 따라 보행난이도 임계값이 다르다(WEAK 가 더 엄격하다). UI 에서는 "보행약자" / "일반". */
public enum WalkMode {
    GENERAL, WEAK;

    /**
     * 요청 문자열을 모드로 바꾼다(대소문자 무시). 비어 있으면 {@code fallback}.
     *
     * @throws InvalidWalkModeException 알 수 없는 값
     */
    public static WalkMode parse(String value, WalkMode fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidWalkModeException();
        }
    }
}
