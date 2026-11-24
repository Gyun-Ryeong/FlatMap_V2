package com.flatmap.navigation.routescore;

import java.util.Map;

/** 축 하나의 점수와 계산 내역. detail 은 점수가 어떻게 나왔는지 보여 주는 입력값이다. */
public record AxisScore(String axis, String name, double score, double weight, Map<String, Object> detail) {
}
