package com.codetrace.service;

import com.codetrace.config.CodeTraceProperties;
import com.codetrace.dto.AnalysisOptionsRequest;
import com.codetrace.dto.AnalysisReportDto;
import com.codetrace.entity.Analysis;
import com.codetrace.entity.ComparisonResult;
import com.codetrace.entity.MatchingRegion;
import com.codetrace.entity.SourceFile;
import com.codetrace.entity.SourceFileContent;
import com.codetrace.exception.ApiValidationException;
import com.codetrace.exception.ResourceNotFoundException;
import com.codetrace.repository.AnalysisRepository;
import com.codetrace.repository.ComparisonResultRepository;
import com.codetrace.repository.MatchingRegionRepository;
import com.codetrace.repository.SourceFileContentRepository;
import com.codetrace.repository.SourceFileRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Service tests with mocked repositories (no database). */
class AnalysisServiceTest {

    private AnalysisRepository analysisRepository;
    private SourceFileRepository sourceFileRepository;
    private SourceFileContentRepository contentRepository;
    private ComparisonResultRepository comparisonRepository;
    private MatchingRegionRepository regionRepository;
    private AnalysisService service;

    private final AtomicReference<Analysis> savedAnalysis = new AtomicReference<>();
    private final List<SourceFile> savedFiles = new ArrayList<>();
    private final List<ComparisonResult> savedComparisons = new ArrayList<>();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        analysisRepository = mock(AnalysisRepository.class);
        sourceFileRepository = mock(SourceFileRepository.class);
        contentRepository = mock(SourceFileContentRepository.class);
        comparisonRepository = mock(ComparisonResultRepository.class);
        regionRepository = mock(MatchingRegionRepository.class);
        CodeTraceProperties properties = new CodeTraceProperties("1.0.0",
                new CodeTraceProperties.Cors(List.of("http://localhost:5173")),
                new CodeTraceProperties.Upload(50, 524288, List.of(".java")),
                new CodeTraceProperties.Retention(true, 30, "0 0 3 * * *"),
                new CodeTraceProperties.Similarity(5, 4, 3, true, false));
        TokenizationService tokenization = new TokenizationService();
        service = new AnalysisService(analysisRepository, sourceFileRepository, contentRepository, comparisonRepository,
                regionRepository, new FileIntakeService(properties), new PairwiseComparisonService(tokenization),
                tokenization, new JsonCodec(new ObjectMapper()), properties, mock(PlatformTransactionManager.class));

