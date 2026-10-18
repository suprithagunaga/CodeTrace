package com.codetrace.similarity;

/**
 * A selected Winnowing fingerprint.
 *
 * @param hash       64-bit hash of a k-gram of normalized tokens
 * @param tokenIndex index of the first token of that k-gram in the normalized token list
 */
public record Fingerprint(long hash, int tokenIndex) {
}
