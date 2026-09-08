package com.softwaredna.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.softwaredna.analysis.entity.AnalysisEntity;
import com.softwaredna.analysis.entity.AnalysisJpaRepository;
import com.softwaredna.api.dto.AnalysisDtos;
import com.softwaredna.common.domain.AnalysisStatus;
import com.softwaredna.common.domain.ArchitectureEdgeKind;
import com.softwaredna.common.domain.ArchitectureLayer;
import com.softwaredna.common.domain.ArchitectureNodeKind;
import com.softwaredna.common.domain.ComplexityBand;
import com.softwaredna.common.domain.DependencyEcosystem;
import com.softwaredna.common.domain.DependencyStatus;
import com.softwaredna.common.domain.DnaDimension;
import com.softwaredna.common.domain.Effort;
import com.softwaredna.common.domain.HealthVerdict;
import com.softwaredna.common.domain.InsightKind;
import com.softwaredna.common.domain.MilestoneKind;
import com.softwaredna.common.domain.RiskLevel;
import com.softwaredna.common.exception.ApiException;
import com.softwaredna.common.exception.ErrorCode;
import com.softwaredna.repository.entity.RepositoryEntity;
import com.softwaredna.repository.entity.RepositoryJpaRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Assembles stored analysis rows into the aggregate the frontend renders.
 *
 * <p>Reads are explicit queries into DTOs rather than entity graph traversal.
 * That removes N+1 by construction — each collection is one query, and there
 * is no lazy proxy that could trigger another — and it means no JPA entity is
 * ever serialised to a client.
 */
@Service
public class AnalysisReadService {

    /** Questions offered as starting points; every one is answerable from the data. */
    private static final List<AnalysisDtos.DoctorPromptDto> DOCTOR_PROMPTS = List.of(
            new AnalysisDtos.DoctorPromptDto("p-architecture",
                    "Why is my architecture score not higher?", "diagnose"),
            new AnalysisDtos.DoctorPromptDto("p-first", "What should I fix first?", "prioritise"),
            new AnalysisDtos.DoctorPromptDto("p-risk",
                    "Which files are the biggest risk?", "diagnose"),
            new AnalysisDtos.DoctorPromptDto("p-graph",
                    "Explain this dependency graph.", "explain"),
            new AnalysisDtos.DoctorPromptDto("p-history",
                    "What changed over the last year?", "history"),
            new AnalysisDtos.DoctorPromptDto("p-testing",
                    "How bad is the testing situation really?", "diagnose"),
            new AnalysisDtos.DoctorPromptDto("p-security",
                    "Is there anything urgent on security?", "diagnose"));

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final AnalysisJpaRepository analyses;
    private final RepositoryJpaRepository repositories;

    public AnalysisReadService(JdbcTemplate jdbc, ObjectMapper json,
                               AnalysisJpaRepository analyses,
                               RepositoryJpaRepository repositories) {
        this.jdbc = jdbc;
        this.json = json;
        this.analyses = analyses;
        this.repositories = repositories;
    }

