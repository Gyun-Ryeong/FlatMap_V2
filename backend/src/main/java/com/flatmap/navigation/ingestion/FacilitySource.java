package com.flatmap.navigation.ingestion;

import com.flatmap.navigation.model.SafetyFacility;

import java.util.Arrays;
import java.util.Optional;

/**
 * 안전시설 수집 소스. {@link #code()} 가 safety_facility.data_source 에 저장되는 고정 영문 코드값이다
 * (한글 출처명이 인코딩 문제로 깨졌던 SafetyRoad 덤프의 교훈 — 코드값은 ASCII 만 쓴다).
 */
public enum FacilitySource {

    GG_CCTV(SafetyFacility.TYPE_CCTV, true),
    GG_SECURITY_LIGHT(SafetyFacility.TYPE_SECURITY_LIGHT, true),
    GG_POLICE(SafetyFacility.TYPE_POLICE, true),
    /** 원본에 주소 컬럼이 없다 → 주소 검사를 하지 않고 좌표 범위로만 거른다(DECISIONS 참고). */
    ODCLOUD_ILLUMINATION(SafetyFacility.TYPE_SECURITY_LIGHT, false),
    ODCLOUD_SEONGNAM_CCTV(SafetyFacility.TYPE_CCTV, true),
    SEONGNAM_CSV(SafetyFacility.TYPE_SECURITY_LIGHT, true);

    private final String facilityType;
    private final boolean addressRequired;

    FacilitySource(String facilityType, boolean addressRequired) {
        this.facilityType = facilityType;
        this.addressRequired = addressRequired;
    }

    public String code() {
        return name();
    }

    public String facilityType() {
        return facilityType;
    }

    /** true 면 주소에 "성남"이 있어야 하고, 주소 공백도 "나쁜 행"으로 센다. */
    public boolean addressRequired() {
        return addressRequired;
    }

    public static Optional<FacilitySource> fromCode(String code) {
        if (code == null) return Optional.empty();
        String c = code.trim();
        return Arrays.stream(values()).filter(s -> s.name().equalsIgnoreCase(c)).findFirst();
    }
}
