package com.codetrace.dto;

/** Comparisons whose overall score is in [from, to) — the last bucket includes 1.0. */
public record ScoreBucketDto(String label, double from, double to, long count) {
}
