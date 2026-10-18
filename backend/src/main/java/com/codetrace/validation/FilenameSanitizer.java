package com.codetrace.validation;

/**
 * Produces a safe display name from an untrusted client-supplied filename.
 * <ul>
 *   <li>Drops any directory part (both {@code /} and {@code \}), so names like {@code ../../etc/passwd.java}
 *       cannot express a path.</li>
 *   <li>Keeps only letters, digits, space, {@code . _ - $ ( )}; everything else (control characters, quotes,
 *       angle brackets, ...) becomes {@code _}.</li>
 *   <li>Removes leading dots and limits the length.</li>
 * </ul>
 * The result is only ever used as a label; files are never written to disk.
 */
public final class FilenameSanitizer {

    public static final int MAX_LENGTH = 120;
    private static final String FALLBACK = "unnamed";

    private FilenameSanitizer() {
    }

    public static String sanitize(String raw) {
        if (raw == null) {
            return FALLBACK;
        }
        String name = raw;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        StringBuilder sb = new StringBuilder(name.length());
        name.codePoints().forEach(cp -> {
            if (Character.isLetterOrDigit(cp) || cp == ' ' || cp == '.' || cp == '_' || cp == '-' || cp == '$'
                    || cp == '(' || cp == ')') {
                sb.appendCodePoint(cp);
            } else {
                sb.append('_');
            }
        });
        String cleaned = sb.toString().strip();
        while (cleaned.startsWith(".")) {
            cleaned = cleaned.substring(1);
        }
        if (cleaned.length() > MAX_LENGTH) {
            int dot = cleaned.lastIndexOf('.');
            String ext = dot > 0 && cleaned.length() - dot <= 10 ? cleaned.substring(dot) : "";
            cleaned = cleaned.substring(0, MAX_LENGTH - ext.length()) + ext;
        }
        return cleaned.isBlank() ? FALLBACK : cleaned;
    }

    /** Lower-case extension including the dot, or empty string. */
    public static String extension(String sanitizedName) {
        int dot = sanitizedName.lastIndexOf('.');
        return dot < 0 ? "" : sanitizedName.substring(dot).toLowerCase(java.util.Locale.ROOT);
    }
}