    /**
     * Completed analyses, one per repository.
     *
     * <p>A repository re-analysed several times has one row per run, but the
     * frontend's list and comparison picker should offer each repository
     * once. Rows already arrive newest-first, so keeping the first one seen
     * per repository keeps the latest run and drops the rest.
     */
    @Transactional(readOnly = true)
    public List<AnalysisDtos.AnalysisSummary> listCompleted() {
        java.util.Set<java.util.UUID> seenRepositories = new java.util.HashSet<>();
        return analyses.findByStatusOrderByFinishedAtDesc(AnalysisStatus.COMPLETED).stream()
                .filter(analysis -> seenRepositories.add(analysis.getRepositoryId()))
                .map(this::toSummary)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Transactional(readOnly = true)
    public AnalysisDtos.RepositoryAnalysisDto getAnalysis(UUID analysisId) {
        AnalysisEntity analysis = analyses.findById(analysisId).orElseThrow(
                () -> new ApiException(ErrorCode.ANALYSIS_NOT_FOUND,
                        "No analysis exists with id " + analysisId + "."));

        if (analysis.getStatus() != AnalysisStatus.COMPLETED) {
            throw new ApiException(ErrorCode.ANALYSIS_NOT_COMPLETE,
                    "That analysis is %s and has no results yet."
                            .formatted(analysis.getStatus().name().toLowerCase()));
        }

        RepositoryEntity repository = repositories.findById(analysis.getRepositoryId())
                .orElseThrow(() -> new ApiException(ErrorCode.REPOSITORY_NOT_FOUND,
                        "The repository for that analysis is missing."));

        return new AnalysisDtos.RepositoryAnalysisDto(
                analysis.getId().toString(),
                toRepositoryRef(repository),
                iso(analysis.getFinishedAt()),
                toStats(analysis),
                loadDna(analysis),
                loadArchitecture(analysisId),
                loadCodebase(analysisId, repository.getName()),
                loadHotspots(analysisId),
                loadDependencies(analysisId),
                loadEvolution(analysisId),
                loadInsights(analysisId),
                loadRemediation(analysisId),
                DOCTOR_PROMPTS,
                analysis.getIncompleteDimensions());
    }

    public AnalysisDtos.AnalysisSummary toSummary(AnalysisEntity analysis) {
        RepositoryEntity repository = repositories.findById(analysis.getRepositoryId())
                .orElse(null);
        if (repository == null || analysis.getOverallScore() == null) {
            return null;
        }
        return new AnalysisDtos.AnalysisSummary(
                analysis.getId().toString(),
                displayNameOf(repository),
                repository.getOwner(),
                repository.getName(),
                repository.getUrl(),
                analysis.getOverallScore().doubleValue(),
                repository.getPrimaryLanguage(),
                iso(analysis.getFinishedAt()));
    }

    // ---- Sections ----------------------------------------------------------

    private AnalysisDtos.DnaScore loadDna(AnalysisEntity analysis) {
        List<AnalysisDtos.DnaDimensionDto> dimensions = jdbc.query("""
                SELECT dimension, score, verdict, confidence, weight, delta, headline,
                       summary, strengths, watch_items, evidence
                FROM dimension_score WHERE analysis_id = ?
                """, (rs, rowNum) -> {
            DnaDimension key = DnaDimension.fromWire(rs.getString("dimension"));
            Double score = roundScore(nullableDouble(rs, "score"));
            return new AnalysisDtos.DnaDimensionDto(
                    key,
                    key.label(),
                    score,
                    score == null ? null : HealthVerdict.forScore(score),
                    rs.getString("headline"),
                    rs.getString("summary"),
                    readStringList(rs.getString("strengths")),
                    readStringList(rs.getString("watch_items")),
                    Math.round(rs.getDouble("delta")),
                    rs.getDouble("weight"),
                    Math.round(rs.getDouble("confidence")),
                    readStringMap(rs.getString("evidence")));
        }, analysis.getId());

        // Present in the canonical order the product reads them in.
        dimensions = dimensions.stream()
                .sorted(Comparator.comparingInt(dimension -> dimension.key().ordinal()))
                .toList();

        double overall = analysis.getOverallScore() == null ? 0
                : Math.round(analysis.getOverallScore().doubleValue());
        double confidence = dimensions.stream()
                .filter(dimension -> dimension.score() != null)
                .mapToDouble(AnalysisDtos.DnaDimensionDto::confidence)
                .average().orElse(0);

        return new AnalysisDtos.DnaScore(
                overall,
                analysis.getOverallVerdict() == null
                        ? HealthVerdict.forScore(overall) : analysis.getOverallVerdict(),
                dimensions,
                roundScore(analysis.getPercentile() == null
                        ? null : analysis.getPercentile().doubleValue()),
                Math.round(confidence));
    }

    private AnalysisDtos.ArchitectureGraphDto loadArchitecture(UUID analysisId) {
        List<AnalysisDtos.ArchitectureNodeDto> nodes = jdbc.query("""
                SELECT node_key, name, kind, layer, path, language, complexity_score,
                       complexity_band, lines_of_code, coverage, risk, fan_in, fan_out,
                       description
                FROM architecture_node WHERE analysis_id = ? ORDER BY layer, name
                """, (rs, rowNum) -> new AnalysisDtos.ArchitectureNodeDto(
                rs.getString("node_key"),
                rs.getString("name"),
                ArchitectureNodeKind.fromWire(rs.getString("kind")),
                ArchitectureLayer.fromWire(rs.getString("layer")),
                rs.getString("path"),
                rs.getString("language"),
                ComplexityBand.fromWire(rs.getString("complexity_band")),
                rs.getInt("complexity_score"),
                rs.getInt("lines_of_code"),
                nullableDouble(rs, "coverage"),
                RiskLevel.fromWire(rs.getString("risk")),
                rs.getInt("fan_out"),
                rs.getInt("fan_in"),
                rs.getString("description")), analysisId);

        List<AnalysisDtos.ArchitectureEdgeDto> edges = jdbc.query("""
                SELECT source_key, target_key, kind, weight
                FROM architecture_edge WHERE analysis_id = ?
                """, (rs, rowNum) -> new AnalysisDtos.ArchitectureEdgeDto(
                rs.getString("source_key") + "->" + rs.getString("target_key"),
                rs.getString("source_key"),
                rs.getString("target_key"),
                ArchitectureEdgeKind.fromWire(rs.getString("kind")),
                rs.getInt("weight")), analysisId);

        List<List<String>> cycles = jdbc.query("""
                SELECT node_keys FROM architecture_cycle
                WHERE analysis_id = ? ORDER BY ordinal
                """, (rs, rowNum) -> readStringList(rs.getString("node_keys")), analysisId);

        return new AnalysisDtos.ArchitectureGraphDto(nodes, edges, cycles);
    }

    /**
     * Rebuilds the directory tree from the flat file rows.
     *
     * <p>Done in one pass over path segments rather than a query per level:
     * the codebase explorer needs the whole tree, and a recursive query would
     * be a round trip per directory.
     */
    private AnalysisDtos.FileNodeDto loadCodebase(UUID analysisId, String repositoryName) {
        record FileRow(String path, String name, String language, int loc,
                       String complexityBand, Integer complexityScore, int dependencies,
                       int dependents, int changes, Double coverage, String risk,
                       Double health, String lastChanged, String primaryAuthor,
                       String nodeKey) {
        }

        List<FileRow> rows = jdbc.query("""
                SELECT path, name, language, lines_of_code, complexity_band, complexity_score,
                       import_count, dependents, change_count, coverage, risk, health,
                       last_changed_at, primary_author, architecture_node_key
                FROM code_file WHERE analysis_id = ? ORDER BY path
                """, (rs, rowNum) -> new FileRow(
                rs.getString("path"), rs.getString("name"), rs.getString("language"),
                rs.getInt("lines_of_code"), rs.getString("complexity_band"),
                nullableInt(rs, "complexity_score"), rs.getInt("import_count"),
                rs.getInt("dependents"), rs.getInt("change_count"),
                nullableDouble(rs, "coverage"), rs.getString("risk"),
                roundScore(nullableDouble(rs, "health")),
                rs.getTimestamp("last_changed_at") == null ? null
                        : rs.getTimestamp("last_changed_at").toInstant().toString()
                                .substring(0, 10),
                rs.getString("primary_author"), rs.getString("architecture_node_key")),
                analysisId);

        // Mutable tree, collapsed into DTOs once it is complete.
        DirectoryNode root = new DirectoryNode("", repositoryName);
        int[] counter = {0};

        for (FileRow row : rows) {
            String[] segments = row.path().split("/");
            DirectoryNode cursor = root;
            StringBuilder currentPath = new StringBuilder();

            for (int i = 0; i < segments.length - 1; i++) {
                if (!currentPath.isEmpty()) {
                    currentPath.append('/');
                }
                currentPath.append(segments[i]);
                cursor = cursor.child(segments[i], currentPath.toString());
            }

            cursor.files.add(new AnalysisDtos.FileNodeDto(
                    "f" + (++counter[0]),
                    row.name(), row.path(), "file", row.language(), null,
                    new AnalysisDtos.FileMetricsDto(
                            row.loc(),
                            row.complexityBand() == null ? null
                                    : ComplexityBand.fromWire(row.complexityBand()),
                            row.complexityScore() == null ? 0 : row.complexityScore(),
                            row.dependencies(), row.dependents(), row.changes(),
                            row.coverage(),
                            row.risk() == null ? null : RiskLevel.fromWire(row.risk()),
                            row.lastChanged(), row.primaryAuthor()),
                    row.health(),
                    row.nodeKey()));
        }

        return root.toDto(counter);
    }

    /** A directory while the tree is being assembled. */
    private static final class DirectoryNode {
        private final String path;
        private final String name;
        private final Map<String, DirectoryNode> directories = new LinkedHashMap<>();
        private final List<AnalysisDtos.FileNodeDto> files = new ArrayList<>();

        DirectoryNode(String path, String name) {
            this.path = path;
            this.name = name;
        }

        DirectoryNode child(String childName, String childPath) {
            return directories.computeIfAbsent(childName,
                    key -> new DirectoryNode(childPath, key));
        }

        /**
         * Collapses into the response shape, computing directory health as the
         * size-weighted mean of the descendants. Weighting by lines means a
         * large healthy directory is not dragged down by one small bad file.
         */
        AnalysisDtos.FileNodeDto toDto(int[] counter) {
            List<AnalysisDtos.FileNodeDto> children = new ArrayList<>();
            directories.values().forEach(directory -> children.add(directory.toDto(counter)));
            children.addAll(files);

            double weightedHealth = 0;
            double totalWeight = 0;
            for (AnalysisDtos.FileNodeDto child : children) {
                double weight = weightOf(child);
                if (weight <= 0) {
                    continue;
                }
                totalWeight += weight;
                weightedHealth += (child.health() == null ? 100 : child.health()) * weight;
            }
            Double health = totalWeight == 0 ? null
                    : Math.round((weightedHealth / totalWeight) * 10.0) / 10.0;

            return new AnalysisDtos.FileNodeDto(
                    "d" + (++counter[0]), name, path, "directory", null,
                    List.copyOf(children), null, health, null);
        }

        private static double weightOf(AnalysisDtos.FileNodeDto node) {
            if (node.metrics() != null) {
                return node.metrics().linesOfCode();
            }
            return node.children() == null ? 0
                    : node.children().stream().mapToDouble(DirectoryNode::weightOf).sum();
        }
    }

    private List<AnalysisDtos.HotspotDto> loadHotspots(UUID analysisId) {
        return jdbc.query("""
                SELECT id, name, path, change_count, complexity_score, complexity_band,
                       dependency_count, bug_fix_count, lines_of_code, coverage,
                       contributor_count, risk_score, severity, rationale, recommendation,
                       architecture_node_key
                FROM hotspot WHERE analysis_id = ? ORDER BY risk_score DESC
                """, (rs, rowNum) -> new AnalysisDtos.HotspotDto(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("path"),
                rs.getInt("change_count"),
                rs.getInt("complexity_score"),
                ComplexityBand.fromWire(rs.getString("complexity_band")),
                rs.getInt("dependency_count"),
                rs.getInt("bug_fix_count"),
                rs.getInt("lines_of_code"),
                roundScore(nullableDouble(rs, "coverage")),
                rs.getInt("contributor_count"),
                Math.round(rs.getDouble("risk_score")),
                RiskLevel.fromWire(rs.getString("severity")),
                rs.getString("rationale"),
                rs.getString("recommendation"),
                rs.getString("architecture_node_key")), analysisId);
    }

    private AnalysisDtos.DependencyProfileDto loadDependencies(UUID analysisId) {
        List<AnalysisDtos.DependencyDto> dependencies = jdbc.query("""
                SELECT id, name, ecosystem, version, latest_version, status, license,
                       direct, used_by, size_kb, risk, advisory_count, note
                FROM dependency WHERE analysis_id = ? ORDER BY ecosystem, name
                """, (rs, rowNum) -> new AnalysisDtos.DependencyDto(
                rs.getString("id"),
                rs.getString("name"),
                DependencyEcosystem.fromWire(rs.getString("ecosystem")),
                rs.getString("version"),
                rs.getString("latest_version"),
                DependencyStatus.fromWire(rs.getString("status")),
                rs.getString("license"),
                rs.getBoolean("direct"),
                rs.getInt("used_by"),
                nullableInt(rs, "size_kb"),
                RiskLevel.fromWire(rs.getString("risk")),
                nullableInt(rs, "advisory_count"),
                rs.getString("note")), analysisId);

        List<AnalysisDtos.TechnologyDto> technologies = jdbc.query("""
                SELECT id, name, category, share, lines_of_code, version, dependency_count
                FROM technology WHERE analysis_id = ? ORDER BY share DESC, name
                """, (rs, rowNum) -> new AnalysisDtos.TechnologyDto(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("category"),
                rs.getDouble("share"),
                rs.getInt("lines_of_code"),
                rs.getString("version"),
                rs.getInt("dependency_count")), analysisId);

        long direct = dependencies.stream().filter(AnalysisDtos.DependencyDto::direct).count();
        boolean versionsChecked = dependencies.stream()
                .anyMatch(dependency -> dependency.latestVersion() != null);
        boolean advisoriesChecked = dependencies.stream()
                .anyMatch(dependency -> dependency.advisories() != null);

        Integer outdated = versionsChecked ? (int) dependencies.stream()
                .filter(dependency -> dependency.status() == DependencyStatus.MINOR_BEHIND
                        || dependency.status() == DependencyStatus.MAJOR_BEHIND)
                .count() : null;
        Integer advisories = advisoriesChecked ? dependencies.stream()
                .filter(dependency -> dependency.advisories() != null)
                .mapToInt(AnalysisDtos.DependencyDto::advisories).sum() : null;

        return new AnalysisDtos.DependencyProfileDto(
                dependencies.size(),
                (int) direct,
                // Null, not zero: transitive resolution was never performed.
                null,
                outdated,
                (int) dependencies.stream()
                        .filter(dependency -> dependency.status() == DependencyStatus.DEPRECATED)
                        .count(),
                advisories,
                dependencies,
                technologies);
    }

    private AnalysisDtos.EvolutionHistoryDto loadEvolution(UUID analysisId) {
        List<AnalysisDtos.EvolutionPointDto> points = jdbc.query("""
                SELECT year, overall, contributors, commits, lines_of_code, modules,
                       hotspots, test_coverage, scores
                FROM evolution_point WHERE analysis_id = ? ORDER BY year
                """, (rs, rowNum) -> new AnalysisDtos.EvolutionPointDto(
                rs.getInt("year"),
                readDoubleMap(rs.getString("scores")),
                nullableDouble(rs, "overall"),
                rs.getInt("contributors"),
                rs.getInt("commits"),
                rs.getInt("lines_of_code"),
                rs.getInt("modules"),
                rs.getInt("hotspots"),
                nullableDouble(rs, "test_coverage")), analysisId);

        List<AnalysisDtos.EvolutionMilestoneDto> milestones = jdbc.query("""
                SELECT id, year, offset_in_year, kind, title, description, impact
                FROM evolution_milestone WHERE analysis_id = ? ORDER BY year, offset_in_year
                """, (rs, rowNum) -> new AnalysisDtos.EvolutionMilestoneDto(
                rs.getString("id"),
                rs.getInt("year"),
                rs.getDouble("offset_in_year"),
                MilestoneKind.fromWire(rs.getString("kind")),
                rs.getString("title"),
                rs.getString("description"),
                rs.getDouble("impact")), analysisId);

        return new AnalysisDtos.EvolutionHistoryDto(points, milestones);
    }

    private List<AnalysisDtos.AiInsightDto> loadInsights(UUID analysisId) {
        return jdbc.query("""
                SELECT id, code, severity, dimension, title, description, related_nodes,
                       confidence
                FROM issue WHERE analysis_id = ?
                ORDER BY CASE severity
                    WHEN 'CRITICAL' THEN 0 WHEN 'HIGH' THEN 1 WHEN 'MEDIUM' THEN 2
                    WHEN 'LOW' THEN 3 ELSE 4 END
                """, (rs, rowNum) -> new AnalysisDtos.AiInsightDto(
                rs.getString("id"),
                // An issue is a risk; strengths are surfaced through dimensions.
                InsightKind.RISK,
                severityToRisk(rs.getString("severity")),
                rs.getString("title"),
                rs.getString("description"),
                DnaDimension.fromWire(rs.getString("dimension")),
                readStringList(rs.getString("related_nodes")),
                rs.getDouble("confidence")), analysisId);
    }

    private List<AnalysisDtos.RemediationStepDto> loadRemediation(UUID analysisId) {
        return jdbc.query("""
                SELECT rank, title, detail, effort, expected_gain, dimension
                FROM recommendation WHERE analysis_id = ? ORDER BY rank
                """, (rs, rowNum) -> new AnalysisDtos.RemediationStepDto(
                rs.getInt("rank"),
                rs.getString("title"),
                rs.getString("detail"),
                Effort.fromWire(rs.getString("effort")),
                rs.getDouble("expected_gain"),
                DnaDimension.fromWire(rs.getString("dimension"))), analysisId);
    }

    // ---- Plumbing ----------------------------------------------------------

    private AnalysisDtos.RepositoryRef toRepositoryRef(RepositoryEntity repository) {
        return new AnalysisDtos.RepositoryRef(
                repository.getOwner(),
                repository.getName(),
                displayNameOf(repository),
                repository.getUrl(),
                repository.getDefaultBranch(),
                repository.getDescription(),
                repository.getStars(),
                repository.getForks(),
                repository.getPrimaryLanguage(),
                iso(repository.getRemoteCreatedAt()),
                iso(repository.getLastCommitAt()));
    }

    private AnalysisDtos.AnalysisStats toStats(AnalysisEntity analysis) {
        return new AnalysisDtos.AnalysisStats(
                orZero(analysis.getTotalFiles()),
                orZero(analysis.getTotalLines()),
                orZero(analysis.getTotalModules()),
                orZero(analysis.getTotalServices()),
                orZero(analysis.getTotalComponents()),
                orZero(analysis.getTotalCommits()),
                orZero(analysis.getTotalContributors()),
                roundScore(analysis.getTestCoverage() == null
                        ? null : analysis.getTestCoverage().doubleValue()),
                analysis.getDurationMs() == null ? 0 : analysis.getDurationMs());
    }

    /** Turns `atlas-platform` into `Atlas Platform` for display. */
    private String displayNameOf(RepositoryEntity repository) {
        String[] words = repository.getName().split("[-_.]");
        StringBuilder display = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (!display.isEmpty()) display.append(' ');
            display.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return display.isEmpty() ? repository.getName() : display.toString();
    }

    private RiskLevel severityToRisk(String severity) {
        return switch (severity) {
            case "CRITICAL" -> RiskLevel.CRITICAL;
            case "HIGH" -> RiskLevel.HIGH;
            case "MEDIUM" -> RiskLevel.MEDIUM;
            default -> RiskLevel.LOW;
        };
    }

    private List<String> readStringList(String value) {
        if (value == null || value.isBlank()) return List.of();
        try {
            return json.readValue(value, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    private Map<String, String> readStringMap(String value) {
        if (value == null || value.isBlank()) return Map.of();
        try {
            return json.readValue(value, new TypeReference<Map<String, String>>() {
            });
        } catch (Exception e) {
            return Map.of();
        }
    }

    private Map<String, Double> readDoubleMap(String value) {
        if (value == null || value.isBlank()) return Map.of();
        try {
            return json.readValue(value, new TypeReference<Map<String, Double>>() {
            });
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static Double nullableDouble(ResultSet rs, String column) throws SQLException {
        BigDecimal value = rs.getBigDecimal(column);
        return value == null ? null : value.doubleValue();
    }

    /**
     * Rounds a score to a whole number for the wire.
     *
     * <p>A DNA score is a heuristic on a 0-100 scale; the digits after the
     * decimal point are arithmetic artefacts, not measurement precision, and
     * presenting "86.76" implies an accuracy the method does not have. The
     * stored value keeps its precision for comparison and trend work.
     */
    private static Double roundScore(Double value) {
        return value == null ? null : (double) Math.round(value);
    }

    private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static int orZero(Integer value) {
        return value == null ? 0 : value;
    }

    private static String iso(Instant instant) {
        return instant == null ? null : instant.toString();
    }
}
