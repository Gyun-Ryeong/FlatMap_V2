package com.flatmap.navigation.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 급경사지 동기화 요청 페이로드.
 * 경사도(grade)·위험도(riskLevel)는 의도적으로 포함하지 않는다 — 기존 값을 보존하기 위함.
 */
public class SlopeSyncRequest {

    private List<Long> delete = new ArrayList<>();
    private List<Item> update = new ArrayList<>();
    private List<Item> insert = new ArrayList<>();

    public List<Long> getDelete() { return delete; }
    public void setDelete(List<Long> delete) { this.delete = delete; }
    public List<Item> getUpdate() { return update; }
    public void setUpdate(List<Item> update) { this.update = update; }
    public List<Item> getInsert() { return insert; }
    public void setInsert(List<Item> insert) { this.insert = insert; }

    public static class Item {
        private Long id;
        private String name;
        private String sigungu;
        private String eupmyeon;
        private String jibun;
        private String regionCode;
        private Double latitude;
        private Double longitude;
        /** 산 지번(임야) 여부 */
        private Boolean isMountain;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getSigungu() { return sigungu; }
        public void setSigungu(String sigungu) { this.sigungu = sigungu; }
        public String getEupmyeon() { return eupmyeon; }
        public void setEupmyeon(String eupmyeon) { this.eupmyeon = eupmyeon; }
        public String getJibun() { return jibun; }
        public void setJibun(String jibun) { this.jibun = jibun; }
        public String getRegionCode() { return regionCode; }
        public void setRegionCode(String regionCode) { this.regionCode = regionCode; }
        public Double getLatitude() { return latitude; }
        public void setLatitude(Double latitude) { this.latitude = latitude; }
        public Double getLongitude() { return longitude; }
        public void setLongitude(Double longitude) { this.longitude = longitude; }
        public Boolean getIsMountain() { return isMountain; }
        public void setIsMountain(Boolean isMountain) { this.isMountain = isMountain; }
    }
}
