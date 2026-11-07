package com.flatmap.navigation.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 급경사지 — 붕괴위험 지정지 표본.
 *
 * <p>이 테이블은 "붕괴위험 경고 레이어"용이다. 보행난이도(DEM 기반 경로 경사)와는 별개의 축이므로
 * 여기에 보행 위험등급(risk_level)을 두지 않는다. 보행난이도 임계값(3.18°/4.76°)을 이 데이터에 적용하지 않는다.
 *
 * <p>grade 의 단위는 각도(°)이며 %가 아니다.
 * geom 컬럼(생성 컬럼)은 latitude/longitude 에서 DB 가 파생한다. JPA 는 geom 을 매핑하지 않는다.
 */
@Entity
@Table(name = "steep_slope_areas")
public class SteepSlopeArea {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private Double latitude;

    private Double longitude;

    /** 경사도, 단위 각도(°) */
    private Double grade;

    private String source;

    @Column(name = "region_code")
    private String regionCode;

    /** 시군구 (예: "성남시 중원구") */
    private String sigungu;

    /** 읍면동 (예: "상대원동") */
    private String eupmyeon;

    /** 지번 (예: "산416-1") */
    private String jibun;

    /** 산 지번(임야) 여부 — 산지형 급경사지 구분용 */
    @Column(name = "is_mountain")
    private Boolean isMountain = Boolean.FALSE;

    @Column(name = "created_at")
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
}
