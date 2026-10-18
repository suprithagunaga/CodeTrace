package com.codetrace.validation;

/**
 * A source file that passed validation.
 *
 * @param originalFilename sanitized client filename
 * @param displayName      unique (within the analysis) name shown in the UI
 * @param sizeBytes        size of the uploaded bytes
 * @param sha256           hex SHA-256 of the uploaded bytes
 * @param content          decoded UTF-8 text (kept in memory only)
 */
public record ValidatedSource(String originalFilename, String displayName, long sizeBytes, String sha256, String content) {
}
