package com.codetrace.dto;

import java.util.List;
import java.util.UUID;

/** Scores are fractions in [0, 1]; the UI renders them as percentages. */
public record ComparisonSummaryDto(
        UUID id,
        UUID fileAId,
        String fileAName,
        UUID fileBId,
        String fileBName,
        double jaccardScore,
        double winnowingScore,
        double overallScore,
        int sharedFingerprintCount,
        int regionCount,
        List<String> warnings) {
}
