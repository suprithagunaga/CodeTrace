package com.codetrace.config;

import com.codetrace.similarity.SimilarityConfig;
import com.codetrace.tokenizer.NormalizationMode;
import com.codetrace.tokenizer.NormalizationOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * Application settings bound from {@code codetrace.*} (see application.yml).
 */
@ConfigurationProperties(prefix = "codetrace")
public record CodeTraceProperties(
        @DefaultValue("1.0.0") String version,
        @DefaultValue Cors cors,
        @DefaultValue Upload upload,
        @DefaultValue Retention retention,
        @DefaultValue Similarity similarity) {

    public record Cors(@DefaultValue("http://localhost:5173") List<String> allowedOrigins) {
    }

    /**
     * @param maxFiles     maximum number of files per analysis
     * @param maxFileBytes maximum size of a single file in bytes
     */
    public record Upload(@DefaultValue("50") int maxFiles,
                         @DefaultValue("524288") long maxFileBytes,
                         @DefaultValue(".java") List<String> allowedExtensions) {
    }

    /**
     * @param storeSource  keep the uploaded source text so the side-by-side view works after the request
     * @param days         analyses older than this are deleted automatically; 0 disables expiry
     * @param cleanupCron  schedule of the expiry job
     */
    public record Retention(@DefaultValue("true") boolean storeSource,
                            @DefaultValue("30") int days,
                            @DefaultValue("0 0 3 * * *") String cleanupCron) {
    }

    public record Similarity(@DefaultValue("5") int kGramSize,
                             @DefaultValue("4") int windowSize,
                             @DefaultValue("3") int jaccardNgram,
                             @DefaultValue("true") boolean normalizeIdentifiers,
                             @DefaultValue("false") boolean normalizeNumbers) {

        public SimilarityConfig toConfig() {
            return new SimilarityConfig(kGramSize, windowSize, jaccardNgram, new NormalizationOptions(
                    normalizeIdentifiers ? NormalizationMode.IDENTIFIERS : NormalizationMode.ORIGINAL, normalizeNumbers));
        }
    }
}
