package com.flatmap.navigation.walk;

import java.util.Arrays;

/** 적재할 경사 셀 묶음(수십만 개라 객체 대신 평행 배열). 경사는 각도(°). */
public final class SlopeCells {

    private int[] i;
    private int[] j;
    private double[] slopeDeg;
    private float[] elevationM;
    private int size;

    public SlopeCells(int initialCapacity) {
        int cap = Math.max(16, initialCapacity);
        i = new int[cap];
        j = new int[cap];
        slopeDeg = new double[cap];
        elevationM = new float[cap];
    }

    public void add(int cellI, int cellJ, double slope, float elevation) {
        if (size == i.length) {
            int cap = size * 2;
            i = Arrays.copyOf(i, cap);
            j = Arrays.copyOf(j, cap);
            slopeDeg = Arrays.copyOf(slopeDeg, cap);
            elevationM = Arrays.copyOf(elevationM, cap);
        }
        i[size] = cellI;
        j[size] = cellJ;
        slopeDeg[size] = slope;
        elevationM[size] = elevation;
        size++;
    }

    public int size() {
        return size;
    }

    public int cellI(int k) {
        return i[k];
    }

    public int cellJ(int k) {
        return j[k];
    }

    public double slopeDeg(int k) {
        return slopeDeg[k];
    }

    public float elevationM(int k) {
        return elevationM[k];
    }
}
