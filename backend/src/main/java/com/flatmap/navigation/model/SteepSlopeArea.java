package com.flatmap.navigation.model;

import java.time.LocalDateTime;

/**
 * 급경사지 — 붕괴위험 지정지 표본. "붕괴위험 경고 레이어"용 값 객체다.
 *
 * <p>이 데이터에는 보행 위험등급(risk_level)이 없다. 보행난이도(DEM 기반 경로 경사)와는 별개의 축이므로
 * 보행난이도 임계값(3.18°/4.76°)을 적용하지 않는다.
 *
 * <p>grade 의 단위는 각도(°)이며 %가 아니다.
 * 공간 데이터이므로 JPA 가 아니라 {@code SteepSlopeAreaRepository}(JdbcTemplate + PostGIS)가 읽고 쓴다.
 * 위치는 DB 의 geom 이 원본이고, latitude/longitude 는 거기서 파생된 값이다.
 */
public class SteepSlopeArea {

    private Long id;
    private String name;
    private Double latitude;
    private Double longitude;
    /** 경사도, 단위 각도(°) */
    private Double grade;
    private String source;
    private String regionCode;
    /** 시군구 (예: "성남시 중원구") */
    private String sigungu;
    /** 읍면동 (예: "상대원동") */
    private String eupmyeon;
    /** 지번 (예: "산416-1") */
    private String jibun;
    /** 산 지번(임야) 여부 — 산지형 급경사지 구분용 */
    private Boolean isMountain = Boolean.FALSE;
    private LocalDateTime createdAt = LocalDateTime.now();

    public SteepSlopeArea() {}

    public SteepSlopeArea(String name, Double latitude, Double longitude, Double grade,
                          String source, String regionCode) {
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.grade = grade;
        this.source = source;
        this.regionCode = regionCode;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public Double getGrade() { return grade; }
    public String getSource() { return source; }
    public String getRegionCode() { return regionCode; }
    public String getSigungu() { return sigungu; }
    public String getEupmyeon() { return eupmyeon; }
    public String getJibun() { return jibun; }
    public Boolean getIsMountain() { return isMountain; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setId(Long id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    public void setGrade(Double grade) { this.grade = grade; }
    public void setSource(String source) { this.source = source; }
    public void setRegionCode(String regionCode) { this.regionCode = regionCode; }
    public void setSigungu(String sigungu) { this.sigungu = sigungu; }
    public void setEupmyeon(String eupmyeon) { this.eupmyeon = eupmyeon; }
    public void setJibun(String jibun) { this.jibun = jibun; }
    public void setIsMountain(Boolean isMountain) { this.isMountain = isMountain; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
