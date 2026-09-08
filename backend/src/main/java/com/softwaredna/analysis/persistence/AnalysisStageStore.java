package com.softwaredna.analysis.persistence;

import com.softwaredna.common.domain.AnalysisStageId;
import com.softwaredna.common.domain.StageStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Reads and writes the eight scan stages.
 *
 * <p>The stage ids are fixed by the frontend, which renders one row per stage
 * while a scan is in flight. Seeding all eight up front means the client sees
 * the full sequence from its first poll, with the pending ones dimmed, rather
 * than watching rows appear one at a time.
 */
@Repository
public class AnalysisStageStore {

    /** Labels and detail text, in execution order. */
    private record StageTemplate(AnalysisStageId id, String label, String detail) {
    }

    private static final List<StageTemplate> TEMPLATES = List.of(
            new StageTemplate(AnalysisStageId.CONNECT,
                    "Repository connected", "Resolving default branch"),
            new StageTemplate(AnalysisStageId.TECHNOLOGIES,
                    "Detecting technologies", "Fingerprinting manifests and sources"),
            new StageTemplate(AnalysisStageId.ARCHITECTURE,
                    "Mapping architecture", "Resolving imports across the tree"),
            new StageTemplate(AnalysisStageId.DEPENDENCIES,
                    "Analysing dependencies", "Reading dependency manifests"),
            new StageTemplate(AnalysisStageId.COMPLEXITY,
                    "Inspecting complexity", "Parsing source files"),
            new StageTemplate(AnalysisStageId.TESTS,
                    "Examining test structure", "Correlating suites with sources"),
            new StageTemplate(AnalysisStageId.HISTORY,
                    "Reading repository history", "Walking the commit graph"),
            new StageTemplate(AnalysisStageId.SYNTHESIS,
                    "Generating Software DNA", "Synthesising eight dimensions"));

    public record StageRow(
            AnalysisStageId id, String label, String detail,
            StageStatus status, String result) {
    }

    private final JdbcTemplate jdbc;

    public AnalysisStageStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void seed(UUID analysisId) {
        jdbc.batchUpdate("""
                        INSERT INTO analysis_stage
                            (id, analysis_id, stage_id, ordinal, label, detail, status)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                TEMPLATES.stream().map(template -> new Object[]{
                        UUID.randomUUID(), analysisId, template.id().wireValue(),
                        TEMPLATES.indexOf(template), template.label(), template.detail(),
                        StageStatus.PENDING.wireValue()
                }).toList());
    }

    public void markRunning(UUID analysisId, AnalysisStageId stage, String detail) {
        jdbc.update("""
                        UPDATE analysis_stage
                        SET status = ?, started_at = ?, detail = COALESCE(?, detail)
                        WHERE analysis_id = ? AND stage_id = ?
                        """,
                StageStatus.RUNNING.wireValue(), Timestamp.from(Instant.now()),
                detail, analysisId, stage.wireValue());
    }

    public void markComplete(UUID analysisId, AnalysisStageId stage, String result) {
        jdbc.update("""
                        UPDATE analysis_stage
                        SET status = ?, finished_at = ?, result = ?
                        WHERE analysis_id = ? AND stage_id = ?
                        """,
                StageStatus.COMPLETE.wireValue(), Timestamp.from(Instant.now()),
                result, analysisId, stage.wireValue());
    }

    /** Marks the stage that was running as failed; the rest stay pending. */
    public void markFailed(UUID analysisId, AnalysisStageId stage) {
        if (stage == null) {
            return;
        }
        jdbc.update("""
                        UPDATE analysis_stage
                        SET status = ?, finished_at = ?
                        WHERE analysis_id = ? AND stage_id = ?
                        """,
                StageStatus.FAILED.wireValue(), Timestamp.from(Instant.now()),
                analysisId, stage.wireValue());
    }

    public List<StageRow> findByAnalysis(UUID analysisId) {
        return jdbc.query("""
                        SELECT stage_id, label, detail, status, result
                        FROM analysis_stage
                        WHERE analysis_id = ?
                        ORDER BY ordinal
                        """,
                (rs, rowNum) -> new StageRow(
                        AnalysisStageId.fromWire(rs.getString("stage_id")),
                        rs.getString("label"),
                        rs.getString("detail"),
                        StageStatus.fromWire(rs.getString("status")),
                        rs.getString("result")),
                analysisId);
    }

    /** Progress derived from completed stages, not from a timer. */
    public double progressOf(UUID analysisId) {
        Integer complete = jdbc.queryForObject("""
                        SELECT count(*) FROM analysis_stage
                        WHERE analysis_id = ? AND status = ?
                        """,
                Integer.class, analysisId, StageStatus.COMPLETE.wireValue());
        return complete == null ? 0 : complete / (double) TEMPLATES.size();
    }

    public static int stageCount() {
        return TEMPLATES.size();
    }
}
