-- Software DNA :: findings
--
-- An issue always carries the evidence that produced it, so a reader can
-- check the claim rather than trust it.

CREATE TABLE issue (
    id             UUID PRIMARY KEY,
    analysis_id    UUID NOT NULL REFERENCES analysis (id) ON DELETE CASCADE,
    code           VARCHAR(64)  NOT NULL,
    severity       VARCHAR(16)  NOT NULL,
    category       VARCHAR(32)  NOT NULL,
    dimension      VARCHAR(32),
    title          VARCHAR(255) NOT NULL,
    description    TEXT         NOT NULL,
    evidence       JSONB NOT NULL DEFAULT '{}'::jsonb,
    affected_paths JSONB NOT NULL DEFAULT '[]'::jsonb,
    related_nodes  JSONB NOT NULL DEFAULT '[]'::jsonb,
    recommendation TEXT,
    confidence     NUMERIC(5, 2) NOT NULL DEFAULT 100
);

CREATE INDEX idx_issue_analysis ON issue (analysis_id, severity);

CREATE TABLE recommendation (
    id             UUID PRIMARY KEY,
    analysis_id    UUID NOT NULL REFERENCES analysis (id) ON DELETE CASCADE,
    rank           SMALLINT NOT NULL,
    title          VARCHAR(255) NOT NULL,
    detail         TEXT         NOT NULL,
    effort         VARCHAR(16)  NOT NULL,
    expected_gain  NUMERIC(5, 2) NOT NULL,
    dimension      VARCHAR(32)  NOT NULL,
    issue_id       UUID REFERENCES issue (id) ON DELETE SET NULL,
    CONSTRAINT uq_recommendation_rank UNIQUE (analysis_id, rank)
);

CREATE INDEX idx_recommendation_analysis ON recommendation (analysis_id, rank);
