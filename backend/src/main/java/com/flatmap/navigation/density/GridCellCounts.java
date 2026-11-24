package com.flatmap.navigation.density;

/** 셀 하나의 시설 종류별 개수(집계 쿼리의 한 행). */
public record GridCellCounts(int i, int j, int cctv, int light, int police) {
}
