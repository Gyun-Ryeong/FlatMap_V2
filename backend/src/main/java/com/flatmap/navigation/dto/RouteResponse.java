package com.flatmap.navigation.dto;

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
