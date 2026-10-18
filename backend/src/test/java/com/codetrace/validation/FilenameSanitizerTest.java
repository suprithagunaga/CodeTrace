package com.codetrace.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FilenameSanitizerTest {

    @Test
    @DisplayName("strips directory components (path traversal)")
    void stripsPaths() {
        assertEquals("passwd.java", FilenameSanitizer.sanitize("../../etc/passwd.java"));
        assertEquals("Main.java", FilenameSanitizer.sanitize("C:\\Users\\x\\Main.java"));
    }

    @Test
    @DisplayName("replaces markup and control characters")
    void replacesUnsafeCharacters() {
        String s = FilenameSanitizer.sanitize("<script>alert(1)</script>.java");
        assertFalse(s.contains("<") || s.contains(">") || s.contains("/"));
        assertEquals("a_b.java", FilenameSanitizer.sanitize("a\u0000b.java"));
        assertEquals("Hello World (1).java", FilenameSanitizer.sanitize("Hello World (1).java"));
    }

    @Test
    @DisplayName("removes leading dots, handles blanks and limits length keeping the extension")
    void edges() {
        assertEquals("hidden.java", FilenameSanitizer.sanitize("...hidden.java"));
        assertEquals("unnamed", FilenameSanitizer.sanitize("   "));
        assertEquals("unnamed", FilenameSanitizer.sanitize(null));
        String longName = FilenameSanitizer.sanitize("x".repeat(500) + ".java");
        assertEquals(FilenameSanitizer.MAX_LENGTH, longName.length());
        assertTrue(longName.endsWith(".java"));
    }

    @Test
    @DisplayName("extension is lower-cased")
    void extension() {
        assertEquals(".java", FilenameSanitizer.extension("A.JAVA"));
        assertEquals("", FilenameSanitizer.extension("Makefile"));
    }
}
