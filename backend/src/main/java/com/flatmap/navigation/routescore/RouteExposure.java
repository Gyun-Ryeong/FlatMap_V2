package com.flatmap.navigation.routescore;

/** 경로 LINESTRING 주변 시설 수와 경로 길이(m). DB 쿼리 한 번의 결과다. */
public record RouteExposure(long cctvCount, long lightCount, long policeCount, double lengthMeters) {
}
