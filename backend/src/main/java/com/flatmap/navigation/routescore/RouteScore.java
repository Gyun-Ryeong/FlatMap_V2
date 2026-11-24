package com.flatmap.navigation.routescore;

import java.util.List;

/** 경로 하나의 총점(0~100)과 축별 점수 내역. 반영된 축만 breakdown 에 들어 있다. */
public record RouteScore(double totalScore, List<AxisScore> breakdown) {
}
