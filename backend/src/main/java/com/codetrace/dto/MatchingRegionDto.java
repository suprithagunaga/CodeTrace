package com.codetrace.dto;

/** Offsets are zero-based character offsets (end exclusive); lines are one-based and inclusive. */
public record MatchingRegionDto(
        int fileAStartOffset,
        int fileAEndOffset,
        int fileBStartOffset,
        int fileBEndOffset,
        int fileAStartLine,
        int fileAEndLine,
        int fileBStartLine,
        int fileBEndLine,
        int tokenLength) {
}
