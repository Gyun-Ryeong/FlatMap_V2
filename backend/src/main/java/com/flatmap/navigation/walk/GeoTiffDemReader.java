package com.flatmap.navigation.walk;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * 최소 GeoTIFF(고도 래스터) 리더. Copernicus DEM COG 가 쓰는 형식만 지원한다:
 * 단일 밴드 32비트 부동소수점, 타일 구조, 무압축 또는 Deflate, 예측기 없음/3(부동소수점 예측기).
 * 그 밖의 형식은 {@link DemException} 으로 거부한다(조용히 잘못 읽지 않는다).
 *
 * <p>JDK 의 TIFF 플러그인(ImageIO)은 부동소수점 예측기를 지원하지 않아 직접 읽는다.
 * 지리 참조는 ModelPixelScale(33550)·ModelTiepoint(33922)와 GeoKey RasterType(1025)만 본다 —
 * 위경도(EPSG:4326) 격자라는 가정은 호출한 쪽이 확인한다(픽셀 크기가 도 단위인지로 거른다).
 */
public final class GeoTiffDemReader {

    private static final int TAG_WIDTH = 256, TAG_HEIGHT = 257, TAG_BITS = 258, TAG_COMPRESSION = 259,
            TAG_SAMPLES = 277, TAG_PLANAR = 284, TAG_PREDICTOR = 317, TAG_TILE_W = 322, TAG_TILE_H = 323,
            TAG_TILE_OFFSETS = 324, TAG_TILE_COUNTS = 325, TAG_SAMPLE_FORMAT = 339,
            TAG_PIXEL_SCALE = 33550, TAG_TIEPOINT = 33922, TAG_GEO_KEYS = 34735;
    private static final int GEOKEY_RASTER_TYPE = 1025, RASTER_PIXEL_IS_POINT = 2;
    private static final long MAX_PIXELS = 100_000_000L;
    private static final int MAX_TILE_BYTES = 64 * 1024 * 1024;
    private static final int MAX_FIELD_BYTES = 16 * 1024 * 1024;

    private GeoTiffDemReader() {}

    public static DemRaster read(Path file) {
        try (RandomAccessFile in = new RandomAccessFile(file.toFile(), "r")) {
            return read(in, Files.size(file));
        } catch (IOException e) {
            throw new DemException("DEM 파일을 읽지 못했다", e);
        }
    }

    private static DemRaster read(RandomAccessFile in, long fileSize) throws IOException {
        byte[] head = new byte[8];
        in.readFully(head);
        ByteOrder order;
        if (head[0] == 'I' && head[1] == 'I') order = ByteOrder.LITTLE_ENDIAN;
        else if (head[0] == 'M' && head[1] == 'M') order = ByteOrder.BIG_ENDIAN;
        else throw new DemException("TIFF 파일이 아니다");
        ByteBuffer hb = ByteBuffer.wrap(head).order(order);
        if (hb.getShort(2) != 42) throw new DemException("BigTIFF 는 지원하지 않는다");
        long ifdOffset = Integer.toUnsignedLong(hb.getInt(4));

        Map<Integer, Entry> tags = readIfd(in, ifdOffset, order, fileSize);
        int width = (int) scalar(tags, TAG_WIDTH);
        int height = (int) scalar(tags, TAG_HEIGHT);
        if (width <= 0 || height <= 0 || (long) width * height > MAX_PIXELS) {
            throw new DemException("래스터 크기가 올바르지 않거나 너무 크다");
        }
        if (scalar(tags, TAG_BITS) != 32 || scalarOr(tags, TAG_SAMPLE_FORMAT, 1) != 3) {
            throw new DemException("32비트 부동소수점 래스터만 지원한다");
        }
        if (scalarOr(tags, TAG_SAMPLES, 1) != 1 || scalarOr(tags, TAG_PLANAR, 1) != 1) {
            throw new DemException("단일 밴드 래스터만 지원한다");
        }
        int compression = (int) scalarOr(tags, TAG_COMPRESSION, 1);
        if (compression != 1 && compression != 8 && compression != 32946) {
            throw new DemException("지원하지 않는 압축 방식: " + compression);
        }
        int predictor = (int) scalarOr(tags, TAG_PREDICTOR, 1);
        if (predictor != 1 && predictor != 3) throw new DemException("지원하지 않는 예측기: " + predictor);
        if (!tags.containsKey(TAG_TILE_W) || !tags.containsKey(TAG_TILE_OFFSETS)) {
            throw new DemException("타일 구조 TIFF 만 지원한다");
        }
        int tileW = (int) scalar(tags, TAG_TILE_W);
        int tileH = (int) scalar(tags, TAG_TILE_H);
        long rawBytesLong = 4L * tileW * tileH;
        if (tileW <= 0 || tileH <= 0 || rawBytesLong > MAX_TILE_BYTES) {
            throw new DemException("타일 크기가 올바르지 않다");
        }
        int rawBytes = (int) rawBytesLong;
        long[] offsets = integers(in, tags.get(TAG_TILE_OFFSETS), order, fileSize);
        long[] counts = integers(in, tags.get(TAG_TILE_COUNTS), order, fileSize);
        int tilesAcross = (width + tileW - 1) / tileW;
        int tilesDown = (height + tileH - 1) / tileH;
        if (offsets.length != (long) tilesAcross * tilesDown || counts.length != offsets.length) {
            throw new DemException("타일 개수가 맞지 않는다");
        }

        float[] data = new float[width * height];
        byte[] raw = new byte[rawBytes];
        for (int t = 0; t < offsets.length; t++) {
            if (counts[t] <= 0 || counts[t] > MAX_TILE_BYTES || offsets[t] < 0 || offsets[t] + counts[t] > fileSize) {
                throw new DemException("타일 위치가 올바르지 않다");
            }
            byte[] stored = new byte[(int) counts[t]];
            in.seek(offsets[t]);
            in.readFully(stored);
            if (compression == 1) {
                if (stored.length < rawBytes) throw new DemException("타일 데이터가 짧다");
                System.arraycopy(stored, 0, raw, 0, rawBytes);
            } else {
                inflate(stored, raw);
            }
            if (predictor == 3) undoFloatPredictor(raw, tileW, tileH, order);
            copyTile(raw, order, data, width, height, tileW, tileH, t % tilesAcross, t / tilesAcross);
        }

        double[] scale = doubles(in, tags.get(TAG_PIXEL_SCALE), order, fileSize);
        double[] tie = doubles(in, tags.get(TAG_TIEPOINT), order, fileSize);
        if (scale.length < 2 || tie.length < 6) throw new DemException("지리 참조(스케일·타이포인트)가 없다");
        double originLng = tie[3] - tie[0] * scale[0];
        double originLat = tie[4] + tie[1] * scale[1];
        if (isPixelIsPoint(in, tags.get(TAG_GEO_KEYS), order, fileSize)) {
            // 픽셀 값이 픽셀 중심의 값이면 모서리는 반 픽셀 바깥이다.
            originLng -= scale[0] / 2;
            originLat += scale[1] / 2;
        }
        return new DemRaster(width, height, originLng, originLat, scale[0], scale[1], data);
    }

