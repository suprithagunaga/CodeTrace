package com.codetrace.similarity;

import com.codetrace.tokenizer.NormalizedToken;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Everything the engine derives from one source file. Built once per file and reused for all pairs.
 */
public record FileProfile(
        String name,
        int lineCount,
        List<NormalizedToken> tokens,
        List<String> values,
        Set<String> shingles,
        List<Fingerprint> fingerprints,
        Set<Long> fingerprintHashes,
        Map<Long, List<Integer>> fingerprintPositions,
        List<String> warnings) {

    public int tokenCount() {
        return tokens.size();
    }
}
