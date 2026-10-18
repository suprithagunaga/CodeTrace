package com.codetrace.similarity;

import com.codetrace.tokenizer.NormalizationMode;
import com.codetrace.tokenizer.NormalizationOptions;

/**
 * Algorithm parameters for one analysis run.
 *
 * @param kGramSize     Winnowing k (tokens per k-gram), 2..50
 * @param windowSize    Winnowing w (k-grams per window), 1..100
 * @param jaccardNgram  shingle size used for Jaccard, 1..10 (1 = plain token set)
 * @param normalization identifier/number normalization
 */
public record SimilarityConfig(int kGramSize, int windowSize, int jaccardNgram, NormalizationOptions normalization) {

    public static final int DEFAULT_K = 5;
    public static final int DEFAULT_W = 4;
    public static final int DEFAULT_JACCARD_N = 3;

    public SimilarityConfig {
        if (kGramSize < 2 || kGramSize > 50) {
            throw new IllegalArgumentException("kGramSize must be between 2 and 50");
        }
        if (windowSize < 1 || windowSize > 100) {
            throw new IllegalArgumentException("windowSize must be between 1 and 100");
        }
        if (jaccardNgram < 1 || jaccardNgram > 10) {
            throw new IllegalArgumentException("jaccardNgram must be between 1 and 10");
        }
        if (normalization == null) {
            normalization = NormalizationOptions.identifiers();
        }
    }

    public static SimilarityConfig defaults() {
        return new SimilarityConfig(DEFAULT_K, DEFAULT_W, DEFAULT_JACCARD_N,
                new NormalizationOptions(NormalizationMode.IDENTIFIERS, false));
    }

    /** Minimum length (tokens) of a shared run that Winnowing is guaranteed to detect. */
    public int guaranteeThreshold() {
        return windowSize + kGramSize - 1;
    }
}
