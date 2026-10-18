package com.codetrace.similarity;

import com.codetrace.tokenizer.JavaTokenizer;
import com.codetrace.tokenizer.NormalizedToken;
import com.codetrace.tokenizer.Token;
import com.codetrace.tokenizer.TokenNormalizer;
import com.codetrace.tokenizer.TokenizationResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Framework-free facade that runs the full pipeline: tokenize → normalize → shingle/fingerprint → compare.
 *
 * <h2>Combined score</h2>
 * {@code overall = (jaccard + winnowing) / 2} — an unweighted arithmetic mean of the two symmetric scores.
 * If Winnowing is not applicable (a file has fewer than k tokens) the overall score equals the Jaccard score.
 * It is a ranking aid, <b>not</b> a calibrated probability of copying.
 */
public final class SimilarityEngine {

    private final JavaTokenizer tokenizer = new JavaTokenizer();
    private final TokenNormalizer normalizer = new TokenNormalizer();
    private final SimilarityConfig config;

    public SimilarityEngine(SimilarityConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public SimilarityConfig config() {
        return config;
    }

    public FileProfile profile(String name, String source) {
        TokenizationResult tr = tokenizer.tokenize(source);
        List<NormalizedToken> normalized = normalizer.normalize(tr.tokens(), config.normalization());
        List<String> values = new ArrayList<>(normalized.size());
        for (NormalizedToken nt : normalized) {
            values.add(nt.value());
        }
        List<Fingerprint> fps = Winnowing.fingerprints(values, config.kGramSize(), config.windowSize());
        Set<Long> hashes = new LinkedHashSet<>();
        Map<Long, List<Integer>> positions = new LinkedHashMap<>();
        for (Fingerprint fp : fps) {
            hashes.add(fp.hash());
            positions.computeIfAbsent(fp.hash(), h -> new ArrayList<>()).add(fp.tokenIndex());
        }
        List<String> warnings = new ArrayList<>(tr.warnings());
        if (values.isEmpty()) {
            warnings.add("No code tokens found (file is empty or contains only comments)");
        } else if (values.size() < config.kGramSize()) {
            warnings.add("Only " + values.size() + " tokens; fewer than k=" + config.kGramSize()
                    + ", so no Winnowing fingerprints could be generated");
        }
        return new FileProfile(name, tr.lineCount(), List.copyOf(normalized), List.copyOf(values),
                Collections.unmodifiableSet(JaccardSimilarity.shingles(values, config.jaccardNgram())),
                List.copyOf(fps), Collections.unmodifiableSet(hashes), Collections.unmodifiableMap(positions),
                List.copyOf(warnings));
    }

    public PairResult compare(FileProfile a, FileProfile b) {
        Objects.requireNonNull(a, "a");
        Objects.requireNonNull(b, "b");
        List<String> warnings = new ArrayList<>();

        double jaccard = JaccardSimilarity.compute(a.shingles(), b.shingles());

        int shared = 0;
        Set<Long> small = a.fingerprintHashes().size() <= b.fingerprintHashes().size() ? a.fingerprintHashes() : b.fingerprintHashes();
        Set<Long> large = small == a.fingerprintHashes() ? b.fingerprintHashes() : a.fingerprintHashes();
        for (Long h : small) {
            if (large.contains(h)) {
                shared++;
            }
        }
        int fa = a.fingerprintHashes().size();
        int fb = b.fingerprintHashes().size();
        boolean winnowingApplicable = fa > 0 && fb > 0;
        double winnowing = winnowingApplicable ? (double) shared / (fa + fb - shared) : 0.0;
        double containmentA = fa == 0 ? 0.0 : (double) shared / fa;
        double containmentB = fb == 0 ? 0.0 : (double) shared / fb;
        if (!winnowingApplicable) {
            warnings.add("Winnowing not applicable: at least one file has fewer than k=" + config.kGramSize()
                    + " tokens; overall score uses Jaccard only");
        }
        if (a.tokenCount() == 0 || b.tokenCount() == 0) {
            warnings.add("At least one file has no code tokens; similarity is reported as 0");
        }

        List<TokenRegion> tokenRegions = MatchRegionFinder.find(a.values(), b.values(),
                a.fingerprintPositions(), b.fingerprintPositions(), config.kGramSize(), config.guaranteeThreshold());
        List<MatchedRegion> regions = new ArrayList<>(tokenRegions.size());
        for (TokenRegion r : tokenRegions) {
            Token as = a.tokens().get(r.aStart()).source();
            Token ae = a.tokens().get(r.aEnd() - 1).source();
            Token bs = b.tokens().get(r.bStart()).source();
            Token be = b.tokens().get(r.bEnd() - 1).source();
            regions.add(new MatchedRegion(as.startOffset(), ae.endOffset(), bs.startOffset(), be.endOffset(),
                    as.line(), ae.endLine(), bs.line(), be.endLine(), r.length()));
        }
        if (winnowingApplicable && regions.isEmpty()) {
            warnings.add("No shared run of at least " + config.guaranteeThreshold() + " normalized tokens was found");
        }

        return new PairResult(jaccard, winnowing, combine(jaccard, winnowing, winnowingApplicable),
                shared, fa, fb, containmentA, containmentB,
                MatchRegionFinder.coverage(tokenRegions, a.tokenCount(), true),
                MatchRegionFinder.coverage(tokenRegions, b.tokenCount(), false),
                List.copyOf(regions), List.copyOf(warnings));
    }

    /** Documented aggregation rule; see class Javadoc. */
    public static double combine(double jaccard, double winnowing, boolean winnowingApplicable) {
        return winnowingApplicable ? (jaccard + winnowing) / 2.0 : jaccard;
    }
}
