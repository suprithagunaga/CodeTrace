package com.codetrace.service;

import com.codetrace.config.CodeTraceProperties;
import com.codetrace.dto.AlgorithmConfigDto;
import com.codetrace.dto.AnalysisOptionsRequest;
import com.codetrace.dto.AnalysisReportDto;
import com.codetrace.dto.AnalysisSummaryDto;
import com.codetrace.dto.ComparisonDetailDto;
import com.codetrace.dto.ComparisonSummaryDto;
import com.codetrace.dto.DashboardDto;
import com.codetrace.dto.MatchingRegionDto;
import com.codetrace.dto.PageResponse;
import com.codetrace.dto.ResultMetadataDto;
import com.codetrace.dto.ScoreBucketDto;
import com.codetrace.dto.SourceFileDto;
import com.codetrace.dto.TopPairDto;
import com.codetrace.entity.Analysis;
import com.codetrace.entity.AnalysisStatus;
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
import com.codetrace.similarity.FileProfile;
import com.codetrace.similarity.MatchedRegion;
import com.codetrace.similarity.PairResult;
import com.codetrace.similarity.SimilarityConfig;
import com.codetrace.tokenizer.NormalizationMode;
import com.codetrace.tokenizer.NormalizationOptions;
import com.codetrace.validation.ValidatedSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Analysis history and report generation: runs analyses, persists results and serves stored reports.
 *
 * <p>Transaction boundaries: the CPU-heavy comparison runs <em>outside</em> any transaction; only the
 * persistence step runs in one write transaction, so either the whole analysis is stored or nothing is.
 * Reads run in read-only transactions. Source code is never logged.
 */
