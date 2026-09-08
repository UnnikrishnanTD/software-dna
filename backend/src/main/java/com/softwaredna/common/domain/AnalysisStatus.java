package com.softwaredna.common.domain;

/**
 * Backend lifecycle of an analysis.
 *
 * This is a different axis from {@link AnalysisStageId}: the status says
 * whether the run is alive, the stage says how far through it is.
 */
public enum AnalysisStatus {
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }
}
