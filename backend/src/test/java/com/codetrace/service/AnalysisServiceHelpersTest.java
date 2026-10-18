package com.codetrace.service;

import com.codetrace.validation.ValidatedSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnalysisServiceHelpersTest {

    private static ValidatedSource src(String name) {
        return new ValidatedSource(name, name, 1, "h", "x");
    }

    @Test
    @DisplayName("search pattern is lower-cased and LIKE wildcards are escaped")
    void likePattern() {
        assertEquals("%main.java%", AnalysisService.likePattern("Main.java"));
        assertEquals("%100!%!_done!!%", AnalysisService.likePattern("100%_done!"));
    }

    @Test
    @DisplayName("default title is derived from filenames; explicit title is cleaned")
    void title() {
        assertEquals("A.java, B.java", AnalysisService.resolveTitle(null, List.of(src("A.java"), src("B.java"))));
        assertEquals("A.java, B.java +2 more", AnalysisService.resolveTitle("  ",
                List.of(src("A.java"), src("B.java"), src("C.java"), src("D.java"))));
        assertEquals("Week 3 lab", AnalysisService.resolveTitle("  Week 3 lab ", List.of(src("A.java"), src("B.java"))));
        assertEquals("a b", AnalysisService.resolveTitle("a\u0007b", List.of(src("A.java"), src("B.java"))));
    }
}
