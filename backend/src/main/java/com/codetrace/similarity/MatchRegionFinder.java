package com.codetrace.similarity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns shared Winnowing fingerprints into approximate matching token regions.
 *
 * <ol>
 *   <li>Every pair of positions (a, b) carrying the same shared fingerprint hash is a <em>seed</em>; the k tokens
 *       at both positions are compared directly so hash collisions never produce a region.</li>
 *   <li>Each seed is extended backwards and forwards while the normalized tokens remain equal.</li>
 *   <li>Candidate regions are taken longest first and accepted only if they overlap no previously accepted region
 *       in either file (a greedy tiling). Ties are broken by start positions, so the output is deterministic.</li>
 *   <li>Regions shorter than {@code minLength} tokens are dropped. The engine uses {@code w + k - 1}, the length
 *       Winnowing is guaranteed to detect, so highlighting is consistent and short boilerplate runs are ignored.</li>
 * </ol>
 */
public final class MatchRegionFinder {

    /** Caps the cross product of positions per hash, protecting against pathological repetitive input. */
    private static final int MAX_POSITIONS_PER_HASH = 16;
    private static final int MAX_REGIONS = 500;

    private MatchRegionFinder() {
    }

    public static List<TokenRegion> find(List<String> a, List<String> b,
                                         Map<Long, List<Integer>> positionsA,
                                         Map<Long, List<Integer>> positionsB,
                                         int k, int minLength) {
        Set<TokenRegion> candidates = new HashSet<>();
        for (Map.Entry<Long, List<Integer>> entry : positionsA.entrySet()) {
            List<Integer> inB = positionsB.get(entry.getKey());
            if (inB == null) {
                continue;
            }
            List<Integer> inA = entry.getValue();
            for (int ia = 0; ia < Math.min(inA.size(), MAX_POSITIONS_PER_HASH); ia++) {
                for (int ib = 0; ib < Math.min(inB.size(), MAX_POSITIONS_PER_HASH); ib++) {
                    TokenRegion r = extend(a, b, inA.get(ia), inB.get(ib), k);
                    if (r != null && r.length() >= minLength) {
                        candidates.add(r);
                    }
                }
            }
        }

        List<TokenRegion> sorted = new ArrayList<>(candidates);
        sorted.sort(Comparator.comparingInt(TokenRegion::length).reversed()
                .thenComparingInt(TokenRegion::aStart)
                .thenComparingInt(TokenRegion::bStart));

        boolean[] usedA = new boolean[a.size()];
        boolean[] usedB = new boolean[b.size()];
        List<TokenRegion> accepted = new ArrayList<>();
        for (TokenRegion r : sorted) {
            if (accepted.size() >= MAX_REGIONS) {
                break;
            }
            if (isFree(usedA, r.aStart(), r.aEnd()) && isFree(usedB, r.bStart(), r.bEnd())) {
                mark(usedA, r.aStart(), r.aEnd());
                mark(usedB, r.bStart(), r.bEnd());
                accepted.add(r);
            }
        }
        accepted.sort(Comparator.comparingInt(TokenRegion::aStart).thenComparingInt(TokenRegion::bStart));
        return accepted;
    }

    /** Fraction of the tokens of a sequence of the given size covered by the regions on one side. */
    public static double coverage(List<TokenRegion> regions, int size, boolean sideA) {
        if (size == 0) {
            return 0.0;
        }
        long covered = 0;
        for (TokenRegion r : regions) {
            covered += sideA ? (r.aEnd() - r.aStart()) : (r.bEnd() - r.bStart());
        }
        return Math.min(1.0, (double) covered / size);
    }

    private static TokenRegion extend(List<String> a, List<String> b, int pa, int pb, int k) {
        if (pa + k > a.size() || pb + k > b.size()) {
            return null;
        }
        for (int j = 0; j < k; j++) {
            if (!a.get(pa + j).equals(b.get(pb + j))) {
                return null; // hash collision
            }
        }
        int sa = pa;
        int sb = pb;
        while (sa > 0 && sb > 0 && a.get(sa - 1).equals(b.get(sb - 1))) {
            sa--;
            sb--;
        }
        int ea = pa + k;
        int eb = pb + k;
        while (ea < a.size() && eb < b.size() && a.get(ea).equals(b.get(eb))) {
            ea++;
            eb++;
        }
        return new TokenRegion(sa, ea, sb, eb);
    }

    private static boolean isFree(boolean[] used, int from, int to) {
        for (int i = from; i < to; i++) {
            if (used[i]) {
                return false;
            }
        }
        return true;
    }

    private static void mark(boolean[] used, int from, int to) {
        for (int i = from; i < to; i++) {
            used[i] = true;
        }
    }
}
