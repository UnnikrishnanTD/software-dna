package com.softwaredna.analysis.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.softwaredna.analysis.engine.AnalysisContext;
import com.softwaredna.analysis.engine.DimensionResult;
import com.softwaredna.analysis.engine.DnaProfile;
import com.softwaredna.analysis.engine.Measurement;
import com.softwaredna.analysis.findings.FindingsGenerator;
import com.softwaredna.architecture.ArchitectureGraph;
import com.softwaredna.codebase.model.ScannedFile;
import com.softwaredna.common.domain.ComplexityBand;
import com.softwaredna.common.domain.RiskLevel;
import com.softwaredna.common.exception.ApiException;
import com.softwaredna.common.exception.ErrorCode;
import com.softwaredna.dependency.TechnologyProfiler;
import com.softwaredna.dependency.model.ResolvedDependency;
import com.softwaredna.evolution.EvolutionTimelineBuilder;
import com.softwaredna.evolution.git.GitHistory;
import com.softwaredna.hotspot.HotspotCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Writes an analysis result to the database.
 *
 * <p>Deliberately JDBC rather than JPA. A single analysis of a large
 * repository produces tens of thousands of rows that are written once and
 * never mutated. Pushing those through the persistence context would mean
 * holding every one in memory as a managed entity, generating a statement per
 * row, and paying for dirty checking that can never fire. Batched JDBC writes
 * them in a handful of round trips at constant memory.
 *
 * <p>JPA is still used for the two aggregate roots, which do have a lifecycle.
 * The split is by access pattern, not by preference.
 */
@Repository
public class AnalysisResultWriter {

    private static final Logger log = LoggerFactory.getLogger(AnalysisResultWriter.class);

