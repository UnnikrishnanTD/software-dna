-- Software DNA :: history
--
-- Contributor identity is stored as a display name plus a hash of the commit
-- email. The hash is enough to count distinct authors and attribute files
-- without persisting personal contact details.

CREATE TABLE contributor (
    id                UUID PRIMARY KEY,
    analysis_id       UUID NOT NULL REFERENCES analysis (id) ON DELETE CASCADE,
    display_name      VARCHAR(255) NOT NULL,
    email_hash        CHAR(64)     NOT NULL,
    commit_count      INTEGER NOT NULL DEFAULT 0,
    additions         BIGINT  NOT NULL DEFAULT 0,
    deletions         BIGINT  NOT NULL DEFAULT 0,
    files_touched     INTEGER NOT NULL DEFAULT 0,
    first_commit_at   TIMESTAMPTZ,
    last_commit_at    TIMESTAMPTZ,
    CONSTRAINT uq_contributor_per_analysis UNIQUE (analysis_id, email_hash)
);

CREATE INDEX idx_contributor_analysis ON contributor (analysis_id, commit_count DESC);

CREATE TABLE evolution_point (
    id             UUID PRIMARY KEY,
    analysis_id    UUID NOT NULL REFERENCES analysis (id) ON DELETE CASCADE,
    year           SMALLINT NOT NULL,
    overall        NUMERIC(5, 2),
    contributors   INTEGER NOT NULL DEFAULT 0,
    commits        INTEGER NOT NULL DEFAULT 0,
    lines_of_code  INTEGER NOT NULL DEFAULT 0,
    modules        INTEGER NOT NULL DEFAULT 0,
    hotspots       INTEGER NOT NULL DEFAULT 0,
    test_coverage  NUMERIC(5, 2),
    -- dimension key -> score at the end of that year, for dimensions that
    -- can be derived from history alone.
    scores         JSONB NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT uq_evolution_point_per_analysis UNIQUE (analysis_id, year)
);

CREATE INDEX idx_evolution_point_analysis ON evolution_point (analysis_id, year);

CREATE TABLE evolution_milestone (
    id              UUID PRIMARY KEY,
    analysis_id     UUID NOT NULL REFERENCES analysis (id) ON DELETE CASCADE,
    year            SMALLINT NOT NULL,
    offset_in_year  NUMERIC(4, 3) NOT NULL DEFAULT 0.5,
    kind            VARCHAR(32)  NOT NULL,
    title           VARCHAR(255) NOT NULL,
    description     TEXT         NOT NULL,
    impact          NUMERIC(6, 2) NOT NULL DEFAULT 0,
    evidence        JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX idx_milestone_analysis ON evolution_milestone (analysis_id, year);
