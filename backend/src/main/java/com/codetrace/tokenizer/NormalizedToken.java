package com.codetrace.tokenizer;

/**
 * A token after normalization. {@code value} is what the similarity algorithms compare;
 * {@code source} keeps the original position for highlighting.
 */
public record NormalizedToken(String value, Token source) {
}
