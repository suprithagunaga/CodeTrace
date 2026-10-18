package com.codetrace.tokenizer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A hand-written, single-pass lexical scanner for Java source code.
 *
 * <p>Design notes:
 * <ul>
 *   <li>Comments are skipped by the scanner itself, not by regular expressions, so comment-like
 *       text inside string, character and text-block literals is preserved correctly.</li>
 *   <li>Escape sequences inside literals are honoured ({@code "a\"b"} is one token).</li>
 *   <li>Every token keeps its original character offsets, line and column.</li>
 *   <li>Malformed input never throws: unterminated comments/literals and unknown characters are
 *       recovered from and reported as warnings.</li>
 *   <li>Unicode escapes ({@code A}) outside literals are not pre-translated (a documented limitation).</li>
 * </ul>
 * The class is stateless and thread-safe.
 */
public final class JavaTokenizer {

    /** Longest operators first so that greedy matching is correct. */
    private static final String[] OPERATORS = {
            ">>>=", "<<=", ">>=", ">>>", "...", "->", "::", "++", "--", "&&", "||", "==", "!=", "<=", ">=",
            "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "<<", ">>",
            "+", "-", "*", "/", "%", "=", "<", ">", "!", "~", "?", ":", "&", "|", "^"
    };
    private static final String SEPARATORS = "(){}[];,.@";
    private static final int MAX_WARNINGS = 25;

    public TokenizationResult tokenize(String source) {
        Objects.requireNonNull(source, "source");
        return new Scanner(source).run();
    }

    private static final class Scanner {
        private final String src;
        private final int n;
        private int pos;
        private int line = 1;
        private int col = 1;
        private final List<Token> tokens = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        private int suppressedWarnings;

        Scanner(String src) {
            this.src = src;
            this.n = src.length();
        }

        TokenizationResult run() {
            if (n > 0 && src.charAt(0) == '﻿') {
                advanceTo(1); // byte-order mark
            }
            while (pos < n) {
                char c = src.charAt(pos);
                char next = pos + 1 < n ? src.charAt(pos + 1) : '\0';

                if (Character.isWhitespace(c) || c == '\u001A') {
                    advanceTo(pos + 1);
                } else if (c == '/' && next == '/') {
                    skipLineComment();
                } else if (c == '/' && next == '*') {
                    skipBlockComment();
                } else if (c == '"') {
                    if (src.startsWith("\"\"\"", pos)) {
                        scanTextBlock();
                    } else {
                        scanQuoted('"', TokenType.STRING, "string literal");
                    }
                } else if (c == '\'') {
                    scanQuoted('\'', TokenType.CHAR, "character literal");
                } else if (isDigit(c) || (c == '.' && isDigit(next))) {
                    scanNumber();
                } else if (Character.isJavaIdentifierStart(src.codePointAt(pos))) {
                    scanIdentifier();
                } else if (!scanOperatorOrSeparator()) {
                    int cp = src.codePointAt(pos);
                    int start = pos;
                    int startLine = line;
                    int startCol = col;
                    advanceTo(pos + Character.charCount(cp));
                    warn("Unrecognised character U+%04X at line %d, column %d".formatted(cp, startLine, startCol));
                    emit(TokenType.UNKNOWN, start, startLine, startCol);
                }
            }
            if (suppressedWarnings > 0) {
                warnings.add(suppressedWarnings + " further tokenizer warning(s) suppressed");
            }
            int lineCount = n == 0 ? 0 : line - (endsWithNewline() ? 1 : 0);
            return new TokenizationResult(tokens, Math.max(lineCount, n == 0 ? 0 : 1), warnings);
        }

        private boolean endsWithNewline() {
            return n > 0 && (src.charAt(n - 1) == '\n' || src.charAt(n - 1) == '\r');
        }

        // ---------------------------------------------------------------- comments

        private void skipLineComment() {
            int i = pos + 2;
            while (i < n && src.charAt(i) != '\n' && src.charAt(i) != '\r') {
                i++;
            }
            advanceTo(i);
        }

        private void skipBlockComment() {
            int startLine = line;
            int close = src.indexOf("*/", pos + 2);
            if (close < 0) {
                warn("Unterminated block comment starting at line " + startLine);
                advanceTo(n);
            } else {
                advanceTo(close + 2);
            }
        }

        // ---------------------------------------------------------------- literals

        private void scanQuoted(char quote, TokenType type, String description) {
            int start = pos;
            int startLine = line;
            int startCol = col;
            int i = pos + 1;
            boolean closed = false;
            while (i < n) {
                char ch = src.charAt(i);
                if (ch == '\\') {
                    i = Math.min(i + 2, n);
                    continue;
                }
                if (ch == quote) {
                    i++;
                    closed = true;
                    break;
                }
                if (ch == '\n' || ch == '\r') {
                    break; // literals cannot span lines; recover at end of line
                }
                i++;
            }
            if (!closed) {
                warn("Unterminated " + description + " at line " + startLine);
            }
            advanceTo(i);
            emit(type, start, startLine, startCol);
        }