    private static void inflate(byte[] stored, byte[] out) {
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(stored);
            int pos = 0;
            while (pos < out.length && !inflater.finished()) {
                int n = inflater.inflate(out, pos, out.length - pos);
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break;
                pos += n;
            }
            if (pos != out.length) throw new DemException("타일 압축 해제 크기가 맞지 않는다");
        } catch (DataFormatException e) {
            throw new DemException("타일 압축 해제 실패", e);
        } finally {
            inflater.end();
        }
    }

    /**
     * TIFF 부동소수점 예측기(예측기 3)를 되돌린다. 타일의 각 행은 ① 바이트 단위 차분 → ② 바이트 평면
     * (상위 바이트 전부, 그다음 바이트 전부 ...)으로 배치돼 있다.
     */
    static void undoFloatPredictor(byte[] tile, int tileW, int tileH, ByteOrder order) {
        int rowBytes = tileW * 4;
        byte[] row = new byte[rowBytes];
        for (int r = 0; r < tileH; r++) {
            int base = r * rowBytes;
            for (int k = 1; k < rowBytes; k++) {
                tile[base + k] = (byte) (tile[base + k] + tile[base + k - 1]);
            }
            System.arraycopy(tile, base, row, 0, rowBytes);
            for (int i = 0; i < tileW; i++) {
                for (int b = 0; b < 4; b++) {          // b = 0 이 상위 바이트
                    int dst = order == ByteOrder.LITTLE_ENDIAN ? 3 - b : b;
                    tile[base + i * 4 + dst] = row[b * tileW + i];
                }
            }
        }
    }

    private static void copyTile(byte[] raw, ByteOrder order, float[] data, int width, int height,
                                 int tileW, int tileH, int tx, int ty) {
        ByteBuffer bb = ByteBuffer.wrap(raw).order(order);
        int x0 = tx * tileW, y0 = ty * tileH;
        int w = Math.min(tileW, width - x0), h = Math.min(tileH, height - y0);
        for (int r = 0; r < h; r++) {
            for (int c = 0; c < w; c++) {
                data[(y0 + r) * width + x0 + c] = bb.getFloat((r * tileW + c) * 4);
            }
        }
    }

    // ── IFD 파싱 ───────────────────────────────────────────────────────────────

    /** 필드 하나. {@code inline} 은 4바이트 값 칸(파일 바이트 순서)이다. 값이 4바이트를 넘으면 그 칸이 파일 오프셋이다. */
    private record Entry(int type, long count, ByteBuffer inline) {
    }

    private static Map<Integer, Entry> readIfd(RandomAccessFile in, long offset, ByteOrder order, long fileSize)
            throws IOException {
        if (offset <= 0 || offset + 2 > fileSize) throw new DemException("IFD 위치가 올바르지 않다");
        in.seek(offset);
        byte[] nb = new byte[2];
        in.readFully(nb);
        int n = ByteBuffer.wrap(nb).order(order).getShort() & 0xFFFF;
        if (n == 0 || offset + 2 + n * 12L > fileSize) throw new DemException("IFD 가 올바르지 않다");
        byte[] raw = new byte[n * 12];
        in.readFully(raw);
        ByteBuffer bb = ByteBuffer.wrap(raw).order(order);
        Map<Integer, Entry> map = new HashMap<>();
        for (int i = 0; i < n; i++) {
            int o = i * 12;
            int tag = bb.getShort(o) & 0xFFFF;
            int type = bb.getShort(o + 2) & 0xFFFF;
            long count = Integer.toUnsignedLong(bb.getInt(o + 4));
            byte[] cell = new byte[4];
            System.arraycopy(raw, o + 8, cell, 0, 4);
            map.put(tag, new Entry(type, count, ByteBuffer.wrap(cell).order(order)));
        }
        return map;
    }

    private static int typeSize(int type) {
        return switch (type) {
            case 1, 2, 6, 7 -> 1;
            case 3, 8 -> 2;
            case 4, 9, 11 -> 4;
            case 5, 10, 12, 16 -> 8;
            default -> throw new DemException("지원하지 않는 TIFF 필드 형식: " + type);
        };
    }

    /** 필드 값 영역. 4바이트 이하면 칸 안에, 아니면 파일의 오프셋에 있다. */
    private static ByteBuffer payload(RandomAccessFile in, Entry e, ByteOrder order, long fileSize)
            throws IOException {
        long size = (long) typeSize(e.type()) * e.count();
        if (size > MAX_FIELD_BYTES) throw new DemException("TIFF 필드가 너무 크다");
        if (size <= 4) return e.inline();
        long offset = Integer.toUnsignedLong(e.inline().getInt(0));
        if (offset + size > fileSize) throw new DemException("TIFF 필드 위치가 올바르지 않다");
        byte[] b = new byte[(int) size];
        in.seek(offset);
        in.readFully(b);
        return ByteBuffer.wrap(b).order(order);
    }

    private static long[] integers(RandomAccessFile in, Entry e, ByteOrder order, long fileSize) throws IOException {
        if (e == null) throw new DemException("필수 TIFF 필드가 없다");
        ByteBuffer bb = payload(in, e, order, fileSize);
        long[] out = new long[(int) e.count()];
        for (int i = 0; i < out.length; i++) {
            out[i] = switch (e.type()) {
                case 3 -> bb.getShort(i * 2) & 0xFFFF;
                case 4 -> Integer.toUnsignedLong(bb.getInt(i * 4));
                case 16 -> bb.getLong(i * 8);
                default -> throw new DemException("정수 필드 형식이 아니다");
            };
        }
        return out;
    }

    /** 값이 하나인 정수 필드(칸 안에 있다). */
    private static long scalar(Map<Integer, Entry> tags, int tag) {
        Entry e = tags.get(tag);
        if (e == null || e.count() < 1) throw new DemException("필수 TIFF 필드가 없다: " + tag);
        return switch (e.type()) {
            case 3 -> e.inline().getShort(0) & 0xFFFF;
            case 4 -> Integer.toUnsignedLong(e.inline().getInt(0));
            default -> throw new DemException("정수 필드 형식이 아니다: " + tag);
        };
    }

    private static long scalarOr(Map<Integer, Entry> tags, int tag, long fallback) {
        return tags.containsKey(tag) ? scalar(tags, tag) : fallback;
    }

    private static double[] doubles(RandomAccessFile in, Entry e, ByteOrder order, long fileSize) throws IOException {
        if (e == null || e.type() != 12) throw new DemException("지리 참조 필드가 없다");
        ByteBuffer bb = payload(in, e, order, fileSize);
        double[] out = new double[(int) e.count()];
        for (int i = 0; i < out.length; i++) out[i] = bb.getDouble(i * 8);
        return out;
    }

    private static boolean isPixelIsPoint(RandomAccessFile in, Entry e, ByteOrder order, long fileSize)
            throws IOException {
        if (e == null || e.type() != 3 || e.count() < 4) return false;
        ByteBuffer bb = payload(in, e, order, fileSize);
        int keys = bb.getShort(6) & 0xFFFF;
        for (int k = 0; k < keys && (4 + 4 * k + 4) <= e.count(); k++) {
            int o = (4 + k * 4) * 2;
            if ((bb.getShort(o) & 0xFFFF) == GEOKEY_RASTER_TYPE && (bb.getShort(o + 2) & 0xFFFF) == 0) {
                return (bb.getShort(o + 6) & 0xFFFF) == RASTER_PIXEL_IS_POINT;
            }
        }
        return false;
    }
}
