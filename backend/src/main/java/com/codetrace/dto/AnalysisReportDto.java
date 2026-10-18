package com.codetrace.dto;

import java.util.List;

public record AnalysisReportDto(
        AnalysisSummaryDto analysis,
        AlgorithmConfigDto configuration,
        boolean sourceRetained,
        List<SourceFileDto> files,
        List<ComparisonSummaryDto> comparisons,
        String disclaimer) {

    public static final String DISCLAIMER = "Similarity scores indicate potential overlap between source files. "
            + "They do not by themselves prove plagiarism or misconduct: shared boilerplate, common algorithms, "
            + "starter code and permitted collaboration also produce matches. Review the matched code in context "
            + "before drawing any conclusion.";
}