        private void scanTextBlock() {
            int start = pos;
            int startLine = line;
            int startCol = col;
            int i = pos + 3;
            boolean closed = false;
            while (i < n) {
                char ch = src.charAt(i);
                if (ch == '\\') {
                    i = Math.min(i + 2, n);
                } else if (src.startsWith("\"\"\"", i)) {
                    i += 3;
                    closed = true;
                    break;
                } else {
                    i++;
                }
            }
            if (!closed) {
                warn("Unterminated text block starting at line " + startLine);
            }
            advanceTo(i);
            emit(TokenType.TEXT_BLOCK, start, startLine, startCol);
        }

        private void scanNumber() {
            int start = pos;
            int startLine = line;
            int startCol = col;
            int i = pos;
            char c = src.charAt(i);
            char next = i + 1 < n ? src.charAt(i + 1) : '\0';
            if (c == '0' && (next == 'x' || next == 'X')) {
                i += 2;
                while (i < n && (isHexDigit(src.charAt(i)) || src.charAt(i) == '_' || src.charAt(i) == '.')) {
                    i++;
                }
                if (i < n && (src.charAt(i) == 'p' || src.charAt(i) == 'P')) {
                    i = scanExponent(i);
                }
            } else if (c == '0' && (next == 'b' || next == 'B')) {
                i += 2;
                while (i < n && (src.charAt(i) == '0' || src.charAt(i) == '1' || src.charAt(i) == '_')) {
                    i++;
                }
            } else {
                i = scanDigits(i);
                if (i < n && src.charAt(i) == '.') {
                    char after = i + 1 < n ? src.charAt(i + 1) : '\0';
                    boolean fraction = isDigit(after) || "eEfFdD".indexOf(after) >= 0
                            || (!Character.isJavaIdentifierStart(after) && after != '.');
                    if (fraction) {
                        i = scanDigits(i + 1);
                    }
                }
                if (i < n && (src.charAt(i) == 'e' || src.charAt(i) == 'E')) {
                    i = scanExponent(i);
                }
            }
            if (i < n && "lLfFdD".indexOf(src.charAt(i)) >= 0) {
                i++;
            }
            advanceTo(i);
            emit(TokenType.NUMBER, start, startLine, startCol);
        }

        private int scanDigits(int i) {
            while (i < n && (isDigit(src.charAt(i)) || src.charAt(i) == '_')) {
                i++;
            }
            return i;
        }

        private int scanExponent(int i) {
            int j = i + 1;
            if (j < n && (src.charAt(j) == '+' || src.charAt(j) == '-')) {
                j++;
            }
            if (j < n && isDigit(src.charAt(j))) {
                return scanDigits(j);
            }
            return i; // not an exponent after all
        }

        // ---------------------------------------------------------------- words and symbols

        private void scanIdentifier() {
            int start = pos;
            int startLine = line;
            int startCol = col;
            int i = pos + Character.charCount(src.codePointAt(pos));
            while (i < n) {
                int cp = src.codePointAt(i);
                if (!Character.isJavaIdentifierPart(cp)) {
                    break;
                }
                i += Character.charCount(cp);
            }
            advanceTo(i);
            String word = src.substring(start, i);
            emit(JavaKeywords.isKeyword(word) ? TokenType.KEYWORD : TokenType.IDENTIFIER, start, startLine, startCol);
        }

        private boolean scanOperatorOrSeparator() {
            int start = pos;
            int startLine = line;
            int startCol = col;
            for (String op : OPERATORS) {
                if (src.startsWith(op, pos)) {
                    // "..." is an operator but "." alone is a separator; handled by ordering.
                    advanceTo(pos + op.length());
                    emit(TokenType.OPERATOR, start, startLine, startCol);
                    return true;
                }
            }
            if (SEPARATORS.indexOf(src.charAt(pos)) >= 0) {
                advanceTo(pos + 1);
                emit(TokenType.SEPARATOR, start, startLine, startCol);
                return true;
            }
            return false;
        }

        // ---------------------------------------------------------------- helpers

        private void emit(TokenType type, int start, int startLine, int startCol) {
            int endLine = line;
            // if the token ended exactly on a newline boundary, its last character is on the previous line
            if (pos > start && pos <= n) {
                char last = src.charAt(pos - 1);
                if (last == '\n' || last == '\r') {
                    endLine = Math.max(startLine, line - 1);
                }
            }
            tokens.add(new Token(type, src.substring(start, pos), start, pos, startLine, startCol, endLine));
        }

        /** Moves the cursor to {@code target}, maintaining line and column counters. */
        private void advanceTo(int target) {
            target = Math.min(target, n);
            while (pos < target) {
                char ch = src.charAt(pos);
                if (ch == '\n') {
                    line++;
                    col = 1;
                } else if (ch == '\r') {
                    if (pos + 1 < n && src.charAt(pos + 1) == '\n') {
                        col++; // the following \n increments the line
                    } else {
                        line++;
                        col = 1;
                    }
                } else {
                    col++;
                }
                pos++;
            }
        }

        private void warn(String message) {
            if (warnings.size() < MAX_WARNINGS) {
                warnings.add(message);
            } else {
                suppressedWarnings++;
            }
        }

        private static boolean isDigit(char c) {
            return c >= '0' && c <= '9';
        }

        private static boolean isHexDigit(char c) {
            return isDigit(c) || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
        }
    }
}
