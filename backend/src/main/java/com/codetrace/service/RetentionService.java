package com.codetrace.service;

import com.codetrace.config.CodeTraceProperties;
import com.codetrace.repository.AnalysisRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Retention policy: analyses older than {@code codetrace.retention.days} are deleted (with all child rows,
 * including any retained source text) by a scheduled job. {@code days = 0} keeps analyses until deleted manually.
 */
@Service
public class RetentionService {

    private static final Logger log = LoggerFactory.getLogger(RetentionService.class);

    private final AnalysisRepository analysisRepository;
    private final CodeTraceProperties properties;

    public RetentionService(AnalysisRepository analysisRepository, CodeTraceProperties properties) {
        this.analysisRepository = analysisRepository;
        this.properties = properties;
    }

    @Scheduled(cron = "${codetrace.retention.cleanup-cron:0 0 3 * * *}")
    @Transactional
    public int purgeExpired() {
        int days = properties.retention().days();
        if (days <= 0) {
            return 0;
        }
        int deleted = analysisRepository.deleteCreatedBefore(Instant.now().minus(Duration.ofDays(days)));
        if (deleted > 0) {
            log.info("Retention: deleted {} analyses older than {} days", deleted, days);
        }
        return deleted;
    }
}
