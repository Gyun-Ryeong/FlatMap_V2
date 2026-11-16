package com.flatmap.navigation.dto;

public class RouteRequest {
    private double originLng;
    private double originLat;
    private double destLng;
    private double destLat;
    private String option;
    /** 보행 유형(GENERAL/WEAK). 없으면 서버 기본값. */
    private String walkMode;

    public double getOriginLng() { return originLng; }
    public void setOriginLng(double originLng) { this.originLng = originLng; }
    public double getOriginLat() { return originLat; }
    public void setOriginLat(double originLat) { this.originLat = originLat; }
    public double getDestLng() { return destLng; }
    public void setDestLng(double destLng) { this.destLng = destLng; }
    public double getDestLat() { return destLat; }
    public void setDestLat(double destLat) { this.destLat = destLat; }
    public String getWalkMode() { return walkMode; }
    public void setWalkMode(String walkMode) { this.walkMode = walkMode; }
    public String getOption() { return option; }
    public void setOption(String option) { this.option = option; }
}
