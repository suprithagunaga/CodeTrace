package com.codetrace.dto;

import java.util.List;

/** Public, non-sensitive settings shown on the Settings &amp; Help page. */
public record ConfigInfoDto(
        String version,
        int maxFiles,
        long maxFileBytes,
        List<String> allowedExtensions,
        boolean sourceRetained,
        int retentionDays,
        AlgorithmConfigDto defaults) {
}
