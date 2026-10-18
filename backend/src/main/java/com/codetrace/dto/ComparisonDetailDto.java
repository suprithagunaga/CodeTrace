package com.codetrace.dto;

import java.util.List;
import java.util.UUID;

/**
 * Full detail of one file pair. Source text and the normalized-token view are present only when the
 * analysis retained source code ({@code sourceAvailable}).
 */
public record ComparisonDetailDto(
        UUID id,
        UUID analysisId,
        SourceFileDto fileA,
        SourceFileDto fileB,
        double jaccardScore,
        double winnowingScore,
        double overallScore,
        int sharedFingerprintCount,
        ResultMetadataDto metadata,
        List<MatchingRegionDto> regions,
        AlgorithmConfigDto configuration,
        String explanation,
        boolean sourceAvailable,
        String fileASource,
        String fileBSource,
        List<String> fileANormalizedLines,
        List<String> fileBNormalizedLines,
        String disclaimer) {
}
