package com.codetrace.dto;

import java.util.List;

/**
 * Stored as JSON in {@code comparison_result.result_metadata}.
 * Containment values are directional (A in B, B in A); every other score is symmetric.
 */
public record ResultMetadataDto(
        int fingerprintsA,
        int fingerprintsB,
        double containmentA,
        double containmentB,
        double coverageA,
        double coverageB,
        boolean winnowingApplicable,
        List<String> warnings) {
}
