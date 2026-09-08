-- Software DNA :: core lifecycle
--
-- A repository is analysed many times. Each analysis is one row that is
-- created at submission and filled in as the pipeline progresses, so the id
-- returned by POST is the id the client polls and later reads results from.
-- A separate job table would be a 1:1 with no independent lifecycle.

CREATE TABLE repository (
    id                  UUID PRIMARY KEY,
    provider            VARCHAR(32)  NOT NULL,
    owner               VARCHAR(255) NOT NULL,
    name                VARCHAR(255) NOT NULL,
    full_name           VARCHAR(511) NOT NULL,
    url                 VARCHAR(1024) NOT NULL,
    default_branch      VARCHAR(255) NOT NULL,
    description         TEXT,
    primary_language    VARCHAR(64),
    stars               INTEGER      NOT NULL DEFAULT 0,
    forks               INTEGER      NOT NULL DEFAULT 0,
    remote_created_at   TIMESTAMPTZ,
    last_commit_at      TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_repository_identity UNIQUE (provider, owner, name)
);

CREATE TABLE analysis (
    id                    UUID PRIMARY KEY,
    repository_id         UUID NOT NULL REFERENCES repository (id) ON DELETE CASCADE,

    -- Exactly what the caller submitted, kept for traceability.
    requested_url         VARCHAR(1024) NOT NULL,
    requested_ref         VARCHAR(255),
    resolved_commit_sha   VARCHAR(64),

    status                VARCHAR(32) NOT NULL,
    current_stage         VARCHAR(32),
    progress              NUMERIC(5, 4) NOT NULL DEFAULT 0,
    status_message        VARCHAR(512),

    error_code            VARCHAR(64),
    error_message         VARCHAR(1024),

    queued_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at            TIMESTAMPTZ,
    finished_at           TIMESTAMPTZ,
    duration_ms           BIGINT,

    -- Headline counts, denormalised because every screen reads them.
    total_files           INTEGER,
    total_lines           INTEGER,
    total_modules         INTEGER,
    total_services        INTEGER,
    total_components      INTEGER,
    total_commits         INTEGER,
    total_contributors    INTEGER,
    test_coverage         NUMERIC(5, 2),          -- NULL when not measurable.
    coverage_source       VARCHAR(32),            -- e.g. JACOCO, LCOV, or NULL.

    overall_score         NUMERIC(5, 2),
    overall_verdict       VARCHAR(32),
    percentile            NUMERIC(5, 2),          -- NULL until a corpus exists.

    -- Dimensions that could not be computed, surfaced to the UI verbatim.
    incomplete_dimensions JSONB NOT NULL DEFAULT '[]'::jsonb,

    CONSTRAINT ck_analysis_progress CHECK (progress >= 0 AND progress <= 1)
);

CREATE INDEX idx_analysis_repository ON analysis (repository_id, queued_at DESC);
CREATE INDEX idx_analysis_status     ON analysis (status) WHERE status IN ('QUEUED', 'RUNNING');
-- Drives the analyses list, which shows completed analyses newest first.
CREATE INDEX idx_analysis_completed  ON analysis (finished_at DESC) WHERE status = 'COMPLETED';

CREATE TABLE analysis_stage (
    id            UUID PRIMARY KEY,
    analysis_id   UUID NOT NULL REFERENCES analysis (id) ON DELETE CASCADE,
    -- Matches the stage ids the frontend already renders.
    stage_id      VARCHAR(32) NOT NULL,
    ordinal       SMALLINT    NOT NULL,
    label         VARCHAR(128) NOT NULL,
    detail        VARCHAR(255) NOT NULL,
    status        VARCHAR(16)  NOT NULL,
    result        VARCHAR(255),
    started_at    TIMESTAMPTZ,
    finished_at   TIMESTAMPTZ,
    CONSTRAINT uq_stage_per_analysis UNIQUE (analysis_id, stage_id)
);

CREATE INDEX idx_stage_analysis ON analysis_stage (analysis_id, ordinal);
