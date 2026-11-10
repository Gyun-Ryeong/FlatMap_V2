package com.flatmap.navigation.density;

/**
 * 시설 밀도 등급. "위험" 표현을 쓰지 않는다 — 시설이 적다는 것은 위험이 아니라 시설 데이터가 적다는 뜻이다.
 * 이름은 facility_density_grid.chk_density_level 과 같다.
 */
public enum DensityLevel {
    LOW_DENSITY,
    MEDIUM,
    HIGH_DENSITY
}
