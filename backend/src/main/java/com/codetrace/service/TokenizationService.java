package com.codetrace.service;

import com.codetrace.similarity.FileProfile;
import com.codetrace.similarity.SimilarityConfig;
import com.codetrace.similarity.SimilarityEngine;
import com.codetrace.tokenizer.JavaTokenizer;
import com.codetrace.tokenizer.TokenNormalizer;
import com.codetrace.tokenizer.TokenizationResult;
import org.springframework.stereotype.Service;

import java.util.List;

/** Tokenization and normalization (wraps the framework-free tokenizer package). */
@Service
public class TokenizationService {

    private final JavaTokenizer tokenizer = new JavaTokenizer();
    private final TokenNormalizer normalizer = new TokenNormalizer();

    /** Tokenizes, normalizes and fingerprints one file under the given configuration. */
    public FileProfile profile(String name, String source, SimilarityConfig config) {
        return new SimilarityEngine(config).profile(name, source);
    }

    /** Normalized token values grouped by original line (index 0 = line 1), for the token view in the UI. */
    public List<String> normalizedLines(String source, SimilarityConfig config) {
        TokenizationResult result = tokenizer.tokenize(source);
        return TokenNormalizer.toLines(normalizer.normalize(result.tokens(), config.normalization()), result.lineCount());
    }
}
