package com.codetrace.tokenizer;

import java.util.List;

/**
 * Output of tokenization: the significant tokens (no whitespace or comments),
 * the number of lines in the source, and any recoverable problems found.
 */
public record TokenizationResult(List<Token> tokens, int lineCount, List<String> warnings) {

    public TokenizationResult {
        tokens = List.copyOf(tokens);
        warnings = List.copyOf(warnings);
    }
}
