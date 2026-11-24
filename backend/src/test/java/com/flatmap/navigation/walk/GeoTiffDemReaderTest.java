package com.flatmap.navigation.walk;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeoTiffDemReaderTest {

    private static final double PX = 1.0 / 3600;

    @TempDir
    Path dir;

    /** 타일 경계에 걸치는 크기(70x50, 타일 32x32)의 값이 서로 다른 래스터. */
    private static float[] ramp(int w, int h) {
        float[] v = new float[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                v[y * w + x] = 100.5f + x * 1.25f + y * 0.0625f * (x % 7) - (float) Math.sin(x * 0.3) * 3.3f;
            }
        }
        return v;
    }

    private Path write(byte[] bytes) throws IOException {
        Path f = dir.resolve("dem.tif");
        Files.write(f, bytes);
        return f;
    }

    @ParameterizedTest(name = "{0} / deflate={1} / 예측기3={2}")
    @CsvSource({
            "LITTLE_ENDIAN, true, true",     // Copernicus COG 와 같은 조합
            "LITTLE_ENDIAN, true, false",
            "LITTLE_ENDIAN, false, false",
            "BIG_ENDIAN, true, true",
            "BIG_ENDIAN, false, true"})
    void roundTripsAllSupportedLayouts(String order, boolean deflate, boolean predictor) throws IOException {
        float[] values = ramp(70, 50);
        ByteOrder bo = order.equals("BIG_ENDIAN") ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN;
        Path f = write(TestTiff.encode(70, 50, 32, 32, values, bo, deflate, predictor,
                127.0, 38.0, PX, PX, false));

        DemRaster dem = GeoTiffDemReader.read(f);

        assertThat(dem.width()).isEqualTo(70);
        assertThat(dem.height()).isEqualTo(50);
        assertThat(dem.values()).containsExactly(values);          // 비트 단위로 같다(손실 없음)
        assertThat(dem.originLng()).isEqualTo(127.0);
        assertThat(dem.originLat()).isEqualTo(38.0);
        assertThat(dem.pixelLng()).isEqualTo(PX);
        assertThat(dem.pixelLat()).isEqualTo(PX);
    }

    @Test
    void pixelIsPointShiftsTheOriginByHalfAPixel() throws IOException {
        Path f = write(TestTiff.encode(40, 40, 32, 32, ramp(40, 40), ByteOrder.LITTLE_ENDIAN, true, true,
                127.0, 38.0, PX, PX, true));
        DemRaster dem = GeoTiffDemReader.read(f);
        assertThat(dem.originLng()).isEqualTo(127.0 - PX / 2);
        assertThat(dem.originLat()).isEqualTo(38.0 + PX / 2);
    }

    @Test
    void columnAndRowLookupMatchTheGeoreference() throws IOException {
        Path f = write(TestTiff.encode(70, 50, 32, 32, ramp(70, 50), ByteOrder.LITTLE_ENDIAN, true, true,
                127.0, 38.0, PX, PX, false));
        DemRaster dem = GeoTiffDemReader.read(f);
        // 픽셀 (col 10, row 20) 의 중심
        assertThat(dem.colOf(127.0 + 10.5 * PX)).isEqualTo(10);
        assertThat(dem.rowOf(38.0 - 20.5 * PX)).isEqualTo(20);
    }

    @Test
    void rejectsFilesThatAreNotSupportedGeoTiffs() throws IOException {
        assertThatThrownBy(() -> GeoTiffDemReader.read(write("not a tiff at all".getBytes())))
                .isInstanceOf(DemException.class);
        assertThatThrownBy(() -> GeoTiffDemReader.read(dir.resolve("missing.tif")))
                .isInstanceOf(DemException.class);
        // 잘린 파일은 조용히 잘못 읽지 않고 거부한다
        byte[] good = TestTiff.encode(70, 50, 32, 32, ramp(70, 50), ByteOrder.LITTLE_ENDIAN, true, true,
                127.0, 38.0, PX, PX, false);
        byte[] truncated = java.util.Arrays.copyOf(good, good.length / 2);
        assertThatThrownBy(() -> GeoTiffDemReader.read(write(truncated))).isInstanceOf(DemException.class);
    }

    /**
     * 실제 Copernicus DEM 과 대조한다 — 이 PC 에는 있고(gitignore) CI 에는 없으므로 파일이 있을 때만 돈다.
     * 기대값은 이전에 별도 도구(Node geotiff)로 추출해 둔 backend/dem/elevations.json 의 고도다.
     */
    @Test
    void realCopernicusTileMatchesIndependentlyExtractedElevations() throws IOException {
        Path tif = Path.of("dem/Copernicus_DSM_COG_10_N37_00_E127_00_DEM.tif");
        Path json = Path.of("dem/elevations.json");
        Assumptions.assumeTrue(Files.isRegularFile(tif) && Files.isRegularFile(json),
                "실제 DEM 파일이 없어 건너뜀");

        DemRaster dem = GeoTiffDemReader.read(tif);
        assertThat(dem.width()).isEqualTo(3600);
        assertThat(dem.height()).isEqualTo(3600);
        // 타이포인트 (127, 38) + PixelIsPoint → 픽셀 (0,0) 의 중심이 (127.0, 38.0), 모서리는 반 픽셀 바깥
        assertThat(dem.originLng()).isCloseTo(127.0 - PX / 2, org.assertj.core.data.Offset.offset(1e-12));
        assertThat(dem.originLat()).isCloseTo(38.0 + PX / 2, org.assertj.core.data.Offset.offset(1e-12));
        assertThat(dem.pixelLng()).isCloseTo(PX, org.assertj.core.data.Offset.offset(1e-12));

        com.fasterxml.jackson.databind.JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(Files.readString(json));
        int checked = 0;
        for (com.fasterxml.jackson.databind.JsonNode n : root) {
            if (!n.hasNonNull("centerElev")) continue;
            // 기존 추출 도구는 (좌표 - 타일 모서리)/픽셀 을 반올림해 픽셀을 골랐다 — 픽셀 중심이 정수 초에 있으므로 같은 픽셀이다.
            int col = (int) Math.round((n.get("lng").asDouble() - 127.0) / PX);
            int row = (int) Math.round((38.0 - n.get("lat").asDouble()) / PX);
            assertThat(dem.colOf(n.get("lng").asDouble())).isEqualTo(col);
            assertThat(dem.rowOf(n.get("lat").asDouble())).isEqualTo(row);
            assertThat((double) dem.value(col, row)).isCloseTo(n.get("centerElev").asDouble(),
                    org.assertj.core.data.Offset.offset(1e-3));
            checked++;
        }
        assertThat(checked).isGreaterThan(50);
        // 성남 범위(타일 안)에 값이 없는 픽셀(NaN)이 없다
        int nan = 0;
        for (int row = dem.rowOf(37.53); row <= dem.rowOf(37.33); row++) {
            for (int col = dem.colOf(127.04); col <= dem.colOf(127.20); col++) {
                if (Float.isNaN(dem.value(col, row))) nan++;
            }
        }
        assertThat(nan).isZero();
    }
}
