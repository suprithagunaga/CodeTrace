package com.codetrace.tokenizer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenNormalizerTest {

    private final JavaTokenizer tokenizer = new JavaTokenizer();
    private final TokenNormalizer normalizer = new TokenNormalizer();

    private List<String> normalize(String src, NormalizationOptions options) {
        return normalizer.normalize(tokenizer.tokenize(src).tokens(), options).stream()
                .map(NormalizedToken::value).toList();
    }

    private static final String ORIGINAL = """
            package demo.math;
            import java.util.List;
            public class Calculator {
                private int total;
                public int add(int a, int b) {
                    int sum = a + b;
                    total += sum;
                    System.out.println("sum=" + sum);
                    return sum;
                }
            }
            """;

    private static final String RENAMED = """
            package demo.math;
            import java.util.List;
            public class Adder {
                private int acc;
                public int plus(int x, int y) {
                    int result = x + y;
                    acc += result;
                    System.out.println("result is " + result);
                    return result;
                }
            }
            """;

    @Test
    @DisplayName("documented example: consistently renamed identifiers normalize to the same sequence")
    void renamedIdentifiersMatch() {
        assertEquals(normalize(ORIGINAL, NormalizationOptions.identifiers()),
                normalize(RENAMED, NormalizationOptions.identifiers()));
        assertNotEquals(normalize(ORIGINAL, NormalizationOptions.original()),
                normalize(RENAMED, NormalizationOptions.original()));
    }

    @Test
    @DisplayName("role-specific placeholders: types T, methods M, variables V")
    void rolePlaceholders() {
        List<String> v = normalize(ORIGINAL, NormalizationOptions.identifiers());
        assertTrue(v.contains("T"), "class name");
        assertTrue(v.contains("M"), "method name");
        assertEquals(10, v.stream().filter("V"::equals).count(), "field, parameters and local, every occurrence");
        // documented example: "public int add(int a, int b)" becomes "public int M ( int V , int V )"
        int idx = v.indexOf("M");
        assertEquals(List.of("public", "int", "M", "(", "int", "V", ",", "int", "V", ")"), v.subList(idx - 2, idx + 8));
    }

    @Test
    @DisplayName("package names, imports and library API names stay verbatim")
    void externalNamesPreserved() {
        List<String> v = normalize(ORIGINAL, NormalizationOptions.identifiers());
        assertEquals(List.of("package", "demo", ".", "math", ";", "import", "java", ".", "util", ".", "List", ";"),
                v.subList(0, 12));
        assertTrue(v.containsAll(List.of("System", "out", "println")));
    }

    @Test
    @DisplayName("keywords are preserved and string literals become their literal type")
    void keywordsAndLiterals() {
        List<String> v = normalize("char c = 'a'; String s = \"x\"; boolean f = true; return null;",
                NormalizationOptions.identifiers());
        assertEquals(List.of("char", "V", "=", "<CHR>", ";", "String", "V", "=", "<STR>", ";",
                "boolean", "V", "=", "true", ";", "return", "null", ";"), v);
    }

    @Test
    @DisplayName("numeric literals are normalized only when requested")
    void numberNormalization() {
        String src = "int x = 10; x = x * 3;";
        assertEquals(List.of("int", "V", "=", "10", ";", "V", "=", "V", "*", "3", ";"),
                normalize(src, NormalizationOptions.identifiers()));
        assertEquals(List.of("int", "V", "=", "<NUM>", ";", "V", "=", "V", "*", "<NUM>", ";"),
                normalize(src, new NormalizationOptions(NormalizationMode.IDENTIFIERS, true)));
        assertEquals(List.of("int", "x", "=", "<NUM>", ";", "x", "=", "x", "*", "<NUM>", ";"),
                normalize(src, new NormalizationOptions(NormalizationMode.ORIGINAL, true)));
    }

    @Test
    @DisplayName("annotations, generics, arrays, varargs, for-each and lambdas")
    void declarationForms() {
        String src = """
                @Override
                public List<String> names(String[] input, int... extra) {
                    for (String item : input) { use(item); }
                    list.forEach(e -> print(e));
                    return null;
                }
                """;
        List<String> v = normalize(src, NormalizationOptions.identifiers());
        assertEquals("Override", v.get(1), "annotation name kept");
        assertTrue(v.contains("M"), "generic return type method recognised");
        int arr = v.indexOf("input");
        assertEquals(-1, arr, "array parameter renamed");
        assertEquals(-1, v.indexOf("item"), "for-each variable renamed");
        assertEquals(-1, v.indexOf("e"), "lambda parameter renamed");
        assertTrue(v.contains("use") && v.contains("print") && v.contains("forEach"), "undeclared calls kept");
    }

    @Test
    @DisplayName("normalized line view preserves original line numbers")
    void toLines() {
        String src = "int a = 1;\n\n// comment\nint b = a;\n";
        TokenizationResult tr = tokenizer.tokenize(src);
        List<String> lines = TokenNormalizer.toLines(normalizer.normalize(tr.tokens(), NormalizationOptions.identifiers()), tr.lineCount());
        assertEquals(List.of("int V = 1 ;", "", "", "int V = V ;"), lines);
    }
}
