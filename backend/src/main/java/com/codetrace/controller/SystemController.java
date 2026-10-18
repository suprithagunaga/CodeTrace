package com.codetrace.controller;

import com.codetrace.config.CodeTraceProperties;
import com.codetrace.dto.ConfigInfoDto;
import com.codetrace.dto.HealthDto;
import com.codetrace.service.JsonCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Instant;

/** Health (application + database connectivity) and public configuration. */
@RestController
@RequestMapping("/api")
public class SystemController {

    private static final Logger log = LoggerFactory.getLogger(SystemController.class);

    private final DataSource dataSource;
    private final CodeTraceProperties properties;

    public SystemController(DataSource dataSource, CodeTraceProperties properties) {
        this.dataSource = dataSource;
        this.properties = properties;
    }

    /** 200 when the database answers, 503 otherwise. The body never contains connection details. */
    @GetMapping("/health")
    public ResponseEntity<HealthDto> health() {
        HealthDto.DatabaseStatus db;
        try (Connection connection = dataSource.getConnection()) {
            db = connection.isValid(2)
                    ? new HealthDto.DatabaseStatus("UP", "PostgreSQL connection is valid")
                    : new HealthDto.DatabaseStatus("DOWN", "Database connection is not valid");
        } catch (Exception e) {
            log.warn("Health check: database unreachable ({})", e.getClass().getSimpleName());
            db = new HealthDto.DatabaseStatus("DOWN", "Database is unreachable");
        }
        boolean up = "UP".equals(db.status());
        HealthDto body = new HealthDto(up ? "UP" : "DEGRADED", "CodeTrace", properties.version(), db, Instant.now());
        return ResponseEntity.status(up ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    @GetMapping("/config")
    public ConfigInfoDto config() {
        return new ConfigInfoDto(properties.version(), properties.upload().maxFiles(), properties.upload().maxFileBytes(),
                properties.upload().allowedExtensions(), properties.retention().storeSource(),
                properties.retention().days(), JsonCodec.toDto(properties.similarity().toConfig()));
    }
}