    /** Rows per batch. Large enough to amortise the round trip, small enough
     *  that a failure does not have to be re-sent in full. */
    private static final int BATCH_SIZE = 500;

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public AnalysisResultWriter(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /**
     * Everything one completed analysis produced.
     */
    public record AnalysisOutput(
            AnalysisContext context,
            DnaProfile profile,
            List<HotspotCalculator.Hotspot> hotspots,
            List<TechnologyProfiler.Technology> technologies,
            EvolutionTimelineBuilder.Timeline timeline,
            FindingsGenerator.Findings findings,
            Map<com.softwaredna.common.domain.DnaDimension, Double> weights,
            Map<com.softwaredna.common.domain.DnaDimension, Double> deltas
    ) {
    }

    @Transactional
    public void write(UUID analysisId, AnalysisOutput output) {
        long startedAt = System.currentTimeMillis();

        writeDimensions(analysisId, output);
        writeMetrics(analysisId, output.profile());
        writeFiles(analysisId, output.context());
        writeGraph(analysisId, output.context().graph());
        writeHotspots(analysisId, output.hotspots());
        writeDependencies(analysisId, output.context().manifests().dependencies());
        writeTechnologies(analysisId, output.technologies());
        writeContributors(analysisId, output.context().history());
        writeEvolution(analysisId, output.timeline());
        writeFindings(analysisId, output.findings());

        log.info("Persisted analysis {} in {} ms", analysisId,
                System.currentTimeMillis() - startedAt);
    }

    // ---- Scores and measurements ------------------------------------------

    private void writeDimensions(UUID analysisId, AnalysisOutput output) {
        batch("""
                INSERT INTO dimension_score
                    (id, analysis_id, dimension, score, verdict, confidence, weight, delta,
                     headline, summary, strengths, watch_items, evidence)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb)
                """,
                output.profile().dimensions(),
                dimension -> new Object[]{
                        UUID.randomUUID(), analysisId,
                        dimension.dimension().wireValue(),
                        dimension.score(),
                        dimension.score() == null ? null
                                : com.softwaredna.common.domain.HealthVerdict
                                        .forScore(dimension.score()).wireValue(),
                        dimension.confidence(),
                        output.weights().getOrDefault(dimension.dimension(), 0.0),
                        output.deltas().getOrDefault(dimension.dimension(), 0.0),
                        dimension.headline(),
                        dimension.summary(),
                        jsonb(dimension.strengths()),
                        jsonb(dimension.watchItems()),
                        jsonb(dimension.evidence())
                });
    }

    private void writeMetrics(UUID analysisId, DnaProfile profile) {
        record Row(DimensionResult dimension, Measurement measurement) {
        }
        List<Row> rows = profile.dimensions().stream()
                .flatMap(dimension -> dimension.measurements().stream()
                        .map(measurement -> new Row(dimension, measurement)))
                .toList();

        batch("""
                INSERT INTO metric
                    (id, analysis_id, dimension, metric_key, value, unit, available,
                     unavailable_reason, confidence)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (analysis_id, metric_key) DO NOTHING
                """,
                rows,
                row -> new Object[]{
                        UUID.randomUUID(), analysisId,
                        row.dimension().dimension().wireValue(),
                        row.measurement().key(),
                        row.measurement().value(),
                        row.measurement().unit(),
                        row.measurement().available(),
                        row.measurement().unavailableReason(),
                        row.dimension().confidence()
                });
    }

    // ---- Codebase ---------------------------------------------------------

    private void writeFiles(UUID analysisId, AnalysisContext context) {
        Map<String, GitHistory.FileHistory> histories = context.history().fileHistories();
        Map<String, String> nodeOfFile = new java.util.HashMap<>();
        context.graph().filesByNode().forEach((node, paths) ->
                paths.forEach(path -> nodeOfFile.put(path, node)));

        batch("""
                INSERT INTO code_file
                    (id, analysis_id, path, name, extension, language, is_test, is_generated,
                     size_bytes, lines_total, lines_of_code, lines_comment, lines_blank,
                     complexity_score, complexity_band, declaration_count, function_count,
                     import_count, max_nesting_depth, change_count, bug_fix_count,
                     contributor_count, last_changed_at, primary_author, coverage, risk,
                     health, architecture_node_key)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                        ?, ?, ?, ?, ?)
                ON CONFLICT (analysis_id, path) DO NOTHING
                """,
                context.scan().files(),
                file -> {
                    GitHistory.FileHistory history = histories.get(file.path());
                    int changes = history == null ? 0 : history.changes();
                    ComplexityBand band = file.metrics().structuralAnalysis()
                            ? ComplexityBand.forScore(file.metrics().cyclomaticComplexity())
                            : null;
                    Double coverage = context.coverageByPath().get(file.path());
                    RiskLevel risk = riskOf(file, changes, coverage);

                    return new Object[]{
                            UUID.randomUUID(), analysisId, file.path(), file.name(),
                            file.extension(), file.language().displayName(),
                            file.test(), file.generated(), file.sizeBytes(),
                            file.metrics().linesTotal(), file.metrics().linesOfCode(),
                            file.metrics().linesComment(), file.metrics().linesBlank(),
                            file.metrics().structuralAnalysis()
                                    ? file.metrics().cyclomaticComplexity() : null,
                            band == null ? null : band.wireValue(),
                            file.metrics().declarationCount(), file.metrics().functionCount(),
                            file.metrics().importCount(), file.metrics().maxNestingDepth(),
                            changes,
                            history == null ? 0 : history.bugFixCommits(),
                            history == null ? 0 : history.distinctAuthors(),
                            history == null || history.lastChangedAt() == null
                                    ? null : Timestamp.from(history.lastChangedAt()),
                            history == null ? null : history.primaryAuthor(),
                            coverage,
                            risk.wireValue(),
                            healthOf(file, changes, coverage),
                            nodeOfFile.get(file.path())
                    };
                });
    }

    /** File risk from complexity, churn and — only when known — coverage. */
    private RiskLevel riskOf(ScannedFile file, int changes, Double coverage) {
        double complexity = Math.min(file.metrics().cyclomaticComplexity() / 34.0, 1);
        double churn = Math.min(changes / 100.0, 1);
        double coverageGap = coverage == null ? 0 : (100 - coverage) / 100.0;
        double weightSum = coverage == null ? 0.75 : 1.0;
        double score = (complexity * 0.45 + churn * 0.30
                + (coverage == null ? 0 : coverageGap * 0.25)) / weightSum;
        return RiskLevel.forScore(score * 100);
    }

    private double healthOf(ScannedFile file, int changes, Double coverage) {
        double penalty = Math.min(file.metrics().cyclomaticComplexity() / 34.0, 1) * 35
                + Math.min(file.metrics().linesOfCode() / 800.0, 1) * 20
                + Math.min(changes / 100.0, 1) * 15
                + (coverage == null ? 0 : (100 - coverage) * 0.3);
        return Math.max(0, Math.min(100, Math.round(100 - penalty)));
    }

    // ---- Architecture -----------------------------------------------------

    private void writeGraph(UUID analysisId, ArchitectureGraph graph) {
        batch("""
                INSERT INTO architecture_node
                    (id, analysis_id, node_key, name, kind, layer, path, language,
                     complexity_score, complexity_band, lines_of_code, file_count, coverage,
                     risk, fan_in, fan_out, description)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (analysis_id, node_key) DO NOTHING
                """,
                graph.nodes(),
                node -> new Object[]{
                        UUID.randomUUID(), analysisId, node.key(), node.name(),
                        node.kind().wireValue(), node.layer().wireValue(), node.path(),
                        node.language(), node.complexityScore(),
                        node.complexityBand().wireValue(), node.linesOfCode(),
                        node.fileCount(), null, node.risk().wireValue(),
                        node.fanIn(), node.fanOut(), node.description()
                });

        batch("""
                INSERT INTO architecture_edge
                    (id, analysis_id, source_key, target_key, kind, weight)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (analysis_id, source_key, target_key, kind) DO NOTHING
                """,
                graph.edges(),
                edge -> new Object[]{
                        UUID.randomUUID(), analysisId, edge.source(), edge.target(),
                        edge.kind().wireValue(), edge.weight()
                });

        List<List<String>> cycles = graph.cycles();
        batch("""
                INSERT INTO architecture_cycle (id, analysis_id, ordinal, node_keys)
                VALUES (?, ?, ?, ?::jsonb)
                """,
                cycles,
                cycle -> new Object[]{
                        UUID.randomUUID(), analysisId, cycles.indexOf(cycle), jsonb(cycle)
                });
    }

    // ---- Hotspots, dependencies, technologies -----------------------------

    private void writeHotspots(UUID analysisId, List<HotspotCalculator.Hotspot> hotspots) {
        batch("""
                INSERT INTO hotspot
                    (id, analysis_id, name, path, change_count, complexity_score,
                     complexity_band, dependency_count, bug_fix_count, lines_of_code,
                     coverage, contributor_count, risk_score, severity, rationale,
                     recommendation, architecture_node_key)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                hotspots,
                hotspot -> new Object[]{
                        UUID.randomUUID(), analysisId, hotspot.name(), hotspot.path(),
                        hotspot.changes(), hotspot.complexityScore(),
                        hotspot.complexityBand().wireValue(), hotspot.dependencies(),
                        hotspot.bugFixes(), hotspot.linesOfCode(), hotspot.coverage(),
                        hotspot.contributors(), hotspot.riskScore(),
                        hotspot.severity().wireValue(), hotspot.rationale(),
                        hotspot.recommendation(), hotspot.architectureNodeKey()
                });
    }

    private void writeDependencies(UUID analysisId, List<ResolvedDependency> dependencies) {
        batch("""
                INSERT INTO dependency
                    (id, analysis_id, name, ecosystem, version, latest_version, status,
                     license, direct, scope, used_by, size_kb, risk, advisory_count, note,
                     manifest_path)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (analysis_id, ecosystem, name, version) DO NOTHING
                """,
                dependencies,
                dependency -> new Object[]{
                        UUID.randomUUID(), analysisId, dependency.name(),
                        dependency.ecosystem().wireValue(), dependency.version(),
                        dependency.latestVersion(), dependency.status().wireValue(),
                        dependency.license(), dependency.direct(), dependency.scope(),
                        0, null, dependency.risk().wireValue(),
                        dependency.advisoryCount(), dependency.note(),
                        dependency.manifestPath()
                });
    }

    private void writeTechnologies(UUID analysisId,
                                   List<TechnologyProfiler.Technology> technologies) {
        batch("""
                INSERT INTO technology
                    (id, analysis_id, name, category, share, lines_of_code, version,
                     dependency_count)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (analysis_id, name) DO NOTHING
                """,
                technologies,
                technology -> new Object[]{
                        UUID.randomUUID(), analysisId, technology.name(),
                        technology.category().wireValue(), technology.share(),
                        technology.linesOfCode(), technology.version(),
                        technology.dependencyCount()
                });
    }

    // ---- History ----------------------------------------------------------

    private void writeContributors(UUID analysisId, GitHistory history) {
        batch("""
                INSERT INTO contributor
                    (id, analysis_id, display_name, email_hash, commit_count, additions,
                     deletions, files_touched, first_commit_at, last_commit_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (analysis_id, email_hash) DO NOTHING
                """,
                history.authors(),
                author -> new Object[]{
                        UUID.randomUUID(), analysisId, author.displayName(),
                        author.emailHash(), author.commits(), 0L, 0L,
                        author.filesTouched(),
                        author.firstCommitAt() == null
                                ? null : Timestamp.from(author.firstCommitAt()),
                        author.lastCommitAt() == null
                                ? null : Timestamp.from(author.lastCommitAt())
                });
    }

    private void writeEvolution(UUID analysisId, EvolutionTimelineBuilder.Timeline timeline) {
        batch("""
                INSERT INTO evolution_point
                    (id, analysis_id, year, overall, contributors, commits, lines_of_code,
                     modules, hotspots, test_coverage, scores)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                ON CONFLICT (analysis_id, year) DO NOTHING
                """,
                timeline.points(),
                point -> new Object[]{
                        UUID.randomUUID(), analysisId, point.year(), point.overall(),
                        point.contributors(), point.commits(), point.linesOfCode(),
                        point.modules(), point.hotspots(), point.testCoverage(),
                        jsonb(point.scores())
                });

        batch("""
                INSERT INTO evolution_milestone
                    (id, analysis_id, year, offset_in_year, kind, title, description,
                     impact, evidence)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                """,
                timeline.milestones(),
                milestone -> new Object[]{
                        UUID.randomUUID(), analysisId, milestone.year(),
                        milestone.offsetInYear(), milestone.kind().wireValue(),
                        milestone.title(), milestone.description(), milestone.impact(),
                        jsonb(milestone.evidence())
                });
    }

    // ---- Findings ---------------------------------------------------------

    private void writeFindings(UUID analysisId, FindingsGenerator.Findings findings) {
        batch("""
                INSERT INTO issue
                    (id, analysis_id, code, severity, category, dimension, title,
                     description, evidence, affected_paths, related_nodes, recommendation,
                     confidence)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?)
                """,
                findings.issues(),
                issue -> new Object[]{
                        UUID.randomUUID(), analysisId, issue.code(),
                        issue.severity().wireValue(), issue.category(),
                        issue.dimension().wireValue(), issue.title(), issue.description(),
                        jsonb(issue.evidence()), jsonb(issue.affectedPaths()),
                        jsonb(issue.relatedNodes()), issue.recommendation(),
                        issue.confidence()
                });

        batch("""
                INSERT INTO recommendation
                    (id, analysis_id, rank, title, detail, effort, expected_gain, dimension)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (analysis_id, rank) DO NOTHING
                """,
                findings.recommendations(),
                recommendation -> new Object[]{
                        UUID.randomUUID(), analysisId, recommendation.rank(),
                        recommendation.title(), recommendation.detail(),
                        recommendation.effort().wireValue(),
                        recommendation.expectedGain(),
                        recommendation.dimension().wireValue()
                });
    }

    // ---- Plumbing ---------------------------------------------------------

    private <T> void batch(String sql, List<T> items,
                           java.util.function.Function<T, Object[]> toArguments) {
        if (items.isEmpty()) {
            return;
        }
        for (int start = 0; start < items.size(); start += BATCH_SIZE) {
            List<T> chunk = items.subList(start, Math.min(start + BATCH_SIZE, items.size()));
            jdbc.batchUpdate(sql, chunk.stream().map(toArguments).toList());
        }
    }

    /**
     * Serialises a value for a {@code jsonb} column.
     *
     * <p>Returns a plain String rather than a driver-specific type; the SQL
     * casts it with {@code ::jsonb}. That keeps the PostgreSQL driver a runtime
     * dependency instead of a compile-time one, so the persistence code carries
     * no import from a specific database vendor.
     */
    private String jsonb(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new ApiException(ErrorCode.ANALYSIS_FAILED,
                    "Could not serialise analysis output.", e);
        }
    }

    /** Removes every row belonging to an analysis. Cascades handle the rest. */
    @Transactional
    public void deleteResults(UUID analysisId) {
        for (String table : List.of("dimension_score", "metric", "code_file",
                "architecture_node", "architecture_edge", "architecture_cycle",
                "hotspot", "dependency", "technology", "contributor",
                "evolution_point", "evolution_milestone", "recommendation", "issue")) {
            jdbc.update("DELETE FROM " + table + " WHERE analysis_id = ?", analysisId);
        }
    }

    /** Recorded so a re-run of the same repository can report a delta. */
    public Instant now() {
        return Instant.now();
    }
}
