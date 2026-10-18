package com.codetrace.dto;

import java.util.List;

public record DashboardDto(
        long totalAnalyses,
        long totalFiles,
        long totalComparisons,
        List<ScoreBucketDto> scoreDistribution,
        List<TopPairDto> topPairs,
        List<AnalysisSummaryDto> recentAnalyses) {
}
