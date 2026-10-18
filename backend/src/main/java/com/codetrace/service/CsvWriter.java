package com.codetrace.service;

import java.util.List;

/**
 * Minimal RFC 4180 CSV writer with spreadsheet formula-injection protection.
 * Text cells starting with {@code = + - @}, tab or carriage return are prefixed with a single quote so that
 * spreadsheet applications treat them as text (OWASP "CSV Injection" guidance).
 */
public final class CsvWriter {

    private final StringBuilder sb = new StringBuilder();

    public CsvWriter row(List<String> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(escape(cells.get(i)));
        }
        sb.append("\r\n");
        return this;
    }

    @Override
    public String toString() {
        return sb.toString();
    }

    /** Neutralizes formula prefixes in untrusted text. */
    public static String neutralize(String value) {
        if (value == null || value.isEmpty()) {
            return value == null ? "" : value;
        }
        char first = value.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
            return "'" + value;
        }
        return value;
    }

    /** Quotes the cell when it contains a delimiter, quote or line break. */
    public static String escape(String value) {
        String v = value == null ? "" : value;
        if (v.indexOf(',') >= 0 || v.indexOf('"') >= 0 || v.indexOf('\n') >= 0 || v.indexOf('\r') >= 0) {
            return '"' + v.replace("\"", "\"\"") + '"';
        }
        return v;
    }
}
