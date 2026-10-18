package com.codetrace.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Optional multipart form fields accompanying {@code POST /api/analyses}. Null fields use the server defaults.
 */
public record AnalysisOptionsRequest(
        @Size(max = 200, message = "title must be at most 200 characters") String title,
        @Min(value = 2, message = "kgramSize must be at least 2") @Max(value = 50, message = "kgramSize must be at most 50") Integer kgramSize,
        @Min(value = 1, message = "windowSize must be at least 1") @Max(value = 100, message = "windowSize must be at most 100") Integer windowSize,
        @Min(value = 1, message = "jaccardNgram must be at least 1") @Max(value = 10, message = "jaccardNgram must be at most 10") Integer jaccardNgram,
        Boolean normalizeIdentifiers,
        Boolean normalizeNumbers) {

    public static AnalysisOptionsRequest empty() {
        return new AnalysisOptionsRequest(null, null, null, null, null, null);
    }
}
