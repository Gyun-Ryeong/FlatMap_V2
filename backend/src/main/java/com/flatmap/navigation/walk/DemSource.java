package com.flatmap.navigation.walk;

/** 고도 래스터 공급자. 운영에서는 설정된 GeoTIFF 파일, 테스트에서는 합성 래스터다. */
public interface DemSource {

    /** 원본이 실제로 있는지(없으면 적재를 시작하지 않는다). */
    boolean exists();

    /** @throws DemException 읽을 수 없거나 지원하지 않는 형식 */
    DemRaster load();
}
