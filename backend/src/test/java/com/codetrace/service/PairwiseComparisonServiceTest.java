package com.codetrace.service;

import com.codetrace.similarity.SimilarityConfig;
import com.codetrace.validation.ValidatedSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PairwiseComparisonServiceTest {

    private final PairwiseComparisonService service = new PairwiseComparisonService(new TokenizationService());

    private static List<ValidatedSource> sources(int n) {
        List<ValidatedSource> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String code = "class C" + i + " { int f(int x) { return x * " + i + " + " + (i * 7) + "; } }";
            list.add(new ValidatedSource("C" + i + ".java", "C" + i + ".java", code.length(), "h" + i, code));
        }
        return list;
    }

    @Test
    @DisplayName("n files produce exactly n*(n-1)/2 unique unordered pairs")
    void pairCount() {
        for (int n = 2; n <= 7; n++) {
            var result = service.compareAll(sources(n), SimilarityConfig.defaults());
            assertEquals(n * (n - 1) / 2, result.pairs().size());
            assertEquals(PairwiseComparisonService.expectedPairCount(n), result.pairs().size());
            Set<String> seen = new HashSet<>();
            for (var p : result.pairs()) {
                assertTrue(p.indexA() < p.indexB(), "pairs use a canonical order");
                assertTrue(seen.add(p.indexA() + "-" + p.indexB()), "each pair exactly once");
            }
        }
    }

    @Test
    @DisplayName("profiles are produced for every file in upload order")
    void profiles() {
        var result = service.compareAll(sources(3), SimilarityConfig.defaults());
        assertEquals(List.of("C0.java", "C1.java", "C2.java"), result.profiles().stream().map(p -> p.name()).toList());
    }
}
