package com.codetrace.dto;

/**
 * Algorithm parameters used for an analysis, stored as JSON in {@code analysis.algorithm_configuration}.
 */
public record AlgorithmConfigDto(
        int kgramSize,
        int windowSize,
        int jaccardNgram,
        String normalizationMode,
        boolean normalizeNumbers,
        String combinedScoreRule) {

    public static final String COMBINED_RULE =
            "overall = (jaccard + winnowing) / 2; if Winnowing is not applicable (a file has fewer than k tokens) overall = jaccard";
}
