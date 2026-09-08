-- Software DNA :: files and the structure they form

CREATE TABLE code_file (
    id                  UUID PRIMARY KEY,
    analysis_id         UUID NOT NULL REFERENCES analysis (id) ON DELETE CASCADE,
    path                VARCHAR(1024) NOT NULL,
    name                VARCHAR(255)  NOT NULL,
    extension           VARCHAR(32),
    language            VARCHAR(64),
    is_test             BOOLEAN NOT NULL DEFAULT FALSE,
    is_generated        BOOLEAN NOT NULL DEFAULT FALSE,
    size_bytes          BIGINT  NOT NULL DEFAULT 0,
    lines_total         INTEGER NOT NULL DEFAULT 0,
    lines_of_code       INTEGER NOT NULL DEFAULT 0,
    lines_comment       INTEGER NOT NULL DEFAULT 0,
    lines_blank         INTEGER NOT NULL DEFAULT 0,
    complexity_score    INTEGER,
    complexity_band     VARCHAR(16),
    declaration_count   INTEGER NOT NULL DEFAULT 0,   -- classes, interfaces, records
    function_count      INTEGER NOT NULL DEFAULT 0,
    import_count        INTEGER NOT NULL DEFAULT 0,
    max_nesting_depth   INTEGER NOT NULL DEFAULT 0,
    dependents          INTEGER NOT NULL DEFAULT 0,
    change_count        INTEGER NOT NULL DEFAULT 0,
    bug_fix_count       INTEGER NOT NULL DEFAULT 0,
    contributor_count   INTEGER NOT NULL DEFAULT 0,
    last_changed_at     TIMESTAMPTZ,
    primary_author      VARCHAR(255),
    coverage            NUMERIC(5, 2),                -- NULL unless a report was found.
    risk                VARCHAR(16),
    health              NUMERIC(5, 2),
    architecture_node_key VARCHAR(512),
    CONSTRAINT uq_file_per_analysis UNIQUE (analysis_id, path)
);

CREATE INDEX idx_file_analysis   ON code_file (analysis_id);
-- The codebase explorer walks the tree in path order.
CREATE INDEX idx_file_path       ON code_file (analysis_id, path);
-- The "lowest health" and hotspot rankings sort on these.
CREATE INDEX idx_file_health     ON code_file (analysis_id, health);
CREATE INDEX idx_file_language   ON code_file (analysis_id, language);

CREATE TABLE architecture_node (
    id               UUID PRIMARY KEY,
    analysis_id      UUID NOT NULL REFERENCES analysis (id) ON DELETE CASCADE,
    -- Stable, human-readable key used as the id in API responses and links.
    node_key         VARCHAR(512) NOT NULL,
    name             VARCHAR(255) NOT NULL,
    kind             VARCHAR(32)  NOT NULL,
    layer            VARCHAR(32)  NOT NULL,
    path             VARCHAR(1024) NOT NULL,
    language         VARCHAR(64),
    complexity_score INTEGER NOT NULL DEFAULT 0,
    complexity_band  VARCHAR(16) NOT NULL,
    lines_of_code    INTEGER NOT NULL DEFAULT 0,
    file_count       INTEGER NOT NULL DEFAULT 0,
    coverage         NUMERIC(5, 2),
    risk             VARCHAR(16) NOT NULL,
    fan_in           INTEGER NOT NULL DEFAULT 0,
    fan_out          INTEGER NOT NULL DEFAULT 0,
    description      VARCHAR(1024),
    CONSTRAINT uq_node_per_analysis UNIQUE (analysis_id, node_key)
);

CREATE INDEX idx_node_analysis ON architecture_node (analysis_id);
CREATE INDEX idx_node_layer    ON architecture_node (analysis_id, layer);

CREATE TABLE architecture_edge (
    id            UUID PRIMARY KEY,
    analysis_id   UUID NOT NULL REFERENCES analysis (id) ON DELETE CASCADE,
    source_key    VARCHAR(512) NOT NULL,
    target_key    VARCHAR(512) NOT NULL,
    kind          VARCHAR(32)  NOT NULL,
    weight        INTEGER      NOT NULL DEFAULT 1,
    CONSTRAINT uq_edge_per_analysis UNIQUE (analysis_id, source_key, target_key, kind)
);

CREATE INDEX idx_edge_analysis ON architecture_edge (analysis_id);
CREATE INDEX idx_edge_source   ON architecture_edge (analysis_id, source_key);
CREATE INDEX idx_edge_target   ON architecture_edge (analysis_id, target_key);

CREATE TABLE architecture_cycle (
    id            UUID PRIMARY KEY,
    analysis_id   UUID NOT NULL REFERENCES analysis (id) ON DELETE CASCADE,
    ordinal       SMALLINT NOT NULL,
    node_keys     JSONB    NOT NULL
);

CREATE INDEX idx_cycle_analysis ON architecture_cycle (analysis_id, ordinal);
