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

/** Similarity result for one unordered file pair (file A always has the lower ordinal). */
@Entity
@Table(name = "comparison_result")
public class ComparisonResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_id", nullable = false)
    private Analysis analysis;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "file_a_id", nullable = false)
    private SourceFile fileA;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "file_b_id", nullable = false)
    private SourceFile fileB;

    @Column(name = "jaccard_score", nullable = false)
    private double jaccardScore;

    @Column(name = "winnowing_score", nullable = false)
    private double winnowingScore;

    @Column(name = "overall_score", nullable = false)
    private double overallScore;

    @Column(name = "shared_fingerprint_count", nullable = false)
    private int sharedFingerprintCount;

    @Column(name = "region_count", nullable = false)
    private int regionCount;

    /** JSON document with directional containment, coverage, fingerprint totals and warnings. */
    @Column(name = "result_metadata", nullable = false, columnDefinition = "text")
    private String resultMetadata;

    protected ComparisonResult() {
    }

    public ComparisonResult(Analysis analysis, SourceFile fileA, SourceFile fileB, double jaccardScore,
                            double winnowingScore, double overallScore, int sharedFingerprintCount,
                            int regionCount, String resultMetadata) {
        this.analysis = analysis;
        this.fileA = fileA;
        this.fileB = fileB;
        this.jaccardScore = jaccardScore;
        this.winnowingScore = winnowingScore;
        this.overallScore = overallScore;
        this.sharedFingerprintCount = sharedFingerprintCount;
        this.regionCount = regionCount;
        this.resultMetadata = resultMetadata;
    }

    public UUID getId() {
        return id;
    }

    public Analysis getAnalysis() {
        return analysis;
    }

    public SourceFile getFileA() {
        return fileA;
    }

    public SourceFile getFileB() {
        return fileB;
    }

    public double getJaccardScore() {
        return jaccardScore;
    }

    public double getWinnowingScore() {
        return winnowingScore;
    }

    public double getOverallScore() {
        return overallScore;
    }

    public int getSharedFingerprintCount() {
        return sharedFingerprintCount;
    }

    public int getRegionCount() {
        return regionCount;
    }

    public String getResultMetadata() {
        return resultMetadata;
    }
}
