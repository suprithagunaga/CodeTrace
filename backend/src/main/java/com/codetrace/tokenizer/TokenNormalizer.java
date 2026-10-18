package com.codetrace.tokenizer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Converts raw tokens into the values the similarity algorithms compare.
 *
 * <h2>Identifier normalization (mode {@link NormalizationMode#IDENTIFIERS})</h2>
 * A lightweight, purely lexical role analysis is used instead of blindly renaming every identifier:
 * <ol>
 *   <li>Names inside {@code package} and {@code import} declarations and annotation names are kept verbatim.</li>
 *   <li>Names <em>declared in this file</em> are detected from their syntactic position:
 *     <ul>
 *       <li>after {@code class}/{@code interface}/{@code enum}/{@code record} &rarr; type ({@code T})</li>
 *       <li>a type followed by a name and {@code (} &rarr; method ({@code M})</li>
 *       <li>a type followed by a name and {@code = ; , ) : [} &rarr; variable, field or parameter ({@code V})</li>
 *       <li>a name directly before {@code ->} &rarr; lambda parameter (variable)</li>
 *     </ul></li>
 *   <li>Every occurrence of a declared name is replaced by its role placeholder ({@code T}, {@code M} or {@code V}).
 *       Placeholders are deliberately <em>not</em> numbered: numbering by first appearance would make a method copied
 *       into a larger file receive different numbers than in its original and hide partial copies.
 *       Names that are <em>not</em> declared in the file (e.g. {@code System}, {@code println}, {@code size})
 *       are library/API references and are kept verbatim, because renaming them changes meaning.</li>
 *   <li>String and text-block literals become {@code <STR>}, character literals become {@code <CHR>}.</li>
 * </ol>
 *
 * <h2>Known limitations</h2>
 * The analysis is lexical, not semantic: it ignores scopes and shadowing, distinct variables are not distinguished
 * from each other ({@code a = b + c} and {@code a = a + a} both become {@code V = V + V}), a name declared with two roles keeps the first role found, multi-parameter lambdas
 * {@code (a, b) ->} are not recognised as declarations, and an expression such as {@code x > y;}
 * can be mistaken for a declaration of {@code y}. These cases only affect how strongly renamed code matches;
 * they never cause crashes.
 */
public final class TokenNormalizer {

    public static final String STRING_PLACEHOLDER = "<STR>";
    public static final String CHAR_PLACEHOLDER = "<CHR>";
    public static final String NUMBER_PLACEHOLDER = "<NUM>";

    private static final Set<String> TYPE_DECLARING_KEYWORDS = Set.of("class", "interface", "enum", "record");
    private static final Set<String> VARIABLE_FOLLOWERS = Set.of("=", ";", ",", ")", ":", "[");

    private enum Role { TYPE, METHOD, VARIABLE }

    public List<NormalizedToken> normalize(List<Token> tokens, NormalizationOptions options) {
        Objects.requireNonNull(tokens, "tokens");
        Objects.requireNonNull(options, "options");
        List<NormalizedToken> out = new ArrayList<>(tokens.size());
        if (options.mode() == NormalizationMode.ORIGINAL) {
            for (Token t : tokens) {
                String value = (t.type() == TokenType.NUMBER && options.normalizeNumbers()) ? NUMBER_PLACEHOLDER : t.text();
                out.add(new NormalizedToken(value, t));
            }
            return out;
        }

        boolean[] verbatim = markVerbatimContexts(tokens);
        Map<String, Role> declared = findDeclarations(tokens, verbatim);

        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            String value = switch (t.type()) {
                case STRING, TEXT_BLOCK -> STRING_PLACEHOLDER;
                case CHAR -> CHAR_PLACEHOLDER;
                case NUMBER -> options.normalizeNumbers() ? NUMBER_PLACEHOLDER : t.text();
                case IDENTIFIER -> {
                    Role role = verbatim[i] ? null : declared.get(t.text());
                    if (role == null) {
                        yield t.text();
                    }
                    yield placeholder(role);
                }
                default -> t.text();
            };
            out.add(new NormalizedToken(value, t));
        }
        return out;
    }

    /**
     * Builds a per-line view of normalized token values, preserving original line numbers.
     * Index {@code 0} of the returned list is line 1.
     */
    public static List<String> toLines(List<NormalizedToken> tokens, int lineCount) {
        List<StringBuilder> lines = new ArrayList<>(lineCount);
        for (int i = 0; i < lineCount; i++) {
            lines.add(new StringBuilder());
        }
        for (NormalizedToken nt : tokens) {
            int idx = nt.source().line() - 1;
            if (idx < 0 || idx >= lineCount) {
                continue;
            }
            StringBuilder sb = lines.get(idx);
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(nt.value());
        }
        List<String> result = new ArrayList<>(lineCount);
        for (StringBuilder sb : lines) {
            result.add(sb.toString());
        }
        return result;
    }

    private static String placeholder(Role role) {
        return switch (role) {
            case TYPE -> "T";
            case METHOD -> "M";
            case VARIABLE -> "V";
        };
    }

    /** Package/import declarations and annotation names are kept verbatim. */
    private static boolean[] markVerbatimContexts(List<Token> tokens) {
        boolean[] verbatim = new boolean[tokens.size()];
        boolean inHeader = false;
        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            if (t.isKeyword("package") || t.isKeyword("import")) {
                inHeader = true;
            }
            if (inHeader) {
                verbatim[i] = true;
                if (t.isSymbol(";")) {
                    inHeader = false;
                }
                continue;
            }
            if (t.type() == TokenType.IDENTIFIER && i > 0 && tokens.get(i - 1).isSymbol("@")) {
                // annotation name, possibly qualified: @a.b.Name
                int j = i;
                verbatim[j] = true;
                while (j + 2 < tokens.size() && tokens.get(j + 1).isSymbol(".")
                        && tokens.get(j + 2).type() == TokenType.IDENTIFIER) {
                    j += 2;
                    verbatim[j] = true;
                }
            }
        }
        return verbatim;
    }

    private static Map<String, Role> findDeclarations(List<Token> tokens, boolean[] verbatim) {
        Map<String, Role> declared = new HashMap<>();
        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            if (t.type() != TokenType.IDENTIFIER || verbatim[i]) {
                continue;
            }
            Token prev = i > 0 ? tokens.get(i - 1) : null;
            Token next = i + 1 < tokens.size() ? tokens.get(i + 1) : null;
            Role role = null;

            if (prev != null && prev.type() == TokenType.KEYWORD && TYPE_DECLARING_KEYWORDS.contains(prev.text())) {
                role = Role.TYPE;
            } else if (next != null && next.isSymbol("->")) {
                role = Role.VARIABLE; // single-parameter lambda
            } else if (prev != null && next != null && isTypeEnd(prev)) {
                if (next.isSymbol("(")) {
                    role = Role.METHOD;
                } else if ((next.type() == TokenType.OPERATOR || next.type() == TokenType.SEPARATOR)
                        && VARIABLE_FOLLOWERS.contains(next.text())) {
                    role = Role.VARIABLE;
                }
            }
            if (role != null) {
                declared.putIfAbsent(t.text(), role);
            }
        }
        return declared;
    }

    /** True when the token can end the type part of a declaration (e.g. {@code int}, {@code String}, {@code List<X>}, {@code int[]}). */
    private static boolean isTypeEnd(Token prev) {
        return switch (prev.type()) {
            case IDENTIFIER -> true;
            case KEYWORD -> JavaKeywords.TYPE_KEYWORDS.contains(prev.text());
            case OPERATOR -> prev.text().equals(">") || prev.text().equals(">>") || prev.text().equals(">>>")
                    || prev.text().equals("...");
            case SEPARATOR -> prev.text().equals("]");
            default -> false;
        };
    }
}
