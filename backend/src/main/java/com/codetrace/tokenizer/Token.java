package com.codetrace.tokenizer;

/**
 * A single lexical token with its exact position in the original source.
 *
 * @param type        lexical category
 * @param text        exact source text of the token
 * @param startOffset zero-based character offset of the first character (inclusive)
 * @param endOffset   zero-based character offset after the last character (exclusive)
 * @param line        one-based line where the token starts
 * @param column      one-based column where the token starts
 * @param endLine     one-based line where the token ends (differs from {@code line} for text blocks)
 */
public record Token(TokenType type, String text, int startOffset, int endOffset, int line, int column, int endLine) {

    public boolean is(TokenType t, String value) {
        return type == t && text.equals(value);
    }

    public boolean isKeyword(String value) {
        return is(TokenType.KEYWORD, value);
    }

    public boolean isSymbol(String value) {
        return (type == TokenType.OPERATOR || type == TokenType.SEPARATOR) && text.equals(value);
    }
}
