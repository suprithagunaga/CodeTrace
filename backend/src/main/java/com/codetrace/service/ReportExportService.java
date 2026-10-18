package com.codetrace.service;

import com.codetrace.dto.AnalysisReportDto;
import com.codetrace.dto.ComparisonSummaryDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Builds downloadable CSV and JSON exports from the saved report. */
@Service
public class ReportExportService {

    public enum Format { CSV, JSON }

    public record Export(byte[] body, String contentType, String filename) {
    }

    private final AnalysisService analysisService;
    private final ObjectMapper exportMapper;

    public ReportExportService(AnalysisService analysisService, ObjectMapper objectMapper) {
        this.analysisService = analysisService;
        this.exportMapper = objectMapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
    }

    public Export export(UUID analysisId, Format format) {
        AnalysisReportDto report = analysisService.getReport(analysisId);
        String base = "codetrace-analysis-" + analysisId;
        return switch (format) {
            case CSV -> new Export(toCsv(report).getBytes(StandardCharsets.UTF_8), "text/csv;charset=UTF-8", base + ".csv");
            case JSON -> new Export(toJson(report), "application/json", base + ".json");
        };
    }

    public static Format parseFormat(String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "csv" -> Format.CSV;
            case "json" -> Format.JSON;
            default -> null;
        };
    }

    static String toCsv(AnalysisReportDto report) {
        CsvWriter csv = new CsvWriter();
        csv.row(List.of("analysis_id", "analysis_title", "created_at", "kgram_size", "window_size", "jaccard_ngram",
                "normalization", "file_a", "file_b", "overall_score_pct", "jaccard_score_pct", "winnowing_score_pct",
                "shared_fingerprints", "matching_regions", "warnings"));
        var a = report.analysis();
        var c = report.configuration();
        for (ComparisonSummaryDto row : report.comparisons()) {
            csv.row(List.of(
                    a.id().toString(),
                    CsvWriter.neutralize(a.title()),
                    a.createdAt().toString(),
                    Integer.toString(c.kgramSize()),
                    Integer.toString(c.windowSize()),
                    Integer.toString(c.jaccardNgram()),
                    c.normalizationMode(),
                    CsvWriter.neutralize(row.fileAName()),
                    CsvWriter.neutralize(row.fileBName()),
                    pct(row.overallScore()),
                    pct(row.jaccardScore()),
                    pct(row.winnowingScore()),
                    Integer.toString(row.sharedFingerprintCount()),
                    Integer.toString(row.regionCount()),
                    CsvWriter.neutralize(String.join(" | ", row.warnings()))));
        }
        return csv.toString();
    }

    private byte[] toJson(AnalysisReportDto report) {
        try {
            return exportMapper.writeValueAsBytes(report);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize report", e);
        }
    }

    private static String pct(double fraction) {
        return String.format(Locale.ROOT, "%.2f", fraction * 100);
    }
}
