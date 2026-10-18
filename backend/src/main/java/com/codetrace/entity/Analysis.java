package com.codetrace.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One analysis run. Child rows (files, comparisons, regions) reference it and are removed by
 * {@code ON DELETE CASCADE} foreign keys, so the entity deliberately has no collection mappings.
 */
@Entity
@Table(name = "analysis")
public class Analysis {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "title", nullable = false, length = 300)
    private String title;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AnalysisStatus status;

    @Column(name = "file_count", nullable = false)
    private int fileCount;

    @Column(name = "comparison_count", nullable = false)
    private int comparisonCount;

    @Column(name = "max_score")
    private Double maxScore;

    @Column(name = "algorithm_configuration", nullable = false, columnDefinition = "text")
    private String algorithmConfiguration;

    @Column(name = "source_retained", nullable = false)
    private boolean sourceRetained;

    protected Analysis() {
    }

    public Analysis(String title, Instant createdAt, AnalysisStatus status, String algorithmConfiguration) {
        this.title = title;
        this.createdAt = createdAt;
        this.status = status;
        this.algorithmConfiguration = algorithmConfiguration;
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public AnalysisStatus getStatus() {
        return status;
    }

    public void setStatus(AnalysisStatus status) {
        this.status = status;
    }

    public int getFileCount() {
        return fileCount;
    }

    public void setFileCount(int fileCount) {
        this.fileCount = fileCount;
    }

    public int getComparisonCount() {
        return comparisonCount;
    }

    public void setComparisonCount(int comparisonCount) {
        this.comparisonCount = comparisonCount;
    }

    public Double getMaxScore() {
        return maxScore;
    }

    public void setMaxScore(Double maxScore) {
        this.maxScore = maxScore;
    }

    public String getAlgorithmConfiguration() {
        return algorithmConfiguration;
    }

    public boolean isSourceRetained() {
        return sourceRetained;
    }

    public void setSourceRetained(boolean sourceRetained) {
        this.sourceRetained = sourceRetained;
    }
}
