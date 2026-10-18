package com.codetrace.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

/** Metadata of one uploaded file. The source text itself (if retained) lives in {@link SourceFileContent}. */
@Entity
@Table(name = "source_file")
public class SourceFile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_id", nullable = false)
    private Analysis analysis;

    @Column(name = "ordinal", nullable = false)
    private int ordinal;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "display_name", nullable = false, length = 300)
    private String displayName;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "token_count", nullable = false)
    private int tokenCount;

    @Column(name = "line_count", nullable = false)
    private int lineCount;

    /** JSON array of tokenizer warnings. */
    @Column(name = "warnings", nullable = false, columnDefinition = "text")
    private String warnings;

    protected SourceFile() {
    }

    public SourceFile(Analysis analysis, int ordinal, String originalFilename, String displayName, long fileSize,
                      String contentHash, int tokenCount, int lineCount, String warnings) {
        this.analysis = analysis;
        this.ordinal = ordinal;
        this.originalFilename = originalFilename;
        this.displayName = displayName;
        this.fileSize = fileSize;
        this.contentHash = contentHash;
        this.tokenCount = tokenCount;
        this.lineCount = lineCount;
        this.warnings = warnings;
    }

    public UUID getId() {
        return id;
    }

    public Analysis getAnalysis() {
        return analysis;
    }

    public int getOrdinal() {
        return ordinal;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getDisplayName() {
        return displayName;
    }

    public long getFileSize() {
        return fileSize;
    }

    public String getContentHash() {
        return contentHash;
    }

    public int getTokenCount() {
        return tokenCount;
    }

    public int getLineCount() {
        return lineCount;
    }

    public String getWarnings() {
        return warnings;
    }
}
