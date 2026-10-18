package com.codetrace.similarity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WinnowingTest {

    private static List<Integer> positions(List<Fingerprint> fps) {
        return fps.stream().map(Fingerprint::tokenIndex).toList();
    }

    @Test
    @DisplayName("selection rule: rightmost minimum per window, no repeated position")
    void selectionRule() {
        long[] h = {3, 1, 4, 1, 5, 9, 2, 6};
        // windows (w=3): [3,1,4]->1  [1,4,1]->3 (rightmost tie)  [4,1,5]->3 (skip)  [1,5,9]->3 (skip)
        //                [5,9,2]->6  [9,2,6]->6 (skip)
        assertEquals(List.of(1, 3, 6), positions(Winnowing.winnow(h, 3)));
    }

    @Test
    @DisplayName("equal hashes across a window select the rightmost each time the window moves")
    void allEqual() {
        long[] h = {7, 7, 7, 7, 7};
        assertEquals(List.of(2, 3, 4), positions(Winnowing.winnow(h, 3)));
    }

    @Test
    @DisplayName("window boundaries: w = 1 keeps everything, w >= n yields one fingerprint, empty yields none")
    void windowBoundaries() {
        long[] h = {5, 4, 3};
        assertEquals(List.of(0, 1, 2), positions(Winnowing.winnow(h, 1)));
        assertEquals(List.of(2), positions(Winnowing.winnow(h, 3)));
        assertEquals(List.of(2), positions(Winnowing.winnow(h, 10)));
        assertTrue(Winnowing.winnow(new long[0], 4).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> Winnowing.winnow(h, 0));
    }

    @Test
    @DisplayName("k-gram hashing: count, insufficient tokens, determinism")
    void kgrams() {
        List<String> t = List.of("a", "b", "c", "d", "e");
        assertEquals(3, Winnowing.kgramHashes(t, 3).length);
        assertEquals(1, Winnowing.kgramHashes(t, 5).length);
        assertEquals(0, Winnowing.kgramHashes(t, 6).length);
        assertArrayEquals(Winnowing.kgramHashes(t, 2), Winnowing.kgramHashes(new ArrayList<>(t), 2));
        long[] h = Winnowing.kgramHashes(List.of("x", "y", "x", "y"), 2);
        assertEquals(h[0], h[2], "same k-gram, same hash");
        assertFalse(h[0] == h[1]);
    }

    @Test
    @DisplayName("every window contains a selected fingerprint (density property)")
    void everyWindowCovered() {
        Random rnd = new Random(42);
        for (int trial = 0; trial < 50; trial++) {
            int n = 20 + rnd.nextInt(200);
            int w = 1 + rnd.nextInt(10);
            long[] h = new long[n];
            for (int i = 0; i < n; i++) {
                h[i] = rnd.nextInt(30);
            }
            Set<Integer> selected = new HashSet<>(positions(Winnowing.winnow(h, w)));
            for (int s = 0; s + w <= n; s++) {
                boolean covered = false;
                for (int j = s; j < s + w; j++) {
                    covered |= selected.contains(j);
                }
                assertTrue(covered, "window starting at " + s + " has no fingerprint");
            }
        }
    }

    @Test
    @DisplayName("guarantee: shared run of length >= w + k - 1 produces a shared fingerprint")
    void detectionGuarantee() {
        int k = 5;
        int w = 4;
        Random rnd = new Random(7);
        for (int trial = 0; trial < 50; trial++) {
            List<String> shared = new ArrayList<>();
            for (int i = 0; i < w + k - 1; i++) {
                shared.add("s" + rnd.nextInt(1000));
            }
            List<String> a = new ArrayList<>();
            List<String> b = new ArrayList<>();
            for (int i = 0; i < 30; i++) {
                a.add("a" + rnd.nextInt(100000));
                b.add("b" + rnd.nextInt(100000));
            }
            a.addAll(10, shared);
            b.addAll(20, shared);
            Set<Long> ha = new HashSet<>();
            Winnowing.fingerprints(a, k, w).forEach(f -> ha.add(f.hash()));
            boolean found = Winnowing.fingerprints(b, k, w).stream().anyMatch(f -> ha.contains(f.hash()));
            assertTrue(found, "trial " + trial);
        }
    }

    @Test
    @DisplayName("identical token sequences produce identical fingerprints")
    void identicalSequences() {
        List<String> t = List.of("int", "V1", "=", "0", ";", "for", "(", "int", "V2", "=", "0", ";");
        assertEquals(Winnowing.fingerprints(t, 4, 3), Winnowing.fingerprints(List.copyOf(t), 4, 3));
    }
}
