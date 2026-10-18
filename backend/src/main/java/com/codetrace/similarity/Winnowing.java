package com.codetrace.similarity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Token-based k-gram hashing and Winnowing fingerprint selection
 * (Schleimer, Wilkerson &amp; Aiken, "Winnowing: Local Algorithms for Document Fingerprinting", SIGMOD 2003).
 *
 * <h2>Parameters</h2>
 * <ul>
 *   <li><b>k</b> (k-gram size): number of consecutive normalized tokens hashed together. Matches shorter than
 *       k tokens are never detected (noise threshold); larger k reduces coincidental matches.</li>
 *   <li><b>w</b> (window size): number of consecutive k-gram hashes in each window. Winnowing guarantees that any
 *       shared run of at least {@code w + k - 1} tokens produces at least one shared fingerprint. Larger w stores
 *       fewer fingerprints (density about 2/(w+1)).</li>
 * </ul>
 *
 * <h2>Deterministic selection rule</h2>
 * In every window the minimum hash is selected, comparing hashes as signed 64-bit integers. If the minimum occurs
 * more than once in the window, the <em>rightmost</em> occurrence is chosen. A position is recorded only if it differs
 * from the position recorded for the previous window, so the same fingerprint is not emitted repeatedly while it
 * stays the minimum of overlapping windows.
 *
 * <h2>Short inputs</h2>
 * Fewer than k tokens: no k-grams and no fingerprints. Fewer than w k-grams: the whole sequence is one window and its
 * minimum is the single fingerprint.
 */
public final class Winnowing {

    private static final long FNV_OFFSET = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    private Winnowing() {
    }

    /** Hash of each k-gram, index i covering tokens [i, i+k). Empty when there are fewer than k tokens. */
    public static long[] kgramHashes(List<String> tokens, int k) {
        Objects.requireNonNull(tokens, "tokens");
        if (k < 1) {
            throw new IllegalArgumentException("k must be >= 1");
        }
        int count = tokens.size() - k + 1;
        if (count <= 0) {
            return new long[0];
        }
        long[] tokenHashes = new long[tokens.size()];
        for (int i = 0; i < tokens.size(); i++) {
            tokenHashes[i] = fnv1a(tokens.get(i));
        }
        long[] result = new long[count];
        for (int i = 0; i < count; i++) {
            long h = FNV_OFFSET;
            for (int j = 0; j < k; j++) {
                h ^= tokenHashes[i + j];
                h *= FNV_PRIME;
            }
            result[i] = mix(h);
        }
        return result;
    }

    /** Applies the Winnowing selection rule described in the class documentation. */
    public static List<Fingerprint> winnow(long[] hashes, int w) {
        Objects.requireNonNull(hashes, "hashes");
        if (w < 1) {
            throw new IllegalArgumentException("w must be >= 1");
        }
        List<Fingerprint> out = new ArrayList<>();
        int n = hashes.length;
        if (n == 0) {
            return out;
        }
        if (n <= w) {
            int minIdx = rightmostMin(hashes, 0, n);
            out.add(new Fingerprint(hashes[minIdx], minIdx));
            return out;
        }
        int previous = -1;
        for (int start = 0; start + w <= n; start++) {
            int minIdx = rightmostMin(hashes, start, start + w);
            if (minIdx != previous) {
                out.add(new Fingerprint(hashes[minIdx], minIdx));
                previous = minIdx;
            }
        }
        return out;
    }

    /** Convenience: k-gram hashing followed by winnowing. */
    public static List<Fingerprint> fingerprints(List<String> tokens, int k, int w) {
        return winnow(kgramHashes(tokens, k), w);
    }

    private static int rightmostMin(long[] hashes, int from, int to) {
        int minIdx = from;
        for (int j = from + 1; j < to; j++) {
            if (hashes[j] <= hashes[minIdx]) {
                minIdx = j;
            }
        }
        return minIdx;
    }

    /** 64-bit FNV-1a over the UTF-16 code units of the string (deterministic across JVMs). */
    static long fnv1a(String s) {
        long h = FNV_OFFSET;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            h ^= (c & 0xff);
            h *= FNV_PRIME;
            h ^= (c >>> 8);
            h *= FNV_PRIME;
        }
        return h;
    }

    /** SplitMix64 finalizer to spread bits so that the minimum selection is unbiased. */
    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }
}