        when(analysisRepository.save(any(Analysis.class))).thenAnswer(inv -> {
            savedAnalysis.set(inv.getArgument(0));
            return inv.getArgument(0);
        });
        when(sourceFileRepository.saveAll(anyIterable())).thenAnswer(inv -> {
            ((Iterable<SourceFile>) inv.getArgument(0)).forEach(savedFiles::add);
            return new ArrayList<>(savedFiles);
        });
        when(comparisonRepository.saveAll(anyIterable())).thenAnswer(inv -> {
            ((Iterable<ComparisonResult>) inv.getArgument(0)).forEach(savedComparisons::add);
            return new ArrayList<>(savedComparisons);
        });
        when(contentRepository.saveAll(anyIterable())).thenAnswer(inv -> inv.getArgument(0));
        when(regionRepository.saveAll(anyIterable())).thenAnswer(inv -> inv.getArgument(0));
        when(analysisRepository.findById(any())).thenAnswer(inv -> Optional.ofNullable(savedAnalysis.get()));
        when(sourceFileRepository.findByAnalysis_IdOrderByOrdinalAsc(any())).thenAnswer(inv -> savedFiles);
        when(comparisonRepository.findForAnalysis(any())).thenAnswer(inv -> savedComparisons);
    }

    private static MultipartFile file(String name, String code) {
        return new MockMultipartFile("files", name, "text/plain", code.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("createAnalysis runs the algorithms and persists files, n(n-1)/2 comparisons and regions")
    @SuppressWarnings("unchecked")
    void createPersistsEverything() {
        String code = "class A { int sum(int[] v) { int s = 0; for (int x : v) { s += x; } return s; } }";
        String renamed = "class B { int total(int[] d) { int t = 0; for (int y : d) { t += y; } return t; } }";
        String other = "interface Shape { double area(); default String label() { return \"shape\"; } }";

        AnalysisReportDto report = service.createAnalysis(
                List.of(file("A.java", code), file("B.java", renamed), file("C.java", other)),
                new AnalysisOptionsRequest("Lab 1", null, null, null, null, null));

        assertThat(savedAnalysis.get().getTitle()).isEqualTo("Lab 1");
        assertThat(savedAnalysis.get().getFileCount()).isEqualTo(3);
        assertThat(savedAnalysis.get().getComparisonCount()).isEqualTo(3);
        assertThat(savedFiles).extracting(SourceFile::getDisplayName).containsExactly("A.java", "B.java", "C.java");
        assertThat(savedComparisons).hasSize(3);
        assertThat(report.comparisons()).hasSize(3);
        ComparisonResult ab = savedComparisons.get(0);
        assertThat(ab.getFileA().getDisplayName()).isEqualTo("A.java");
        assertThat(ab.getOverallScore()).isEqualTo(1.0);
        assertThat(savedAnalysis.get().getMaxScore()).isEqualTo(1.0);

        ArgumentCaptor<Iterable<SourceFileContent>> contents = ArgumentCaptor.forClass(Iterable.class);
        verify(contentRepository).saveAll(contents.capture());
        assertThat(contents.getValue()).hasSize(3);
        ArgumentCaptor<Iterable<MatchingRegion>> regions = ArgumentCaptor.forClass(Iterable.class);
        verify(regionRepository).saveAll(regions.capture());
        assertThat(regions.getValue()).isNotEmpty();
    }

    @Test
    @DisplayName("invalid uploads are rejected before anything is persisted")
    void invalidUploadNothingPersisted() {
        assertThrows(ApiValidationException.class, () -> service.createAnalysis(
                List.of(file("A.java", "class A {}"), file("notes.txt", "hello")), AnalysisOptionsRequest.empty()));
        verify(analysisRepository, never()).save(any());
    }

    @Test
    @DisplayName("database failure during persistence propagates as DataAccessException (mapped to 503)")
    void databaseFailure() {
        when(analysisRepository.save(any(Analysis.class))).thenThrow(new DataAccessResourceFailureException("down"));
        assertThrows(DataAccessException.class, () -> service.createAnalysis(
                List.of(file("A.java", "class A {}"), file("B.java", "class B {}")), AnalysisOptionsRequest.empty()));
    }

    @Test
    @DisplayName("listing validates paging and sort parameters")
    void listValidation() {
        assertThrows(ApiValidationException.class, () -> service.listAnalyses(-1, 10, "createdAt", "desc", null));
        assertThrows(ApiValidationException.class, () -> service.listAnalyses(0, 0, "createdAt", "desc", null));
        assertThrows(ApiValidationException.class, () -> service.listAnalyses(0, 101, "createdAt", "desc", null));
        assertThrows(ApiValidationException.class, () -> service.listAnalyses(0, 10, "password", "desc", null));
        assertThrows(ApiValidationException.class, () -> service.listAnalyses(0, 10, "createdAt", "sideways", null));
    }

    @Test
    @DisplayName("search uses an escaped LIKE pattern; empty query lists everything")
    void listSearch() {
        Page<Analysis> empty = new PageImpl<>(List.of());
        when(analysisRepository.search(any(), any(Pageable.class))).thenReturn(empty);
        when(analysisRepository.findAll(any(Pageable.class))).thenReturn(empty);
        service.listAnalyses(0, 10, "createdAt", "desc", "50%");
        verify(analysisRepository).search(eq("%50!%%"), any(Pageable.class));
        service.listAnalyses(0, 10, "title", "asc", "   ");
        verify(analysisRepository).findAll(any(Pageable.class));
    }

    @Test
    @DisplayName("deleting an unknown analysis is a 404")
    void deleteUnknown() {
        UUID id = UUID.randomUUID();
        when(analysisRepository.deleteAnalysis(id)).thenReturn(0);
        assertThrows(ResourceNotFoundException.class, () -> service.deleteAnalysis(id));
        when(analysisRepository.deleteAnalysis(id)).thenReturn(1);
        service.deleteAnalysis(id);
    }

    @Test
    @DisplayName("unknown report and comparison are 404")
    void notFound() {
        savedAnalysis.set(null);
        assertThrows(ResourceNotFoundException.class, () -> service.getReport(UUID.randomUUID()));
        assertThrows(ResourceNotFoundException.class, () -> service.getComparison(UUID.randomUUID(), UUID.randomUUID()));
    }

    @Test
    @DisplayName("options outside the configured bounds are rejected")
    void optionBounds() {
        assertThrows(ApiValidationException.class, () -> service.resolveConfig(
                new AnalysisOptionsRequest(null, 60, null, null, null, null)));
        assertThat(service.resolveConfig(new AnalysisOptionsRequest(null, 7, 2, 1, false, true)).kGramSize()).isEqualTo(7);
    }
}
