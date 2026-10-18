package com.codetrace.tokenizer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaTokenizerTest {

    private final JavaTokenizer tokenizer = new JavaTokenizer();

    private List<String> texts(String src) {
        return tokenizer.tokenize(src).tokens().stream().map(Token::text).toList();
    }

    private List<TokenType> types(String src) {
        return tokenizer.tokenize(src).tokens().stream().map(Token::type).toList();
    }

    @Test
    @DisplayName("recognises keywords, identifiers, literals, operators and separators")
    void basicCategories() {
        String src = "int count = 42; String s = \"hi\"; char c = 'x'; if (count >= 10) count++;";
        assertEquals(List.of("int", "count", "=", "42", ";", "String", "s", "=", "\"hi\"", ";",
                "char", "c", "=", "'x'", ";", "if", "(", "count", ">=", "10", ")", "count", "++", ";"), texts(src));
        List<TokenType> t = types(src);
        assertEquals(TokenType.KEYWORD, t.get(0));
        assertEquals(TokenType.IDENTIFIER, t.get(1));
        assertEquals(TokenType.OPERATOR, t.get(2));
        assertEquals(TokenType.NUMBER, t.get(3));
        assertEquals(TokenType.SEPARATOR, t.get(4));
        assertEquals(TokenType.STRING, t.get(8));
        assertEquals(TokenType.CHAR, t.get(13));
    }

    @Test
    @DisplayName("line and block comments are ignored")
    void commentsIgnored() {
        String src = "int a = 1; // trailing comment\n/* block\n comment */ int b = 2; /** javadoc */";
        assertEquals(List.of("int", "a", "=", "1", ";", "int", "b", "=", "2", ";"), texts(src));
    }

    @Test
    @DisplayName("comment-like text inside string literals is preserved")
    void commentMarkersInsideStrings() {
        String src = "String url = \"http://example.com/*path*/\"; String c = \"// not a comment\"; int x = 1;";
        List<Token> tokens = tokenizer.tokenize(src).tokens();
        assertEquals("\"http://example.com/*path*/\"", tokens.get(3).text());
        assertEquals("\"// not a comment\"", tokens.get(8).text());
        assertEquals("x", tokens.get(11).text(), "tokens after the strings must still be produced");
        assertTrue(tokenizer.tokenize(src).warnings().isEmpty());
    }

    @Test
    @DisplayName("escaped quotes and characters stay inside their literal")
    void escapedCharacters() {
        String src = "String s = \"say \\\"hi\\\" \\\\\"; char q = '\\''; char b = '\\\\'; char u = '\\u0041';";
        List<Token> tokens = tokenizer.tokenize(src).tokens();
        assertEquals("\"say \\\"hi\\\" \\\\\"", tokens.get(3).text());
        assertEquals(TokenType.STRING, tokens.get(3).type());
        assertEquals("'\\''", tokens.get(8).text());
        assertEquals("'\\\\'", tokens.get(13).text());
        assertEquals("'\\u0041'", tokens.get(18).text());
        assertEquals(20, tokens.size());
    }

    @Test
    @DisplayName("text blocks are a single token, including embedded quotes and comment markers")
    void textBlocks() {
        String src = "String j = \"\"\"\n  {\"a\": \"/* x */\", \"b\": \\\"\"\"\n  }\n  \"\"\";\nint z;";
        List<Token> tokens = tokenizer.tokenize(src).tokens();
        assertEquals(TokenType.TEXT_BLOCK, tokens.get(3).type());
        assertEquals(1, tokens.get(3).line());
        assertEquals(4, tokens.get(3).endLine());
        assertEquals(List.of(";", "int", "z", ";"), tokens.subList(4, 8).stream().map(Token::text).toList());
    }

    @Test
    @DisplayName("numeric literal forms")
    void numbers() {
        String src = "a = 0xFF + 0b1010 + 1_000_000L + 3.14e-2f + .5 + 1. + 2d + 077;";
        List<Token> numbers = tokenizer.tokenize(src).tokens().stream()
                .filter(t -> t.type() == TokenType.NUMBER).toList();
        assertEquals(List.of("0xFF", "0b1010", "1_000_000L", "3.14e-2f", ".5", "1.", "2d", "077"),
                numbers.stream().map(Token::text).toList());
    }

    @Test
    @DisplayName("method reference and member access on numbers do not break tokenization")
    void dotsAfterIdentifiers() {
        assertEquals(List.of("list", ".", "forEach", "(", "System", ".", "out", "::", "println", ")", ";"),
                texts("list.forEach(System.out::println);"));
        assertEquals(List.of("String", "...", "args"), texts("String... args"));
    }

    @Test
    @DisplayName("operators use longest match")
    void longestMatchOperators() {
        assertEquals(List.of("x", ">>>=", "2", ";", "y", "->", "y", "!=", "z"), texts("x >>>= 2; y -> y != z"));
        assertEquals(List.of("a", "/", "b"), texts("a / b"));
    }

    @Test
    @DisplayName("positions: offsets, lines and columns map to the original text")
    void positions() {
        String src = "class A {\n  int x = 1;\r\n\tvoid m() {}\n}";
        List<Token> tokens = tokenizer.tokenize(src).tokens();
        for (Token t : tokens) {
            assertEquals(t.text(), src.substring(t.startOffset(), t.endOffset()));
        }
        Token x = tokens.get(4);
        assertEquals("x", x.text());
        assertEquals(2, x.line());
        assertEquals(7, x.column());
        Token m = tokens.stream().filter(t -> t.text().equals("m")).findFirst().orElseThrow();
        assertEquals(3, m.line());
        assertEquals(7, m.column());
        assertEquals(4, tokenizer.tokenize(src).lineCount());
    }

    @Test
    @DisplayName("malformed source is handled gracefully with warnings")
    void malformedSource() {
        TokenizationResult r1 = tokenizer.tokenize("String s = \"unterminated;\nint y = 2;");
        assertFalse(r1.warnings().isEmpty());
        assertTrue(r1.warnings().get(0).contains("Unterminated string"));
        assertTrue(texts("String s = \"unterminated;\nint y = 2;").contains("y"), "scanner recovers on next line");

        TokenizationResult r2 = tokenizer.tokenize("int a; /* never closed");
        assertEquals(3, r2.tokens().size());
        assertTrue(r2.warnings().get(0).contains("Unterminated block comment"));

        TokenizationResult r3 = tokenizer.tokenize("int # a;");
        assertEquals(TokenType.UNKNOWN, r3.tokens().get(1).type());
        assertEquals(1, r3.warnings().size());

        TokenizationResult r4 = tokenizer.tokenize("char c = '");
        assertEquals(TokenType.CHAR, r4.tokens().get(3).type());
        assertFalse(r4.warnings().isEmpty());
    }

    @Test
    @DisplayName("empty and whitespace-only input")
    void emptyInput() {
        assertEquals(0, tokenizer.tokenize("").tokens().size());
        assertEquals(0, tokenizer.tokenize("").lineCount());
        assertEquals(0, tokenizer.tokenize("   \n\t  // only a comment\n").tokens().size());
    }

    @Test
    @DisplayName("unicode identifiers and byte-order mark")
    void unicode() {
        assertEquals(List.of("int", "größe", "=", "1", ";"), texts("﻿int größe = 1;"));
    }
}
