package com.flatmap.navigation.walk;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.Deflater;

/** 테스트용 GeoTIFF 인코더(타일·Deflate·부동소수점 예측기). 리더와 같은 형식을 직접 만들어 왕복(round-trip)을 검증한다. */
final class TestTiff {

    private TestTiff() {}

    static byte[] encode(int width, int height, int tileW, int tileH, float[] values, ByteOrder order,
                         boolean deflate, boolean predictor3, double originLng, double originLat,
                         double pixelLng, double pixelLat, boolean pixelIsPoint) {
        int tilesAcross = (width + tileW - 1) / tileW;
        int tilesDown = (height + tileH - 1) / tileH;
        List<byte[]> tiles = new ArrayList<>();
        for (int ty = 0; ty < tilesDown; ty++) {
            for (int tx = 0; tx < tilesAcross; tx++) {
                byte[] raw = new byte[tileW * tileH * 4];
                ByteBuffer bb = ByteBuffer.wrap(raw).order(order);
                for (int r = 0; r < tileH; r++) {
                    for (int c = 0; c < tileW; c++) {
                        int x = tx * tileW + c, y = ty * tileH + r;
                        float v = x < width && y < height ? values[y * width + x] : 0f;
                        bb.putFloat((r * tileW + c) * 4, v);
                    }
                }
                if (predictor3) applyFloatPredictor(raw, tileW, tileH, order);
                tiles.add(deflate ? deflate(raw) : raw);
            }
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteBuffer head = ByteBuffer.allocate(8).order(order);
        head.put((byte) (order == ByteOrder.LITTLE_ENDIAN ? 'I' : 'M'));
        head.put((byte) (order == ByteOrder.LITTLE_ENDIAN ? 'I' : 'M'));
        head.putShort((short) 42);
        head.putInt(0);                       // IFD 오프셋은 아래에서 채운다
        out.writeBytes(head.array());

        long[] tileOffsets = new long[tiles.size()];
        long[] tileCounts = new long[tiles.size()];
        for (int t = 0; t < tiles.size(); t++) {
            tileOffsets[t] = out.size();
            tileCounts[t] = tiles.get(t).length;
            out.writeBytes(tiles.get(t));
        }

        // 값이 4바이트를 넘는 필드의 본문 영역
        ByteArrayOutputStream extra = new ByteArrayOutputStream();
        long extraBase = out.size();
        List<Object[]> entries = new ArrayList<>();             // {tag, type, count, byte[] 값}

        entries.add(entry(256, 4, 1, longs(order, width)));
        entries.add(entry(257, 4, 1, longs(order, height)));
        entries.add(entry(258, 3, 1, shorts(order, 32)));
        entries.add(entry(259, 3, 1, shorts(order, deflate ? 8 : 1)));
        entries.add(entry(262, 3, 1, shorts(order, 1)));
        entries.add(entry(277, 3, 1, shorts(order, 1)));
        entries.add(entry(284, 3, 1, shorts(order, 1)));
        if (predictor3) entries.add(entry(317, 3, 1, shorts(order, 3)));
        entries.add(entry(322, 4, 1, longs(order, tileW)));
        entries.add(entry(323, 4, 1, longs(order, tileH)));
        entries.add(entry(324, 4, tiles.size(), longs(order, tileOffsets)));
        entries.add(entry(325, 4, tiles.size(), longs(order, tileCounts)));
        entries.add(entry(339, 3, 1, shorts(order, 3)));
        entries.add(entry(33550, 12, 3, doubles(order, pixelLng, pixelLat, 0)));
        entries.add(entry(33922, 12, 6, doubles(order, 0, 0, 0, originLng, originLat, 0)));
        // GeoKeyDirectory: 헤더(버전 1.1.0, 키 1개) + RasterType 키
        entries.add(entry(34735, 3, 8, shorts(order, 1, 1, 0, 1, 1025, 0, 1, pixelIsPoint ? 2 : 1)));
        entries.sort((a, b) -> Integer.compare((int) a[0], (int) b[0]));

        ByteBuffer ifd = ByteBuffer.allocate(2 + entries.size() * 12 + 4).order(order);
        ifd.putShort((short) entries.size());
        for (Object[] e : entries) {
            byte[] value = (byte[]) e[3];
            ifd.putShort((short) (int) e[0]);
            ifd.putShort((short) (int) e[1]);
            ifd.putInt((int) e[2]);
            if (value.length <= 4) {
                byte[] cell = new byte[4];
                System.arraycopy(value, 0, cell, 0, value.length);
                ifd.put(cell);
            } else {
                if ((extra.size() & 1) == 1) extra.write(0);
                ifd.putInt((int) (extraBase + extra.size()));
                extra.writeBytes(value);
            }
        }
        ifd.putInt(0);
        out.writeBytes(extra.toByteArray());
        if ((out.size() & 1) == 1) out.write(0);
        long ifdOffset = out.size();
        out.writeBytes(ifd.array());

        byte[] file = out.toByteArray();
        ByteBuffer.wrap(file).order(order).putInt(4, (int) ifdOffset);
        return file;
    }

    /** 리더의 undoFloatPredictor 의 정반대: 바이트 평면으로 나눈 뒤 바이트 차분. */
    private static void applyFloatPredictor(byte[] tile, int tileW, int tileH, ByteOrder order) {
        int rowBytes = tileW * 4;
        byte[] row = new byte[rowBytes];
        for (int r = 0; r < tileH; r++) {
            int base = r * rowBytes;
            for (int i = 0; i < tileW; i++) {
                for (int b = 0; b < 4; b++) {
                    int src = order == ByteOrder.LITTLE_ENDIAN ? 3 - b : b;
                    row[b * tileW + i] = tile[base + i * 4 + src];
                }
            }
            for (int k = rowBytes - 1; k >= 1; k--) {
                row[k] = (byte) (row[k] - row[k - 1]);
            }
            System.arraycopy(row, 0, tile, base, rowBytes);
        }
    }

    private static byte[] deflate(byte[] raw) {
        Deflater d = new Deflater();
        d.setInput(raw);
        d.finish();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        while (!d.finished()) {
            int n = d.deflate(buf);
            bos.write(buf, 0, n);
        }
        d.end();
        return bos.toByteArray();
    }

    private static Object[] entry(int tag, int type, int count, byte[] value) {
        return new Object[]{tag, type, count, value};
    }

    private static byte[] shorts(ByteOrder order, int... v) {
        ByteBuffer bb = ByteBuffer.allocate(v.length * 2).order(order);
        for (int x : v) bb.putShort((short) x);
        return bb.array();
    }

    private static byte[] longs(ByteOrder order, long... v) {
        ByteBuffer bb = ByteBuffer.allocate(v.length * 4).order(order);
        for (long x : v) bb.putInt((int) x);
        return bb.array();
    }

    private static byte[] doubles(ByteOrder order, double... v) {
        ByteBuffer bb = ByteBuffer.allocate(v.length * 8).order(order);
        for (double x : v) bb.putDouble(x);
        return bb.array();
    }
}
