package com.codetrace.dto;

import java.time.Instant;

public record HealthDto(String status, String application, String version, DatabaseStatus database, Instant timestamp) {

    public record DatabaseStatus(String status, String message) {
    }
}
