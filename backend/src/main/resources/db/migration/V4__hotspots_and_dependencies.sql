-- Software DNA :: risk concentration and the supply chain

CREATE TABLE hotspot (
    id                    UUID PRIMARY KEY,
    analysis_id           UUID NOT NULL REFERENCES analysis (id) ON DELETE CASCADE,
    code_file_id          UUID REFERENCES code_file (id) ON DELETE SET NULL,
    name                  VARCHAR(255)  NOT NULL,
    path                  VARCHAR(1024) NOT NULL,
    change_count          INTEGER NOT NULL DEFAULT 0,
    complexity_score      INTEGER NOT NULL DEFAULT 0,
    complexity_band       VARCHAR(16) NOT NULL,
    dependency_count      INTEGER NOT NULL DEFAULT 0,
    bug_fix_count         INTEGER NOT NULL DEFAULT 0,
    lines_of_code         INTEGER NOT NULL DEFAULT 0,
    coverage              NUMERIC(5, 2),
    contributor_count     INTEGER NOT NULL DEFAULT 0,
    risk_score            NUMERIC(5, 2) NOT NULL,
    severity              VARCHAR(16)   NOT NULL,
    rationale             TEXT          NOT NULL,
    recommendation        TEXT          NOT NULL,
    architecture_node_key VARCHAR(512)
);

CREATE INDEX idx_hotspot_analysis ON hotspot (analysis_id, risk_score DESC);

CREATE TABLE dependency (
    id             UUID PRIMARY KEY,
    analysis_id    UUID NOT NULL REFERENCES analysis (id) ON DELETE CASCADE,
    name           VARCHAR(512) NOT NULL,
    ecosystem      VARCHAR(32)  NOT NULL,
    version        VARCHAR(128) NOT NULL,
    -- All NULL unless a registry lookup actually ran. A null latest_version
    -- means "not checked", never "up to date".
    latest_version VARCHAR(128),
    status         VARCHAR(32)  NOT NULL,
    license        VARCHAR(128),
    direct         BOOLEAN NOT NULL DEFAULT TRUE,
    scope          VARCHAR(32),
    used_by        INTEGER NOT NULL DEFAULT 0,
    size_kb        INTEGER,
    risk           VARCHAR(16) NOT NULL,
    -- NULL means no vulnerability source was consulted; 0 means one was and
    -- found nothing. These must never be conflated.
    advisory_count INTEGER,
    note           VARCHAR(1024),
    manifest_path  VARCHAR(1024),
    CONSTRAINT uq_dependency_per_analysis UNIQUE (analysis_id, ecosystem, name, version)
);

CREATE INDEX idx_dependency_analysis ON dependency (analysis_id);
CREATE INDEX idx_dependency_status   ON dependency (analysis_id, status);

CREATE TABLE technology (
    id               UUID PRIMARY KEY,
    analysis_id      UUID NOT NULL REFERENCES analysis (id) ON DELETE CASCADE,
    name             VARCHAR(128) NOT NULL,
    category         VARCHAR(32)  NOT NULL,
    share            NUMERIC(5, 2) NOT NULL,
    lines_of_code    INTEGER NOT NULL DEFAULT 0,
    version          VARCHAR(64),
    dependency_count INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uq_technology_per_analysis UNIQUE (analysis_id, name)
);

CREATE INDEX idx_technology_analysis ON technology (analysis_id, share DESC);
