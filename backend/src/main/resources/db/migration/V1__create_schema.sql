-- CodeTrace schema. Managed by Flyway; Hibernate does not create or alter tables.
-- Every child table cascades on delete so that removing an analysis removes all of its records.

CREATE TABLE analysis (
    id                      UUID PRIMARY KEY,
    title                   VARCHAR(300)     NOT NULL,
    created_at              TIMESTAMPTZ      NOT NULL,
    completed_at            TIMESTAMPTZ,
    status                  VARCHAR(20)      NOT NULL,
    file_count              INTEGER          NOT NULL,
    comparison_count        INTEGER          NOT NULL,
    max_score               DOUBLE PRECISION,
    algorithm_configuration TEXT             NOT NULL,
    source_retained         BOOLEAN          NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_analysis_status CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED')),
    CONSTRAINT ck_analysis_counts CHECK (file_count >= 0 AND comparison_count >= 0),
    CONSTRAINT ck_analysis_score CHECK (max_score IS NULL OR (max_score >= 0 AND max_score <= 1))
);

CREATE INDEX ix_analysis_created_at ON analysis (created_at DESC);

CREATE TABLE source_file (
    id                UUID PRIMARY KEY,
    analysis_id       UUID          NOT NULL REFERENCES analysis (id) ON DELETE CASCADE,
    ordinal           INTEGER       NOT NULL,
    original_filename VARCHAR(255)  NOT NULL,
    display_name      VARCHAR(300)  NOT NULL,
    file_size         BIGINT        NOT NULL,
    content_hash      VARCHAR(64)   NOT NULL,
    token_count       INTEGER       NOT NULL,
    line_count        INTEGER       NOT NULL,
    warnings          TEXT          NOT NULL DEFAULT '[]',
    CONSTRAINT uq_source_file_ordinal UNIQUE (analysis_id, ordinal),
    CONSTRAINT uq_source_file_display_name UNIQUE (analysis_id, display_name),
    CONSTRAINT ck_source_file_size CHECK (file_size >= 0 AND token_count >= 0 AND line_count >= 0)
);

CREATE INDEX ix_source_file_analysis ON source_file (analysis_id);
CREATE INDEX ix_source_file_display_name_lower ON source_file (lower(display_name));

-- Optional retention of source text (codetrace.retention.store-source). Kept in its own table so that
-- listing queries never load file contents and so the data can be purged independently.
CREATE TABLE source_file_content (
    source_file_id UUID PRIMARY KEY REFERENCES source_file (id) ON DELETE CASCADE,
    content        TEXT NOT NULL
);

CREATE TABLE comparison_result (
    id                       UUID PRIMARY KEY,
    analysis_id              UUID             NOT NULL REFERENCES analysis (id) ON DELETE CASCADE,
    file_a_id                UUID             NOT NULL REFERENCES source_file (id) ON DELETE CASCADE,
    file_b_id                UUID             NOT NULL REFERENCES source_file (id) ON DELETE CASCADE,
    jaccard_score            DOUBLE PRECISION NOT NULL,
    winnowing_score          DOUBLE PRECISION NOT NULL,
    overall_score            DOUBLE PRECISION NOT NULL,
    shared_fingerprint_count INTEGER          NOT NULL,
    region_count             INTEGER          NOT NULL DEFAULT 0,
    result_metadata          TEXT             NOT NULL,
    CONSTRAINT uq_comparison_pair UNIQUE (analysis_id, file_a_id, file_b_id),
    CONSTRAINT ck_comparison_distinct_files CHECK (file_a_id <> file_b_id),
    CONSTRAINT ck_comparison_scores CHECK (
        jaccard_score BETWEEN 0 AND 1 AND winnowing_score BETWEEN 0 AND 1 AND overall_score BETWEEN 0 AND 1
        AND shared_fingerprint_count >= 0 AND region_count >= 0)
);

CREATE INDEX ix_comparison_analysis_score ON comparison_result (analysis_id, overall_score DESC);
CREATE INDEX ix_comparison_overall_score ON comparison_result (overall_score DESC);
CREATE INDEX ix_comparison_file_a ON comparison_result (file_a_id);
CREATE INDEX ix_comparison_file_b ON comparison_result (file_b_id);

CREATE TABLE matching_region (
    id                   UUID PRIMARY KEY,
    comparison_result_id UUID    NOT NULL REFERENCES comparison_result (id) ON DELETE CASCADE,
    file_a_start_offset  INTEGER NOT NULL,
    file_a_end_offset    INTEGER NOT NULL,
    file_b_start_offset  INTEGER NOT NULL,
    file_b_end_offset    INTEGER NOT NULL,
    file_a_start_line    INTEGER NOT NULL,
    file_a_end_line      INTEGER NOT NULL,
    file_b_start_line    INTEGER NOT NULL,
    file_b_end_line      INTEGER NOT NULL,
    token_length         INTEGER NOT NULL,
    CONSTRAINT ck_region_offsets CHECK (
        file_a_start_offset >= 0 AND file_a_end_offset > file_a_start_offset
        AND file_b_start_offset >= 0 AND file_b_end_offset > file_b_start_offset
        AND file_a_end_line >= file_a_start_line AND file_b_end_line >= file_b_start_line
        AND token_length > 0)
);

CREATE INDEX ix_matching_region_comparison ON matching_region (comparison_result_id);
