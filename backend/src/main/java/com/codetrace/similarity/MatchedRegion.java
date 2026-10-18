package com.codetrace.similarity;

/**
 * A matching region mapped back to the original source text of both files.
 * Offsets are zero-based character offsets (end exclusive); lines are one-based and inclusive.
 */
public record MatchedRegion(
        int fileAStartOffset, int fileAEndOffset,
        int fileBStartOffset, int fileBEndOffset,
        int fileAStartLine, int fileAEndLine,
        int fileBStartLine, int fileBEndLine,
        int tokenLength) {
}
