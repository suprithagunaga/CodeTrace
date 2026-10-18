package com.codetrace.similarity;

/**
 * A run of identical normalized tokens shared by two files. Token indices are half-open: [start, end).
 */
public record TokenRegion(int aStart, int aEnd, int bStart, int bEnd) {

    public int length() {
        return aEnd - aStart;
    }
}
