package com.codetrace.service;

import com.codetrace.dto.AlgorithmConfigDto;
import com.codetrace.dto.AnalysisReportDto;
import com.codetrace.dto.AnalysisSummaryDto;
import com.codetrace.dto.ComparisonSummaryDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportCsvTest {

    @Test
    @DisplayName("CSV export contains one row per comparison with percentages and safe filenames")
    void csv() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        AnalysisReportDto report = new AnalysisReportDto(
                new AnalysisSummaryDto(id, "=evil()", Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:01Z"),
                        "COMPLETED", 2, 1, 0.5),
                new AlgorithmConfigDto(5, 4, 3, "IDENTIFIERS", false, AlgorithmConfigDto.COMBINED_RULE),
                true, List.of(),
                List.of(new ComparisonSummaryDto(UUID.randomUUID(), UUID.randomUUID(), "@A.java", UUID.randomUUID(),
                        "B,1.java", 0.25, 0.75, 0.5, 12, 2, List.of("note"))),
                AnalysisReportDto.DISCLAIMER);
        String[] lines = ReportExportService.toCsv(report).split("\r\n");
        assertEquals(2, lines.length);
        assertTrue(lines[0].startsWith("analysis_id,analysis_title,created_at"));
        assertEquals("00000000-0000-0000-0000-000000000001,'=evil(),2026-01-01T00:00:00Z,5,4,3,IDENTIFIERS,'@A.java,\"B,1.java\",50.00,25.00,75.00,12,2,note",
                lines[1]);
    }
}
