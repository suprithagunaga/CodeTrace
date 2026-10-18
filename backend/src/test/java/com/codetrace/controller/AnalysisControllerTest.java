package com.codetrace.controller;

import com.codetrace.dto.AlgorithmConfigDto;
import com.codetrace.dto.AnalysisReportDto;
import com.codetrace.dto.AnalysisSummaryDto;
import com.codetrace.dto.ComparisonSummaryDto;
import com.codetrace.dto.PageResponse;
import com.codetrace.exception.ApiValidationException;
import com.codetrace.exception.PayloadTooLargeException;
import com.codetrace.exception.ResourceNotFoundException;
import com.codetrace.service.AnalysisService;
import com.codetrace.service.ReportExportService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Web-layer tests: routing, validation, status codes and the uniform error body. Services are mocked. */
@WebMvcTest(controllers = {AnalysisController.class, SystemController.class})
class AnalysisControllerTest {

    private static final UUID ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private MockMvc mvc;

    @MockBean
    private AnalysisService analysisService;

    @MockBean
    private ReportExportService exportService;

    @MockBean
    private DataSource dataSource;

    private static MockMultipartFile javaFile(String name, String code) {
        return new MockMultipartFile("files", name, "text/x-java", code.getBytes(StandardCharsets.UTF_8));
    }

    private static AnalysisReportDto report() {
        AnalysisSummaryDto summary = new AnalysisSummaryDto(ID, "A.java, B.java", Instant.now(), Instant.now(),
                "COMPLETED", 2, 1, 0.42);
        ComparisonSummaryDto pair = new ComparisonSummaryDto(UUID.randomUUID(), UUID.randomUUID(), "A.java",
                UUID.randomUUID(), "B.java", 0.4, 0.44, 0.42, 7, 1, List.of());
        return new AnalysisReportDto(summary, new AlgorithmConfigDto(5, 4, 3, "IDENTIFIERS", false,
                AlgorithmConfigDto.COMBINED_RULE), true, List.of(), List.of(pair), AnalysisReportDto.DISCLAIMER);
    }

