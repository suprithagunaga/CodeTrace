package com.codetrace.dto;

import java.time.Instant;
import java.util.UUID;

public record TopPairDto(
        UUID analysisId,
        String analysisTitle,
        UUID comparisonId,
        String fileAName,
        String fileBName,
        double overallScore,
        Instant createdAt) {
}
