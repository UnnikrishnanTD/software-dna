-- Software DNA :: scores and the measurements behind them
--
-- Every dimension score must be reconstructible from the metric rows that
-- produced it. `metric` is the audit trail: if a value could not be measured
-- it is stored with available = FALSE rather than defaulted to zero, so a
-- missing measurement can never masquerade as a bad one.

CREATE TABLE dimension_score (
    id            UUID PRIMARY KEY,
    analysis_id   UUID NOT NULL REFERENCES analysis (id) ON DELETE CASCADE,
    dimension     VARCHAR(32) NOT NULL,
    score         NUMERIC(5, 2),            -- NULL when the dimension is unavailable.
    verdict       VARCHAR(32),
    -- 0-100. Falls as inputs go missing, so partial evidence is visible.
    confidence    NUMERIC(5, 2) NOT NULL,
    weight        NUMERIC(4, 3) NOT NULL,
    delta         NUMERIC(6, 2) NOT NULL DEFAULT 0,
    headline      VARCHAR(512),
    summary       TEXT,
    strengths     JSONB NOT NULL DEFAULT '[]'::jsonb,
    watch_items   JSONB NOT NULL DEFAULT '[]'::jsonb,
    -- metric key -> value that justified each statement above.
    evidence      JSONB NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT uq_dimension_per_analysis UNIQUE (analysis_id, dimension)
);

CREATE INDEX idx_dimension_analysis ON dimension_score (analysis_id);

CREATE TABLE metric (
    id            UUID PRIMARY KEY,
    analysis_id   UUID NOT NULL REFERENCES analysis (id) ON DELETE CASCADE,
    dimension     VARCHAR(32),
    metric_key    VARCHAR(128) NOT NULL,
    value         NUMERIC(18, 4),
    unit          VARCHAR(32),
    available     BOOLEAN NOT NULL DEFAULT TRUE,
    -- Why a metric is unavailable, e.g. "coverage report not present".
    unavailable_reason VARCHAR(255),
    confidence    NUMERIC(5, 2) NOT NULL DEFAULT 100,
    CONSTRAINT uq_metric_per_analysis UNIQUE (analysis_id, metric_key)
);

CREATE INDEX idx_metric_analysis  ON metric (analysis_id);
CREATE INDEX idx_metric_dimension ON metric (analysis_id, dimension);
