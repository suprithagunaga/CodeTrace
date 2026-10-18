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

/** A matching code region. Offsets are character offsets (end exclusive); lines are 1-based inclusive. */
@Entity
@Table(name = "matching_region")
public class MatchingRegion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "comparison_result_id", nullable = false)
    private ComparisonResult comparison;

    @Column(name = "file_a_start_offset", nullable = false)
    private int fileAStartOffset;

    @Column(name = "file_a_end_offset", nullable = false)
    private int fileAEndOffset;

    @Column(name = "file_b_start_offset", nullable = false)
    private int fileBStartOffset;

    @Column(name = "file_b_end_offset", nullable = false)
    private int fileBEndOffset;

    @Column(name = "file_a_start_line", nullable = false)
    private int fileAStartLine;

    @Column(name = "file_a_end_line", nullable = false)
    private int fileAEndLine;

    @Column(name = "file_b_start_line", nullable = false)
    private int fileBStartLine;

    @Column(name = "file_b_end_line", nullable = false)
    private int fileBEndLine;

    @Column(name = "token_length", nullable = false)
    private int tokenLength;

    protected MatchingRegion() {
    }

    public MatchingRegion(ComparisonResult comparison, int fileAStartOffset, int fileAEndOffset,
                          int fileBStartOffset, int fileBEndOffset, int fileAStartLine, int fileAEndLine,
                          int fileBStartLine, int fileBEndLine, int tokenLength) {
        this.comparison = comparison;
        this.fileAStartOffset = fileAStartOffset;
        this.fileAEndOffset = fileAEndOffset;
        this.fileBStartOffset = fileBStartOffset;
        this.fileBEndOffset = fileBEndOffset;
        this.fileAStartLine = fileAStartLine;
        this.fileAEndLine = fileAEndLine;
        this.fileBStartLine = fileBStartLine;
        this.fileBEndLine = fileBEndLine;
        this.tokenLength = tokenLength;
    }

    public UUID getId() {
        return id;
    }

    public ComparisonResult getComparison() {
        return comparison;
    }

    public int getFileAStartOffset() {
        return fileAStartOffset;
    }

    public int getFileAEndOffset() {
        return fileAEndOffset;
    }

    public int getFileBStartOffset() {
        return fileBStartOffset;
    }

    public int getFileBEndOffset() {
        return fileBEndOffset;
    }

    public int getFileAStartLine() {
        return fileAStartLine;
    }

    public int getFileAEndLine() {
        return fileAEndLine;
    }

    public int getFileBStartLine() {
        return fileBStartLine;
    }

    public int getFileBEndLine() {
        return fileBEndLine;
    }

    public int getTokenLength() {
        return tokenLength;
    }
}
