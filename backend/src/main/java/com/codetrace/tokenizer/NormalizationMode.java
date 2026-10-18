package com.codetrace.tokenizer;

/** How identifiers and literals are represented before comparison. */
public enum NormalizationMode {
    /** Token text is compared exactly as written (comments and whitespace are still ignored). */
    ORIGINAL,
    /**
     * Identifiers declared in the file are replaced by role-specific placeholders
     * (T for types, M for methods, V for variables/fields/parameters); string and character
     * literals are replaced by their literal type. Keywords and operators are preserved.
     */
    IDENTIFIERS
}
