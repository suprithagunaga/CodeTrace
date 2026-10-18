package com.codetrace.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CsvWriterTest {

    @Test
    @DisplayName("quotes delimiters, quotes and newlines")
    void escaping() {
        assertEquals("plain", CsvWriter.escape("plain"));
        assertEquals("\"a,b\"", CsvWriter.escape("a,b"));
        assertEquals("\"say \"\"hi\"\"\"", CsvWriter.escape("say \"hi\""));
        assertEquals("\"line\nbreak\"", CsvWriter.escape("line\nbreak"));
        assertEquals("a,\"b,c\"\r\n", new CsvWriter().row(List.of("a", "b,c")).toString());
    }

    @Test
    @DisplayName("neutralizes spreadsheet formula prefixes")
    void formulaInjection() {
        assertEquals("'=HYPERLINK(\"x\")", CsvWriter.neutralize("=HYPERLINK(\"x\")"));
        assertEquals("'+cmd", CsvWriter.neutralize("+cmd"));
        assertEquals("'-1+1", CsvWriter.neutralize("-1+1"));
        assertEquals("'@SUM(A1)", CsvWriter.neutralize("@SUM(A1)"));
        assertEquals("Main.java", CsvWriter.neutralize("Main.java"));
        assertEquals("", CsvWriter.neutralize(null));
    }
}
