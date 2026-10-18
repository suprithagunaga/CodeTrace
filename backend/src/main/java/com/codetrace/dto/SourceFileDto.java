package com.codetrace.dto;

import java.util.List;
import java.util.UUID;

public record SourceFileDto(
        UUID id,
        int ordinal,
        String filename,
        long fileSize,
        String contentHash,
        int tokenCount,
        int lineCount,
        List<String> warnings) {
}
