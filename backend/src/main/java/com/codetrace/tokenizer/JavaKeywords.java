package com.codetrace.tokenizer;

import java.util.Set;

/** Reserved words, literal keywords and the contextual keywords CodeTrace treats as keywords. */
public final class JavaKeywords {

    private JavaKeywords() {
    }

    public static final Set<String> KEYWORDS = Set.of(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
            "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float",
            "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native",
            "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp",
            "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void",
            "volatile", "while",
            // literal keywords
            "true", "false", "null",
            // contextual keywords (treated as keywords for comparison purposes)
            "var", "record", "yield", "sealed", "permits");

    /** Keywords that can appear in the type position of a declaration. */
    public static final Set<String> TYPE_KEYWORDS = Set.of(
            "boolean", "byte", "char", "short", "int", "long", "float", "double", "void", "var");

    public static boolean isKeyword(String word) {
        return KEYWORDS.contains(word);
    }
}
