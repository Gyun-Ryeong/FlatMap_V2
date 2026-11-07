package com.flatmap.navigation.model;

import java.time.LocalDateTime;

/**
 * safety_facility 한 행. JPA 엔티티가 아니다 — 공간 데이터는 JdbcTemplate + PostGIS 로 다룬다(CLAUDE.md 규칙 3).
 * 기존 API 응답(CctvLocation / SecurityLight)과 같은 필드명(name, latitude, longitude, address, createdAt)을 유지한다.
 */
public class SafetyFacility {

    public static final String TYPE_CCTV = "CCTV";
    public static final String TYPE_SECURITY_LIGHT = "SECURITY_LIGHT";
    public static final String TYPE_POLICE = "POLICE";

    private Long id;
    private String facilityType;
    private String name;
    private String address;
    private String regionCode;
    private double latitude;
    private double longitude;
    private String dataSource;
    private LocalDateTime createdAt;

    public SafetyFacility() {}

    public SafetyFacility(String facilityType, String name, String address, String regionCode,
                          double latitude, double longitude, String dataSource) {
        this.facilityType = facilityType;
        this.name = name;
        this.address = address;
        this.regionCode = regionCode;
        this.latitude = latitude;
        this.longitude = longitude;
        this.dataSource = dataSource;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFacilityType() { return facilityType; }
    public void setFacilityType(String facilityType) { this.facilityType = facilityType; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getRegionCode() { return regionCode; }
    public void setRegionCode(String regionCode) { this.regionCode = regionCode; }
    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }
    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }
    public String getDataSource() { return dataSource; }
    public void setDataSource(String dataSource) { this.dataSource = dataSource; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
