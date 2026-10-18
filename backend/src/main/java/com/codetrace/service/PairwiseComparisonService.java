package com.codetrace.service;

import com.codetrace.similarity.FileProfile;
import com.codetrace.similarity.PairResult;
import com.codetrace.similarity.SimilarityConfig;
import com.codetrace.similarity.SimilarityEngine;
import com.codetrace.validation.ValidatedSource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Pairwise comparison: every unordered pair (i &lt; j) is compared exactly once,
 * giving n * (n - 1) / 2 results for n files.
 */
@Service
public class PairwiseComparisonService {

    public record ComputedPair(int indexA, int indexB, PairResult result) {
    }

    public record ComputedAnalysis(List<FileProfile> profiles, List<ComputedPair> pairs) {
    }

    private final TokenizationService tokenizationService;

    public PairwiseComparisonService(TokenizationService tokenizationService) {
        this.tokenizationService = tokenizationService;
    }

    public ComputedAnalysis compareAll(List<ValidatedSource> sources, SimilarityConfig config) {
        SimilarityEngine engine = new SimilarityEngine(config);
        List<FileProfile> profiles = new ArrayList<>(sources.size());
        for (ValidatedSource s : sources) {
            profiles.add(tokenizationService.profile(s.displayName(), s.content(), config));
        }
        List<ComputedPair> pairs = new ArrayList<>(sources.size() * (sources.size() - 1) / 2);
        for (int i = 0; i < profiles.size(); i++) {
            for (int j = i + 1; j < profiles.size(); j++) {
                pairs.add(new ComputedPair(i, j, engine.compare(profiles.get(i), profiles.get(j))));
            }
        }
        return new ComputedAnalysis(List.copyOf(profiles), List.copyOf(pairs));
    }

    public static int expectedPairCount(int fileCount) {
        return fileCount * (fileCount - 1) / 2;
    }
}