    @Test
    @DisplayName("POST /api/analyses returns 201 with Location and the report")
    void createAnalysis() throws Exception {
        when(analysisService.createAnalysis(anyList(), any())).thenReturn(report());
        mvc.perform(multipart("/api/analyses")
                        .file(javaFile("A.java", "class A {}"))
                        .file(javaFile("B.java", "class B {}"))
                        .param("title", "Lab 1"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/analyses/" + ID)))
                .andExpect(jsonPath("$.analysis.id").value(ID.toString()))
                .andExpect(jsonPath("$.comparisons[0].overallScore").value(0.42))
                .andExpect(jsonPath("$.configuration.kgramSize").value(5))
                .andExpect(jsonPath("$.disclaimer").exists());
    }

    @Test
    @DisplayName("out-of-range algorithm options are rejected with field details")
    void invalidOptions() throws Exception {
        mvc.perform(multipart("/api/analyses")
                        .file(javaFile("A.java", "class A {}"))
                        .file(javaFile("B.java", "class B {}"))
                        .param("kgramSize", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.details[0].field").value("kgramSize"));
        verifyNoInteractions(analysisService);
    }

    @Test
    @DisplayName("non-numeric option is a 400, not a 500")
    void nonNumericOption() throws Exception {
        mvc.perform(multipart("/api/analyses")
                        .file(javaFile("A.java", "class A {}"))
                        .file(javaFile("B.java", "class B {}"))
                        .param("windowSize", "abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("service validation errors become 400 with the message and per-file details")
    void serviceValidation() throws Exception {
        when(analysisService.createAnalysis(any(), any())).thenThrow(new ApiValidationException(
                "1 file(s) failed validation", List.of(new com.codetrace.dto.ApiError.FieldError("notes.txt", "Unsupported file type"))));
        mvc.perform(multipart("/api/analyses").file(javaFile("notes.txt", "x")).file(javaFile("A.java", "class A {}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("1 file(s) failed validation"))
                .andExpect(jsonPath("$.details[0].field").value("notes.txt"))
                .andExpect(jsonPath("$.path").value("/api/analyses"));
    }

    @Test
    @DisplayName("missing files part is a 400")
    void missingFiles() throws Exception {
        when(analysisService.createAnalysis(any(), any())).thenThrow(
                new ApiValidationException("At least 2 source files are required for a pairwise comparison"));
        mvc.perform(multipart("/api/analyses"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("At least 2")));
    }

    @Test
    @DisplayName("JSON body instead of multipart is 415")
    void wrongContentType() throws Exception {
        mvc.perform(post("/api/analyses").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    @DisplayName("oversized upload is 413")
    void oversized() throws Exception {
        when(analysisService.createAnalysis(any(), any())).thenThrow(new PayloadTooLargeException("File 'A.java' is too large"));
        mvc.perform(multipart("/api/analyses").file(javaFile("A.java", "class A {}")).file(javaFile("B.java", "class B {}")))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.message").value("File 'A.java' is too large"));
    }

    @Test
    @DisplayName("history endpoint passes paging, sorting and search parameters")
    void list() throws Exception {
        when(analysisService.listAnalyses(1, 5, "maxScore", "asc", "lab"))
                .thenReturn(new PageResponse<>(List.of(report().analysis()), 1, 5, 6, 2));
        mvc.perform(get("/api/analyses").param("page", "1").param("size", "5")
                        .param("sort", "maxScore").param("direction", "asc").param("q", "lab"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(6))
                .andExpect(jsonPath("$.content[0].title").value("A.java, B.java"));
    }

    @Test
    @DisplayName("invalid UUID path variable is 400")
    void invalidUuid() throws Exception {
        mvc.perform(get("/api/analyses/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("id")));
    }

    @Test
    @DisplayName("unknown analysis is 404")
    void notFound() throws Exception {
        when(analysisService.getReport(ID)).thenThrow(new ResourceNotFoundException("Analysis " + ID + " not found"));
        mvc.perform(get("/api/analyses/" + ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    @DisplayName("database failure is 503 without leaking internals")
    void databaseFailure() throws Exception {
        when(analysisService.listAnalyses(anyInt(), anyInt(), any(), any(), any()))
                .thenThrow(new DataAccessResourceFailureException("Connection to localhost:5432 refused; password=secret"));
        mvc.perform(get("/api/analyses"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string(not(containsString("5432"))))
                .andExpect(content().string(not(containsString("secret"))));
    }

    @Test
    @DisplayName("DELETE returns 204, and 404 for unknown ids")
    void deleteAnalysis() throws Exception {
        mvc.perform(delete("/api/analyses/" + ID)).andExpect(status().isNoContent());
        verify(analysisService).deleteAnalysis(ID);

        UUID other = UUID.randomUUID();
        doThrow(new ResourceNotFoundException("Analysis not found")).when(analysisService).deleteAnalysis(other);
        mvc.perform(delete("/api/analyses/" + other)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("CSV export is an attachment with the right content type")
    void exportCsv() throws Exception {
        when(exportService.export(eq(ID), eq(ReportExportService.Format.CSV))).thenReturn(new ReportExportService.Export(
                "analysis_id\r\n".getBytes(StandardCharsets.UTF_8), "text/csv;charset=UTF-8", "codetrace-analysis-" + ID + ".csv"));
        mvc.perform(get("/api/analyses/" + ID + "/export").param("format", "csv"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Content-Disposition", containsString(".csv")))
                .andExpect(content().contentTypeCompatibleWith("text/csv"));
    }

    @Test
    @DisplayName("unknown export format is 400")
    void exportBadFormat() throws Exception {
        mvc.perform(get("/api/analyses/" + ID + "/export").param("format", "xlsx"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("health reports database UP with 200")
    void healthUp() throws Exception {
        Connection connection = mock(Connection.class);
        when(connection.isValid(anyInt())).thenReturn(true);
        when(dataSource.getConnection()).thenReturn(connection);
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.database.status").value("UP"));
    }

    @Test
    @DisplayName("health reports database DOWN with 503")
    void healthDown() throws Exception {
        when(dataSource.getConnection()).thenThrow(new SQLException("refused"));
        mvc.perform(get("/api/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.database.status").value("DOWN"))
                .andExpect(content().string(not(containsString("refused"))));
    }

    @Test
    @DisplayName("public config exposes limits and defaults")
    void config() throws Exception {
        mvc.perform(get("/api/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxFiles").value(50))
                .andExpect(jsonPath("$.allowedExtensions[0]").value(".java"))
                .andExpect(jsonPath("$.defaults.windowSize").value(4));
    }
}
