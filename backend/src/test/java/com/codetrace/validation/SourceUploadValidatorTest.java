package com.codetrace.validation;

import com.codetrace.exception.ApiValidationException;
import com.codetrace.exception.PayloadTooLargeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceUploadValidatorTest {

    private final SourceUploadValidator validator = new SourceUploadValidator(5, 1000, List.of(".java"));

    private static SourceUploadValidator.RawUpload file(String name, String content) {
        return new SourceUploadValidator.RawUpload(name, content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("accepts valid files and computes hashes")
    void valid() {
        List<ValidatedSource> out = validator.validate(List.of(file("A.java", "class A {}"), file("B.java", "class B {}")));
        assertEquals(2, out.size());
        assertEquals("A.java", out.get(0).displayName());
        assertEquals(64, out.get(0).sha256().length());
        assertEquals("class A {}", out.get(0).content());
    }

    @Test
    @DisplayName("missing or single file is rejected")
    void tooFew() {
        assertThrows(ApiValidationException.class, () -> validator.validate(List.of()));
        assertThrows(ApiValidationException.class, () -> validator.validate(null));
        assertThrows(ApiValidationException.class, () -> validator.validate(List.of(file("A.java", "class A {}"))));
    }

    @Test
    @DisplayName("too many files is rejected")
    void tooMany() {
        List<SourceUploadValidator.RawUpload> many = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            many.add(file("F" + i + ".java", "class F {}"));
        }
        ApiValidationException ex = assertThrows(ApiValidationException.class, () -> validator.validate(many));
        assertTrue(ex.getMessage().contains("maximum is 5"));
    }

    @Test
    @DisplayName("invalid extension, empty, whitespace, binary and invalid UTF-8 are reported per file")
    void invalidFiles() {
        SourceUploadValidator roomy = new SourceUploadValidator(10, 1000, List.of(".java"));
        ApiValidationException ex = assertThrows(ApiValidationException.class, () -> roomy.validate(List.of(
                file("A.java", "class A {}"),
                file("notes.txt", "hello"),
                file("Empty.java", ""),
                file("Blank.java", "   \n "),
                file("Bin.java", "class\u0000X"),
                new SourceUploadValidator.RawUpload("Bad.java", new byte[]{(byte) 0xC3, (byte) 0x28}))));
        assertEquals(5, ex.getDetails().size());
        assertTrue(ex.getDetails().get(0).message().contains("Unsupported file type"));
        assertEquals("notes.txt", ex.getDetails().get(0).field());
        assertTrue(ex.getDetails().get(4).message().contains("UTF-8"));
    }

    @Test
    @DisplayName("extension check is case-insensitive and ignores client paths")
    void extensionCase() {
        List<ValidatedSource> out = validator.validate(List.of(file("dir/../A.JAVA", "class A {}"), file("B.Java", "class B {}")));
        assertEquals("A.JAVA", out.get(0).displayName());
    }

    @Test
    @DisplayName("oversized file raises 413-type exception")
    void oversized() {
        assertThrows(PayloadTooLargeException.class, () -> validator.validate(List.of(
                file("A.java", "x".repeat(1001)), file("B.java", "class B {}"))));
    }

    @Test
    @DisplayName("duplicate filenames are disambiguated, not rejected")
    void duplicates() {
        List<ValidatedSource> out = validator.validate(List.of(
                file("Main.java", "class A {}"), file("Main.java", "class B {}"), file("main.java", "class C {}")));
        assertEquals(List.of("Main.java", "Main (2).java", "main (3).java"),
                out.stream().map(ValidatedSource::displayName).toList());
        assertEquals("Main.java", out.get(1).originalFilename());
    }
}
