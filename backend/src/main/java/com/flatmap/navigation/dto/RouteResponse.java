package com.flatmap.navigation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.flatmap.navigation.routescore.AxisScore;

import java.util.ArrayList;
import java.util.List;

public class RouteResponse {
    private List<Coord> coords;
    private int totalDistance;
    private int totalDuration;
    /** 경로 반경 이내 급경사지(붕괴위험 경고 레이어). 등급·종합 위험도는 없다. */
    private List<RiskSection> riskSections;
    private WeatherInfo weather;
    private boolean detourApplied = true;
    private String detourMessage;
    /** 이 경로의 총점(0~100, 설계 파라미터 기반 상대 비교 값)과 축별 내역. 점수를 매기지 않은 응답에는 없다. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Double totalScore;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<AxisScore> breakdown;
    /** 우회 요청에서 비교 기준이 된 원경로의 총점. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Double originalTotalScore;

    public RouteResponse(List<Coord> coords, int totalDistance, int totalDuration) {
        this.coords = coords;
        this.totalDistance = totalDistance;
        this.totalDuration = totalDuration;
        this.riskSections = new ArrayList<>();
    }

    public List<Coord> getCoords() { return coords; }
    public int getTotalDistance() { return totalDistance; }
    public int getTotalDuration() { return totalDuration; }
    public List<RiskSection> getRiskSections() { return riskSections; }
    public void setRiskSections(List<RiskSection> riskSections) {
        this.riskSections = riskSections;
    }
    public WeatherInfo getWeather() { return weather; }
    public void setWeather(WeatherInfo weather) { this.weather = weather; }
    public boolean isDetourApplied() { return detourApplied; }
    public void setDetourApplied(boolean detourApplied) { this.detourApplied = detourApplied; }
    public String getDetourMessage() { return detourMessage; }
    public void setDetourMessage(String detourMessage) { this.detourMessage = detourMessage; }

    public Double getTotalScore() { return totalScore; }
    public void setTotalScore(Double totalScore) { this.totalScore = totalScore; }
    public List<AxisScore> getBreakdown() { return breakdown; }
    public void setBreakdown(List<AxisScore> breakdown) { this.breakdown = breakdown; }
    public Double getOriginalTotalScore() { return originalTotalScore; }
    public void setOriginalTotalScore(Double originalTotalScore) { this.originalTotalScore = originalTotalScore; }

    public static class Coord {
        private double lat;
        private double lng;

        public Coord(double lat, double lng) {
            this.lat = lat;
            this.lng = lng;
        }

        public double getLat() { return lat; }
        public double getLng() { return lng; }
    }
}
