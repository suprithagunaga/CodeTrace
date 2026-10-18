package com.codetrace.controller;

import com.codetrace.dto.AnalysisOptionsRequest;
import com.codetrace.dto.AnalysisReportDto;
import com.codetrace.dto.AnalysisSummaryDto;
import com.codetrace.dto.ComparisonDetailDto;
import com.codetrace.dto.DashboardDto;
import com.codetrace.dto.PageResponse;
import com.codetrace.exception.ApiValidationException;
import com.codetrace.service.AnalysisService;
import com.codetrace.service.ReportExportService;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class AnalysisController {

    private final AnalysisService analysisService;
    private final ReportExportService exportService;

    public AnalysisController(AnalysisService analysisService, ReportExportService exportService) {
        this.analysisService = analysisService;
        this.exportService = exportService;
    }

    /** Upload two or more .java files (multipart field "files"), run all algorithms and persist the results. */
    @PostMapping(path = "/analyses", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AnalysisReportDto> create(
            @RequestParam(name = "files", required = false) List<MultipartFile> files,
            @Valid @ModelAttribute AnalysisOptionsRequest options) {
        AnalysisReportDto report = analysisService.createAnalysis(files, options);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(report.analysis().id()).toUri();
        return ResponseEntity.created(location).body(report);
    }

    @GetMapping("/analyses")
    public PageResponse<AnalysisSummaryDto> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String direction,
            @RequestParam(name = "q", required = false) String query) {
        return analysisService.listAnalyses(page, size, sort, direction, query);
    }

    @GetMapping("/analyses/{id}")
    public AnalysisReportDto get(@PathVariable UUID id) {
        return analysisService.getReport(id);
    }

    @GetMapping("/analyses/{id}/comparisons/{comparisonId}")
    public ComparisonDetailDto comparison(@PathVariable UUID id, @PathVariable UUID comparisonId) {
        return analysisService.getComparison(id, comparisonId);
    }

    @DeleteMapping("/analyses/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        analysisService.deleteAnalysis(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/analyses/{id}/export")
    public ResponseEntity<byte[]> export(@PathVariable UUID id, @RequestParam(defaultValue = "json") String format) {
        ReportExportService.Format parsed = ReportExportService.parseFormat(format);
        if (parsed == null) {
            throw new ApiValidationException("format must be 'csv' or 'json'");
        }
        ReportExportService.Export export = exportService.export(id, parsed);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(export.filename()).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(export.contentType()))
                .body(export.body());
    }

    @GetMapping("/dashboard")
    public DashboardDto dashboard() {
        return analysisService.dashboard();
    }
}
