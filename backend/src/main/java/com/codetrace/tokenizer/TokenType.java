package com.codetrace.tokenizer;

/**
 * Lexical categories produced by {@link JavaTokenizer}.
 * Whitespace and comments are never emitted as tokens.
 */
public enum TokenType {
    KEYWORD,
    IDENTIFIER,
    NUMBER,
    STRING,
    TEXT_BLOCK,
    CHAR,
    OPERATOR,
    SEPARATOR,
    /** A character the tokenizer could not classify (malformed input). */
    UNKNOWN
}
