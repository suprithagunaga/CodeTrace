package com.codetrace.dto;

import java.time.Instant;
import java.util.UUID;

/** One row of the analysis history. {@code maxScore} is the highest overall pair score (0..1) or null. */
public record AnalysisSummaryDto(
        UUID id,
        String title,
        Instant createdAt,
        Instant completedAt,
        String status,
        int fileCount,
        int comparisonCount,
        Double maxScore) {
}
