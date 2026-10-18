package com.codetrace.similarity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JaccardSimilarityTest {

    @Test
    @DisplayName("identical sets score 1")
    void identical() {
        assertEquals(1.0, JaccardSimilarity.compute(Set.of("a", "b", "c"), Set.of("a", "b", "c")), 1e-12);
    }

    @Test
    @DisplayName("disjoint sets score 0")
    void disjoint() {
        assertEquals(0.0, JaccardSimilarity.compute(Set.of("a", "b"), Set.of("c", "d")), 1e-12);
    }

    @Test
    @DisplayName("partial overlap: |{b}| / |{a,b,c}| = 1/3")
    void partial() {
        assertEquals(1.0 / 3.0, JaccardSimilarity.compute(Set.of("a", "b"), Set.of("b", "c")), 1e-12);
        assertEquals(0.75, JaccardSimilarity.compute(Set.of(1, 2, 3), Set.of(2, 3, 4, 1)), 1e-12);
    }

    @Test
    @DisplayName("empty inputs are safe")
    void empty() {
        assertEquals(0.0, JaccardSimilarity.compute(Set.of(), Set.of()), 1e-12);
        assertEquals(0.0, JaccardSimilarity.compute(Set.of("a"), Set.of()), 1e-12);
    }

    @Test
    @DisplayName("symmetric and bounded")
    void symmetric() {
        Set<String> a = Set.of("x", "y", "z", "w");
        Set<String> b = Set.of("y", "q");
        double ab = JaccardSimilarity.compute(a, b);
        assertEquals(ab, JaccardSimilarity.compute(b, a), 0.0);
        assertTrue(ab >= 0 && ab <= 1);
    }

    @Test
    @DisplayName("shingles: n-grams, short sequences and n = 1")
    void shingles() {
        assertEquals(3, JaccardSimilarity.shingles(List.of("a", "b", "c", "d"), 2).size());
        assertEquals(1, JaccardSimilarity.shingles(List.of("a"), 3).size());
        assertEquals(0, JaccardSimilarity.shingles(List.of(), 3).size());
        assertEquals(Set.of("a", "b"), JaccardSimilarity.shingles(List.of("a", "b", "a"), 1));
        assertThrows(IllegalArgumentException.class, () -> JaccardSimilarity.shingles(List.of("a"), 0));
    }

    @Test
    @DisplayName("set similarity ignores order: reversed sequence has Jaccard 1 on unigrams")
    void setNotSequence() {
        Set<String> forward = JaccardSimilarity.shingles(List.of("a", "b", "c"), 1);
        Set<String> reversed = JaccardSimilarity.shingles(List.of("c", "b", "a"), 1);
        assertEquals(1.0, JaccardSimilarity.compute(forward, reversed), 1e-12);
        Set<String> f2 = JaccardSimilarity.shingles(List.of("a", "b", "c"), 2);
        Set<String> r2 = JaccardSimilarity.shingles(List.of("c", "b", "a"), 2);
        assertEquals(0.0, JaccardSimilarity.compute(f2, r2), 1e-12);
    }
}