@Service
public class AnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);
    private static final Map<String, String> SORTABLE = Map.of(
            "createdAt", "createdAt",
            "title", "title",
            "fileCount", "fileCount",
            "comparisonCount", "comparisonCount",
            "maxScore", "maxScore");
    public static final int MAX_PAGE_SIZE = 100;

    private final AnalysisRepository analysisRepository;
    private final SourceFileRepository sourceFileRepository;
    private final SourceFileContentRepository contentRepository;
    private final ComparisonResultRepository comparisonRepository;
    private final MatchingRegionRepository regionRepository;
    private final FileIntakeService intakeService;
    private final PairwiseComparisonService pairwiseService;
    private final TokenizationService tokenizationService;
    private final JsonCodec json;
    private final CodeTraceProperties properties;
    private final TransactionTemplate writeTx;
    private final TransactionTemplate readTx;

    public AnalysisService(AnalysisRepository analysisRepository,
                           SourceFileRepository sourceFileRepository,
                           SourceFileContentRepository contentRepository,
                           ComparisonResultRepository comparisonRepository,
                           MatchingRegionRepository regionRepository,
                           FileIntakeService intakeService,
                           PairwiseComparisonService pairwiseService,
                           TokenizationService tokenizationService,
                           JsonCodec json,
                           CodeTraceProperties properties,
                           PlatformTransactionManager transactionManager) {
        this.analysisRepository = analysisRepository;
        this.sourceFileRepository = sourceFileRepository;
        this.contentRepository = contentRepository;
        this.comparisonRepository = comparisonRepository;
        this.regionRepository = regionRepository;
        this.intakeService = intakeService;
        this.pairwiseService = pairwiseService;
        this.tokenizationService = tokenizationService;
        this.json = json;
        this.properties = properties;
        this.writeTx = new TransactionTemplate(transactionManager);
        this.readTx = new TransactionTemplate(transactionManager);
        this.readTx.setReadOnly(true);
    }

    // ------------------------------------------------------------------------------------------ create

    public AnalysisReportDto createAnalysis(List<org.springframework.web.multipart.MultipartFile> files,
                                            AnalysisOptionsRequest options) {
        Instant createdAt = Instant.now();
        AnalysisOptionsRequest opts = options == null ? AnalysisOptionsRequest.empty() : options;
        List<ValidatedSource> sources = intakeService.intake(files);
        SimilarityConfig config = resolveConfig(opts);

        long start = System.nanoTime();
        PairwiseComparisonService.ComputedAnalysis computed = pairwiseService.compareAll(sources, config);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        String title = resolveTitle(opts.title(), sources);
        boolean storeSource = properties.retention().storeSource();
        UUID id = writeTx.execute(status -> persist(title, createdAt, config, sources, computed, storeSource));
        log.info("Analysis {} completed: {} files, {} pairs, computed in {} ms", id, sources.size(),
                computed.pairs().size(), elapsedMs);
        return getReport(id);
    }

    SimilarityConfig resolveConfig(AnalysisOptionsRequest o) {
        CodeTraceProperties.Similarity d = properties.similarity();
        boolean identifiers = o.normalizeIdentifiers() != null ? o.normalizeIdentifiers() : d.normalizeIdentifiers();
        boolean numbers = o.normalizeNumbers() != null ? o.normalizeNumbers() : d.normalizeNumbers();
        try {
            return new SimilarityConfig(
                    o.kgramSize() != null ? o.kgramSize() : d.kGramSize(),
                    o.windowSize() != null ? o.windowSize() : d.windowSize(),
                    o.jaccardNgram() != null ? o.jaccardNgram() : d.jaccardNgram(),
                    new NormalizationOptions(identifiers ? NormalizationMode.IDENTIFIERS : NormalizationMode.ORIGINAL, numbers));
        } catch (IllegalArgumentException e) {
            throw new ApiValidationException(e.getMessage());
        }
    }

    static String resolveTitle(String requested, List<ValidatedSource> sources) {
        if (requested != null && !requested.isBlank()) {
            String t = requested.strip().replaceAll("\\p{Cntrl}", " ");
            return t.length() > 200 ? t.substring(0, 200) : t;
        }
        String first = sources.get(0).displayName() + ", " + sources.get(1).displayName();
        return sources.size() > 2 ? first + " +" + (sources.size() - 2) + " more" : first;
    }

    private UUID persist(String title, Instant createdAt, SimilarityConfig config, List<ValidatedSource> sources,
                         PairwiseComparisonService.ComputedAnalysis computed, boolean storeSource) {
        double maxScore = computed.pairs().stream().mapToDouble(p -> p.result().overall()).max().orElse(0.0);
        Analysis analysis = new Analysis(title, createdAt, AnalysisStatus.COMPLETED, json.write(JsonCodec.toDto(config)));
        analysis.setFileCount(sources.size());
        analysis.setComparisonCount(computed.pairs().size());
        analysis.setMaxScore(clamp(maxScore));
        analysis.setSourceRetained(storeSource);
        analysis.setCompletedAt(Instant.now());
        analysisRepository.save(analysis);

        List<SourceFile> files = new ArrayList<>(sources.size());
        for (int i = 0; i < sources.size(); i++) {
            ValidatedSource s = sources.get(i);
            FileProfile p = computed.profiles().get(i);
            files.add(new SourceFile(analysis, i, s.originalFilename(), s.displayName(), s.sizeBytes(), s.sha256(),
                    p.tokenCount(), p.lineCount(), json.write(p.warnings())));
        }
        files = sourceFileRepository.saveAll(files);
        // source_file_content has a database foreign key but no JPA association, so make sure the
        // source_file rows are written before their contents.
        sourceFileRepository.flush();

        if (storeSource) {
            List<SourceFileContent> contents = new ArrayList<>(files.size());
            for (int i = 0; i < files.size(); i++) {
                contents.add(new SourceFileContent(files.get(i).getId(), sources.get(i).content()));
            }
            contentRepository.saveAll(contents);
        }

        List<ComparisonResult> comparisons = new ArrayList<>(computed.pairs().size());
        for (PairwiseComparisonService.ComputedPair pair : computed.pairs()) {
            PairResult r = pair.result();
            List<String> warnings = new ArrayList<>(r.warnings());
            if (sources.get(pair.indexA()).sha256().equals(sources.get(pair.indexB()).sha256())) {
                warnings.add(0, "The two files are byte-for-byte identical");
            }
            ResultMetadataDto metadata = new ResultMetadataDto(r.fingerprintsA(), r.fingerprintsB(),
                    r.containmentA(), r.containmentB(), r.coverageA(), r.coverageB(),
                    r.fingerprintsA() > 0 && r.fingerprintsB() > 0, warnings);
            comparisons.add(new ComparisonResult(analysis, files.get(pair.indexA()), files.get(pair.indexB()),
                    clamp(r.jaccard()), clamp(r.winnowing()), clamp(r.overall()), r.sharedFingerprints(),
                    r.regions().size(), json.write(metadata)));
        }
        comparisons = comparisonRepository.saveAll(comparisons);

        List<MatchingRegion> regions = new ArrayList<>();
        for (int c = 0; c < comparisons.size(); c++) {
            for (MatchedRegion m : computed.pairs().get(c).result().regions()) {
                regions.add(new MatchingRegion(comparisons.get(c), m.fileAStartOffset(), m.fileAEndOffset(),
                        m.fileBStartOffset(), m.fileBEndOffset(), m.fileAStartLine(), m.fileAEndLine(),
                        m.fileBStartLine(), m.fileBEndLine(), m.tokenLength()));
            }
        }
        regionRepository.saveAll(regions);
        return analysis.getId();
    }

    private static double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    // ------------------------------------------------------------------------------------------ read

    public PageResponse<AnalysisSummaryDto> listAnalyses(int page, int size, String sort, String direction, String query) {
        if (page < 0) {
            throw new ApiValidationException("page must be >= 0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ApiValidationException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        String property = SORTABLE.get(sort == null ? "createdAt" : sort);
        if (property == null) {
            throw new ApiValidationException("sort must be one of " + String.join(", ", SORTABLE.keySet()));
        }
        Sort.Direction dir;
        if (direction == null || direction.equalsIgnoreCase("desc")) {
            dir = Sort.Direction.DESC;
        } else if (direction.equalsIgnoreCase("asc")) {
            dir = Sort.Direction.ASC;
        } else {
            throw new ApiValidationException("direction must be 'asc' or 'desc'");
        }
        Pageable pageable = PageRequest.of(page, size, Sort.by(dir, property).and(Sort.by(Sort.Direction.DESC, "id")));
        String q = query == null ? "" : query.strip();
        if (q.length() > 100) {
            throw new ApiValidationException("q must be at most 100 characters");
        }
        Page<Analysis> result = readTx.execute(status -> q.isEmpty()
                ? analysisRepository.findAll(pageable)
                : analysisRepository.search(likePattern(q), pageable));
        return new PageResponse<>(result.getContent().stream().map(AnalysisService::toSummary).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    /** Lower-cases, escapes LIKE wildcards with '!' and wraps in '%'. */
    static String likePattern(String q) {
        String escaped = q.toLowerCase(Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escaped + "%";
    }

    public AnalysisReportDto getReport(UUID id) {
        return readTx.execute(status -> {
            Analysis analysis = findAnalysis(id);
            List<SourceFile> files = sourceFileRepository.findByAnalysis_IdOrderByOrdinalAsc(id);
            List<ComparisonResult> comparisons = comparisonRepository.findForAnalysis(id);
            return new AnalysisReportDto(toSummary(analysis), json.readConfig(analysis.getAlgorithmConfiguration()),
                    analysis.isSourceRetained(),
                    files.stream().map(this::toFileDto).toList(),
                    comparisons.stream().map(this::toComparisonSummary).toList(),
                    AnalysisReportDto.DISCLAIMER);
        });
    }

    public ComparisonDetailDto getComparison(UUID analysisId, UUID comparisonId) {
        return readTx.execute(status -> {
            Analysis analysis = findAnalysis(analysisId);
            ComparisonResult c = comparisonRepository.findInAnalysis(analysisId, comparisonId)
                    .orElseThrow(() -> new ResourceNotFoundException("Comparison " + comparisonId
                            + " not found in analysis " + analysisId));
            List<MatchingRegionDto> regions = regionRepository.findByComparison_IdOrderByFileAStartOffsetAsc(comparisonId)
                    .stream().map(AnalysisService::toRegionDto).toList();
            AlgorithmConfigDto configDto = json.readConfig(analysis.getAlgorithmConfiguration());
            ResultMetadataDto metadata = json.readMetadata(c.getResultMetadata());

            Optional<SourceFileContent> contentA = analysis.isSourceRetained()
                    ? contentRepository.findById(c.getFileA().getId()) : Optional.empty();
            Optional<SourceFileContent> contentB = analysis.isSourceRetained()
                    ? contentRepository.findById(c.getFileB().getId()) : Optional.empty();
            boolean available = contentA.isPresent() && contentB.isPresent();
            String sourceA = available ? contentA.get().getContent() : null;
            String sourceB = available ? contentB.get().getContent() : null;
            List<String> linesA = List.of();
            List<String> linesB = List.of();
            if (available) {
                SimilarityConfig config = JsonCodec.fromDto(configDto);
                linesA = tokenizationService.normalizedLines(sourceA, config);
                linesB = tokenizationService.normalizedLines(sourceB, config);
            }
            return new ComparisonDetailDto(c.getId(), analysisId, toFileDto(c.getFileA()), toFileDto(c.getFileB()),
                    c.getJaccardScore(), c.getWinnowingScore(), c.getOverallScore(), c.getSharedFingerprintCount(),
                    metadata, regions, configDto, explain(c, metadata, configDto), available, sourceA, sourceB,
                    linesA, linesB, AnalysisReportDto.DISCLAIMER);
        });
    }

    static String explain(ComparisonResult c, ResultMetadataDto m, AlgorithmConfigDto cfg) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT,
                "Jaccard compares the sets of %d-token shingles of both files: %.1f%% of the distinct shingles are shared. ",
                cfg.jaccardNgram(), c.getJaccardScore() * 100));
        if (m.winnowingApplicable()) {
            sb.append(String.format(Locale.ROOT,
                    "Winnowing selected %d and %d distinct fingerprints (k=%d tokens, window w=%d); %d are shared, "
                            + "a fingerprint overlap of %.1f%%. ",
                    m.fingerprintsA(), m.fingerprintsB(), cfg.kgramSize(), cfg.windowSize(),
                    c.getSharedFingerprintCount(), c.getWinnowingScore() * 100));
            sb.append(String.format(Locale.ROOT,
                    "Directionally, %.1f%% of file A's fingerprints occur in file B and %.1f%% of file B's occur in file A. ",
                    m.containmentA() * 100, m.containmentB() * 100));
            sb.append(String.format(Locale.ROOT, "The overall score is the unweighted mean of the two: %.1f%%.",
                    c.getOverallScore() * 100));
        } else {
            sb.append(String.format(Locale.ROOT,
                    "Winnowing was not applicable because a file has fewer than k=%d tokens, so the overall score equals the Jaccard score (%.1f%%).",
                    cfg.kgramSize(), c.getOverallScore() * 100));
        }
        if ("IDENTIFIERS".equals(cfg.normalizationMode())) {
            sb.append(" Identifiers declared in each file were normalized by role, so consistent renaming does not lower the score.");
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------------------------------ delete

    public void deleteAnalysis(UUID id) {
        writeTx.executeWithoutResult(status -> {
            int deleted = analysisRepository.deleteAnalysis(id);
            if (deleted == 0) {
                throw new ResourceNotFoundException("Analysis " + id + " not found");
            }
        });
        log.info("Analysis {} deleted with all associated records", id);
    }

    // ------------------------------------------------------------------------------------------ dashboard

    public DashboardDto dashboard() {
        return readTx.execute(status -> {
            List<Object[]> totalsRows = analysisRepository.totals();
            Object[] totals = totalsRows.isEmpty() ? new Object[]{0, 0, 0} : totalsRows.get(0);
            long[] counts = new long[10];
            for (Object[] row : comparisonRepository.scoreHistogram()) {
                int bucket = (int) Math.max(0, Math.min(9, ((Number) row[0]).doubleValue()));
                counts[bucket] += ((Number) row[1]).longValue();
            }
            List<ScoreBucketDto> buckets = new ArrayList<>(10);
            for (int i = 0; i < 10; i++) {
                buckets.add(new ScoreBucketDto((i * 10) + "–" + ((i + 1) * 10) + "%", i / 10.0, (i + 1) / 10.0, counts[i]));
            }
            List<TopPairDto> top = comparisonRepository.findTopScoring(PageRequest.of(0, 5)).stream()
                    .map(c -> new TopPairDto(c.getAnalysis().getId(), c.getAnalysis().getTitle(), c.getId(),
                            c.getFileA().getDisplayName(), c.getFileB().getDisplayName(), c.getOverallScore(),
                            c.getAnalysis().getCreatedAt()))
                    .toList();
            List<AnalysisSummaryDto> recent = analysisRepository
                    .findAll(PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "createdAt")))
                    .getContent().stream().map(AnalysisService::toSummary).toList();
            return new DashboardDto(((Number) totals[0]).longValue(), ((Number) totals[1]).longValue(),
                    ((Number) totals[2]).longValue(), buckets, top, recent);
        });
    }

    // ------------------------------------------------------------------------------------------ mapping

    private Analysis findAnalysis(UUID id) {
        return analysisRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Analysis " + id + " not found"));
    }

    static AnalysisSummaryDto toSummary(Analysis a) {
        return new AnalysisSummaryDto(a.getId(), a.getTitle(), a.getCreatedAt(), a.getCompletedAt(),
                a.getStatus().name(), a.getFileCount(), a.getComparisonCount(), a.getMaxScore());
    }

    private SourceFileDto toFileDto(SourceFile f) {
        return new SourceFileDto(f.getId(), f.getOrdinal(), f.getDisplayName(), f.getFileSize(), f.getContentHash(),
                f.getTokenCount(), f.getLineCount(), json.readStringList(f.getWarnings()));
    }

    private ComparisonSummaryDto toComparisonSummary(ComparisonResult c) {
        ResultMetadataDto metadata = json.readMetadata(c.getResultMetadata());
        return new ComparisonSummaryDto(c.getId(), c.getFileA().getId(), c.getFileA().getDisplayName(),
                c.getFileB().getId(), c.getFileB().getDisplayName(), c.getJaccardScore(), c.getWinnowingScore(),
                c.getOverallScore(), c.getSharedFingerprintCount(), c.getRegionCount(), metadata.warnings());
    }

    private static MatchingRegionDto toRegionDto(MatchingRegion r) {
        return new MatchingRegionDto(r.getFileAStartOffset(), r.getFileAEndOffset(), r.getFileBStartOffset(),
                r.getFileBEndOffset(), r.getFileAStartLine(), r.getFileAEndLine(), r.getFileBStartLine(),
                r.getFileBEndLine(), r.getTokenLength());
    }
}
