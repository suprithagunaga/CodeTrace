package com.codetrace.similarity;

import com.codetrace.tokenizer.NormalizationMode;
import com.codetrace.tokenizer.NormalizationOptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimilarityEngineTest {

    private final SimilarityEngine engine = new SimilarityEngine(SimilarityConfig.defaults());

    static final String BUBBLE = """
            public class Sorter {
                public static void sort(int[] values) {
                    int n = values.length;
                    for (int i = 0; i < n - 1; i++) {
                        for (int j = 0; j < n - i - 1; j++) {
                            if (values[j] > values[j + 1]) {
                                int tmp = values[j];
                                values[j] = values[j + 1];
                                values[j + 1] = tmp;
                            }
                        }
                    }
                }
            }
            """;

    static final String BUBBLE_REFORMATTED = """
            public class Sorter
            {
              public static void sort(int[] values)
              {
                int n=values.length;
                for(int i=0;i<n-1;i++)
                  for(int j=0;j<n-i-1;j++)
                    if(values[j]>values[j+1]) { int tmp=values[j]; values[j]=values[j+1]; values[j+1]=tmp; }
              }
            }
            """;

    static final String BUBBLE_COMMENTED = """
            /** Sorting utilities. */
            public class Sorter {
                // classic bubble sort
                public static void sort(int[] values) {
                    int n = values.length; /* cache length */
                    for (int i = 0; i < n - 1; i++) {
                        for (int j = 0; j < n - i - 1; j++) {
                            if (values[j] > values[j + 1]) { // swap neighbours
                                int tmp = values[j];
                                values[j] = values[j + 1];
                                values[j + 1] = tmp;
                            }
                        }
                    }
                }
            }
            """;

    static final String BUBBLE_RENAMED = """
            public class ArrayOrder {
                public static void arrange(int[] data) {
                    int len = data.length;
                    for (int p = 0; p < len - 1; p++) {
                        for (int q = 0; q < len - p - 1; q++) {
                            if (data[q] > data[q + 1]) {
                                int swap = data[q];
                                data[q] = data[q + 1];
                                data[q + 1] = swap;
                            }
                        }
                    }
                }
            }
            """;

    static final String UNRELATED = """
            import java.util.HashMap;
            import java.util.Map;
            public class WordCounter {
                private final Map<String, Integer> counts = new HashMap<>();
                public void accept(String text) {
                    for (String word : text.toLowerCase().split("\\\\s+")) {
                        if (!word.isBlank()) {
                            counts.merge(word, 1, Integer::sum);
                        }
                    }
                }
                public Map<String, Integer> snapshot() { return Map.copyOf(counts); }
            }
            """;

    static final String PARTIAL = """
            import java.util.Scanner;
            public class Homework {
                public static void main(String[] args) {
                    Scanner in = new Scanner(System.in);
                    int total = 0;
                    while (in.hasNextInt()) { total += in.nextInt(); }
                    System.out.println("Total: " + total);
                }
                public static void sort(int[] values) {
                    int n = values.length;
                    for (int i = 0; i < n - 1; i++) {
                        for (int j = 0; j < n - i - 1; j++) {
                            if (values[j] > values[j + 1]) {
                                int tmp = values[j];
                                values[j] = values[j + 1];
                                values[j + 1] = tmp;
                            }
                        }
                    }
                }
            }
            """;

    private PairResult compare(String a, String b) {
        return engine.compare(engine.profile("A.java", a), engine.profile("B.java", b));
    }

    @Test
    @DisplayName("identical files receive the maximum score for every algorithm")
    void identical() {
        PairResult r = compare(BUBBLE, BUBBLE);
        assertEquals(1.0, r.jaccard(), 1e-12);
        assertEquals(1.0, r.winnowing(), 1e-12);
        assertEquals(1.0, r.overall(), 1e-12);
        assertEquals(r.fingerprintsA(), r.sharedFingerprints());
        assertEquals(1, r.regions().size());
        MatchedRegion region = r.regions().get(0);
        assertEquals(BUBBLE.strip(), BUBBLE.substring(region.fileAStartOffset(), region.fileAEndOffset()));
        assertEquals(1.0, r.coverageA(), 1e-12);
    }

    @Test
    @DisplayName("formatting-only changes do not affect normalized comparison")
    void formattingOnly() {
        PairResult r = compare(BUBBLE, BUBBLE_REFORMATTED);
        // the reformatted variant drops two pairs of braces, so the token streams differ slightly
        assertTrue(r.winnowing() > 0.6, "winnowing " + r.winnowing());
        assertTrue(r.jaccard() > 0.6, "jaccard " + r.jaccard());
        PairResult whitespaceOnly = compare(BUBBLE, BUBBLE.replace("    ", "\t").replace(" = ", "="));
        assertEquals(1.0, whitespaceOnly.overall(), 1e-12);
    }

    @Test
    @DisplayName("comment-only changes do not affect the score")
    void commentsOnly() {
        assertEquals(1.0, compare(BUBBLE, BUBBLE_COMMENTED).overall(), 1e-12);
    }

    @Test
    @DisplayName("renamed identifiers: full match in identifier mode, lower in original mode")
    void renamedIdentifiers() {
        assertEquals(1.0, compare(BUBBLE, BUBBLE_RENAMED).overall(), 1e-12);
        SimilarityEngine strict = new SimilarityEngine(new SimilarityConfig(5, 4, 3, NormalizationOptions.original()));
        PairResult r = strict.compare(strict.profile("a", BUBBLE), strict.profile("b", BUBBLE_RENAMED));
        assertTrue(r.overall() < 0.5, "original-token mode should be sensitive to renaming: " + r.overall());
    }

    @Test
    @DisplayName("partially copied code scores between unrelated and identical and is located")
    void partialOverlap() {
        PairResult r = compare(BUBBLE, PARTIAL);
        assertTrue(r.overall() > 0.25 && r.overall() < 0.9, "overall " + r.overall());
        assertFalse(r.regions().isEmpty());
        MatchedRegion longest = r.regions().stream().max((x, y) -> Integer.compare(x.tokenLength(), y.tokenLength())).orElseThrow();
        String inPartial = PARTIAL.substring(longest.fileBStartOffset(), longest.fileBEndOffset());
        assertTrue(inPartial.contains("values[j + 1] = tmp;"), "region should cover the copied method: " + inPartial);
        assertTrue(longest.fileBStartLine() >= 9, "copied method starts at line 9 of PARTIAL");
        assertTrue(r.containmentA() > r.containmentB(), "A is mostly contained in B, not vice versa");
    }

    @Test
    @DisplayName("unrelated files score low")
    void unrelated() {
        PairResult r = compare(BUBBLE, UNRELATED);
        assertTrue(r.overall() < 0.15, "overall " + r.overall());
        assertEquals(0, r.regions().stream().filter(x -> x.tokenLength() >= 15).count());
    }

    @Test
    @DisplayName("regions shorter than the Winnowing guarantee threshold (w + k - 1) are not reported")
    void minimumRegionLength() {
        int threshold = SimilarityConfig.defaults().guaranteeThreshold();
        for (String other : new String[]{UNRELATED, PARTIAL, BUBBLE_REFORMATTED}) {
            for (MatchedRegion region : compare(BUBBLE, other).regions()) {
                assertTrue(region.tokenLength() >= threshold, "region of " + region.tokenLength() + " tokens");
            }
        }
    }

    @Test
    @DisplayName("symmetric: compare(A,B) equals compare(B,A) for all scores")
    void symmetric() {
        String[] samples = {BUBBLE, BUBBLE_RENAMED, UNRELATED, PARTIAL, BUBBLE_REFORMATTED};
        for (String x : samples) {
            for (String y : samples) {
                PairResult xy = compare(x, y);
                PairResult yx = compare(y, x);
                assertEquals(xy.jaccard(), yx.jaccard(), 0.0);
                assertEquals(xy.winnowing(), yx.winnowing(), 0.0);
                assertEquals(xy.overall(), yx.overall(), 0.0);
                assertEquals(xy.sharedFingerprints(), yx.sharedFingerprints());
                assertEquals(xy.containmentA(), yx.containmentB(), 0.0);
            }
        }
    }

    @Test
    @DisplayName("stable: repeated runs give identical results")
    void stable() {
        PairResult first = compare(BUBBLE, PARTIAL);
        for (int i = 0; i < 5; i++) {
            assertEquals(first, compare(BUBBLE, PARTIAL));
        }
    }

    @Test
    @DisplayName("empty files are handled without errors")
    void emptyFiles() {
        PairResult r = compare("", BUBBLE);
        assertEquals(0.0, r.overall(), 0.0);
        assertTrue(r.regions().isEmpty());
        assertFalse(r.warnings().isEmpty());
        PairResult both = compare("// only comments", "  ");
        assertEquals(0.0, both.overall(), 0.0);
        assertFalse(engine.profile("e", "").warnings().isEmpty());
    }

    @Test
    @DisplayName("very short files: Winnowing not applicable, Jaccard still computed")
    void veryShortFiles() {
        PairResult r = compare("x++;", "x++;");
        assertEquals(1.0, r.jaccard(), 1e-12);
        assertEquals(0, r.sharedFingerprints());
        assertEquals(r.jaccard(), r.overall(), 0.0);
        assertTrue(r.warnings().stream().anyMatch(w -> w.contains("Winnowing not applicable")));
    }

    @Test
    @DisplayName("malformed source does not throw")
    void malformed() {
        assertDoesNotThrow(() -> compare("public class { int x = \"oops; /* never closed", BUBBLE));
        assertFalse(engine.profile("m", "class X { String s = \"oops;\n}").warnings().isEmpty());
    }

    @Test
    @DisplayName("configuration is validated")
    void configValidation() {
        NormalizationOptions o = new NormalizationOptions(NormalizationMode.IDENTIFIERS, false);
        assertThrows(IllegalArgumentException.class, () -> new SimilarityConfig(1, 4, 3, o));
        assertThrows(IllegalArgumentException.class, () -> new SimilarityConfig(5, 0, 3, o));
        assertThrows(IllegalArgumentException.class, () -> new SimilarityConfig(5, 4, 0, o));
        assertEquals(8, SimilarityConfig.defaults().guaranteeThreshold());
    }
}
