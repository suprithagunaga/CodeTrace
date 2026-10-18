package com.codetrace.tokenizer;

import java.util.Objects;

/**
 * @param mode             identifier handling
 * @param normalizeNumbers when true every numeric literal becomes {@code <NUM>}
 */
public record NormalizationOptions(NormalizationMode mode, boolean normalizeNumbers) {

    public NormalizationOptions {
        Objects.requireNonNull(mode, "mode");
    }

    public static NormalizationOptions original() {
        return new NormalizationOptions(NormalizationMode.ORIGINAL, false);
    }

    public static NormalizationOptions identifiers() {
        return new NormalizationOptions(NormalizationMode.IDENTIFIERS, false);
    }
}
