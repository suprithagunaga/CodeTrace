package com.codetrace.similarity;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Set-based Jaccard similarity: {@code J(A,B) = |A ∩ B| / |A ∪ B|}.
 *
 * <p>Jaccard ignores order and multiplicity; it says how much of the <em>vocabulary</em> of two token
 * collections is shared, not whether tokens appear in the same sequence. Sequence evidence comes from
 * {@link Winnowing} and {@link MatchRegionFinder}. To retain a little local order, CodeTrace applies
 * Jaccard to token n-gram "shingles" (n = 1 gives plain token sets).
 */
public final class JaccardSimilarity {

    private static final char SEPARATOR = '\u0001';

    private JaccardSimilarity() {
    }

    /**
     * @return a value in [0, 1]; {@code 0.0} when both sets are empty (no evidence of similarity).
     */
    public static <T> double compute(Set<T> a, Set<T> b) {
        Objects.requireNonNull(a, "a");
        Objects.requireNonNull(b, "b");
        if (a.isEmpty() && b.isEmpty()) {
            return 0.0;
        }
        Set<T> small = a.size() <= b.size() ? a : b;
        Set<T> large = small == a ? b : a;
        long intersection = 0;
        for (T item : small) {
            if (large.contains(item)) {
                intersection++;
            }
        }
        long union = (long) a.size() + b.size() - intersection;
        return union == 0 ? 0.0 : (double) intersection / union;
    }

    /**
     * Builds the set of contiguous token n-grams. If the sequence is shorter than {@code n}
     * but non-empty, the whole sequence becomes a single shingle so tiny files can still be compared.
     */
    public static Set<String> shingles(List<String> tokens, int n) {
        if (n < 1) {
            throw new IllegalArgumentException("n must be >= 1");
        }
        Set<String> result = new HashSet<>();
        if (tokens.isEmpty()) {
            return result;
        }
        if (tokens.size() < n) {
            result.add(String.join(String.valueOf(SEPARATOR), tokens));
            return result;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i + n <= tokens.size(); i++) {
            sb.setLength(0);
            for (int j = 0; j < n; j++) {
                if (j > 0) {
                    sb.append(SEPARATOR);
                }
                sb.append(tokens.get(i + j));
            }
            result.add(sb.toString());
        }
        return result;
    }
}
