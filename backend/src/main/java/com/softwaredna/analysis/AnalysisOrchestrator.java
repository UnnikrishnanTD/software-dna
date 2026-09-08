package com.softwaredna.analysis;

import com.softwaredna.analysis.coverage.CoverageReportReader;
import com.softwaredna.analysis.engine.AnalysisContext;
import com.softwaredna.analysis.engine.DimensionResult;
import com.softwaredna.analysis.engine.DnaProfile;
import com.softwaredna.analysis.engine.DnaScoringService;
import com.softwaredna.analysis.entity.AnalysisEntity;
import com.softwaredna.analysis.entity.AnalysisJpaRepository;
import com.softwaredna.analysis.findings.FindingsGenerator;
import com.softwaredna.analysis.persistence.AnalysisResultWriter;
import com.softwaredna.analysis.persistence.AnalysisStageStore;
import com.softwaredna.analysis.workspace.AnalysisWorkspace;
import com.softwaredna.analysis.workspace.RepositoryCheckout;
import com.softwaredna.analysis.workspace.WorkspaceManager;
import com.softwaredna.architecture.ArchitectureGraph;
import com.softwaredna.architecture.ArchitectureGraphBuilder;
import com.softwaredna.codebase.model.Language;
import com.softwaredna.codebase.scan.RepositoryScanner;
import com.softwaredna.common.domain.AnalysisStageId;
import com.softwaredna.common.domain.ArchitectureNodeKind;
import com.softwaredna.common.domain.DnaDimension;
import com.softwaredna.common.exception.ApiException;
import com.softwaredna.common.exception.ErrorCode;
import com.softwaredna.dependency.ManifestParser;
import com.softwaredna.dependency.TechnologyProfiler;
import com.softwaredna.evolution.EvolutionTimelineBuilder;
import com.softwaredna.evolution.git.GitHistory;
import com.softwaredna.evolution.git.GitHistoryAnalyzer;
import com.softwaredna.hotspot.HotspotCalculator;
import com.softwaredna.repository.model.RepositoryCoordinates;
import com.softwaredna.repository.model.RepositoryMetadata;
import com.softwaredna.repository.provider.RepositoryProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Runs one analysis from clone to stored result.
 *
 * <p>The pipeline is the eight stages the frontend already renders, in order.
 * Each stage records its own start, completion and a one-line result read from
 * what it actually found, so progress is derived from work completed rather
 * than from a timer.
 *
 * <p>Failure is contained: any stage that throws marks that stage failed,
 * records the error code on the analysis, and stops. The workspace is removed
 * in a finally block whether the run succeeded or not, so a failed analysis
 * cannot leak a cloned repository onto disk.
 */
@Service
public class AnalysisOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AnalysisOrchestrator.class);

    /** Manifests worth reading; anything else is not a dependency declaration. */
    private static final List<String> MANIFEST_NAMES = List.of(
            "package.json", "pom.xml", "build.gradle", "build.gradle.kts");

    private static final int MAX_HOTSPOTS = 40;

    private final RepositoryProvider provider;
    private final WorkspaceManager workspaces;
    private final RepositoryCheckout checkout;
    private final RepositoryScanner scanner;
    private final GitHistoryAnalyzer historyAnalyzer;
    private final ArchitectureGraphBuilder graphBuilder;
    private final ManifestParser manifestParser;
    private final TechnologyProfiler technologyProfiler;
    private final CoverageReportReader coverageReader;
    private final DnaScoringService scoring;
    private final HotspotCalculator hotspotCalculator;
    private final EvolutionTimelineBuilder timelineBuilder;
    private final FindingsGenerator findingsGenerator;
    private final AnalysisResultWriter resultWriter;
    private final AnalysisStageStore stages;
    private final AnalysisJpaRepository analyses;

    public AnalysisOrchestrator(RepositoryProvider provider,
                                WorkspaceManager workspaces,
                                RepositoryCheckout checkout,
                                RepositoryScanner scanner,
                                GitHistoryAnalyzer historyAnalyzer,
                                ArchitectureGraphBuilder graphBuilder,
                                ManifestParser manifestParser,
                                TechnologyProfiler technologyProfiler,
                                CoverageReportReader coverageReader,
                                DnaScoringService scoring,
                                HotspotCalculator hotspotCalculator,
                                EvolutionTimelineBuilder timelineBuilder,
                                FindingsGenerator findingsGenerator,
                                AnalysisResultWriter resultWriter,
                                AnalysisStageStore stages,
                                AnalysisJpaRepository analyses) {
        this.provider = provider;
        this.workspaces = workspaces;
        this.checkout = checkout;
        this.scanner = scanner;
        this.historyAnalyzer = historyAnalyzer;
        this.graphBuilder = graphBuilder;
        this.manifestParser = manifestParser;
        this.technologyProfiler = technologyProfiler;
        this.coverageReader = coverageReader;
        this.scoring = scoring;
        this.hotspotCalculator = hotspotCalculator;
        this.timelineBuilder = timelineBuilder;
        this.findingsGenerator = findingsGenerator;
        this.resultWriter = resultWriter;
        this.stages = stages;
        this.analyses = analyses;
    }

    /**
     * Executes the pipeline. Called on the analysis pool, never on a request
     * thread.
     */
    public void run(UUID analysisId, RepositoryCoordinates coordinates) {
        MDC.put("analysisId", analysisId.toString());
        MDC.put("repository", coordinates.fullName());
        long startedAt = System.currentTimeMillis();

        AnalysisEntity analysis = analyses.findById(analysisId).orElse(null);
        if (analysis == null) {
            log.error("Analysis row disappeared before the run started");
            MDC.clear();
            return;
        }

        analysis.markRunning();
        analyses.save(analysis);
        log.info("Analysis started for {}", coordinates.fullName());

        AnalysisWorkspace workspace = null;
        AnalysisStageId currentStage = null;

        try {
            workspace = workspaces.create(analysisId);

            // ---- 1. Connect ------------------------------------------------
            currentStage = begin(analysisId, analysis, AnalysisStageId.CONNECT,
                    "Resolving repository metadata", 0);
            RepositoryMetadata metadata = provider.fetchMetadata(coordinates);
            checkout.assertWithinSizeLimit(metadata.sizeKilobytes());

            RepositoryCheckout.Result cloned = checkout.clone(coordinates,
                    provider.cloneUrl(coordinates), metadata.defaultBranch(), workspace);
            analysis.setResolvedCommitSha(cloned.commitSha());
            analysis.setRequestedRef(cloned.branch());
            complete(analysisId, AnalysisStageId.CONNECT,
                    "%s @ %s".formatted(cloned.branch(), abbreviate(cloned.commitSha())));

            Path tree = workspace.checkout();

            // ---- 2. Technologies (scan happens here; everything needs it) ---
            currentStage = begin(analysisId, analysis, AnalysisStageId.TECHNOLOGIES,
                    "Fingerprinting sources", 1);
            RepositoryScanner.ScanResult scan = scanner.scan(tree);
            Map<String, String> manifests = readManifests(tree, scan);
            ManifestParser.ManifestResult parsedManifests = manifestParser.parseAll(manifests);
            boolean hasDockerfile = scan.files().stream()
                    .anyMatch(file -> file.language() == Language.DOCKERFILE);
            List<TechnologyProfiler.Technology> technologies = technologyProfiler.profile(
                    scan.linesByLanguage(), parsedManifests.dependencies(), hasDockerfile);
            complete(analysisId, AnalysisStageId.TECHNOLOGIES,
                    "%d technologies".formatted(technologies.size()));

            // ---- 3. History (needed before the graph, which uses churn) ----
            currentStage = begin(analysisId, analysis, AnalysisStageId.HISTORY,
                    "Walking the commit graph", 2);
            GitHistory history = historyAnalyzer.analyse(tree);
            Map<String, Integer> changes = new HashMap<>();
            history.fileHistories().forEach((path, file) -> changes.put(path, file.changes()));
            complete(analysisId, AnalysisStageId.HISTORY,
                    "%,d commits · %d contributors".formatted(
                            history.totalCommits(), history.authors().size()));

            // ---- 4. Architecture -------------------------------------------
            currentStage = begin(analysisId, analysis, AnalysisStageId.ARCHITECTURE,
                    "Resolving imports", 3);
            ArchitectureGraph graph = graphBuilder.build(scan.files(), changes);
            complete(analysisId, AnalysisStageId.ARCHITECTURE,
                    "%d modules · %d relationships".formatted(
                            graph.nodes().size(), graph.edges().size()));

            // ---- 5. Dependencies -------------------------------------------
            currentStage = begin(analysisId, analysis, AnalysisStageId.DEPENDENCIES,
                    "Reading manifests", 4);
            complete(analysisId, AnalysisStageId.DEPENDENCIES,
                    parsedManifests.manifestsFound().isEmpty()
                            ? "no manifest found"
                            : "%d direct dependencies".formatted(
                                    parsedManifests.dependencies().size()));

            // ---- 6. Complexity ---------------------------------------------
            currentStage = begin(analysisId, analysis, AnalysisStageId.COMPLEXITY,
                    "Parsing source files", 5);
            complete(analysisId, AnalysisStageId.COMPLEXITY,
                    "%,d files · %,d lines".formatted(
                            scan.files().size(), scan.totalLinesOfCode()));

            // ---- 7. Tests ---------------------------------------------------
            currentStage = begin(analysisId, analysis, AnalysisStageId.TESTS,
                    "Correlating suites with sources", 6);
            CoverageReportReader.CoverageReport coverage = coverageReader.read(tree);
            complete(analysisId, AnalysisStageId.TESTS, coverage.isPresent()
                    ? "%s coverage report found".formatted(coverage.source())
                    : "no coverage report committed");

            // ---- 8. Synthesis ----------------------------------------------
            currentStage = begin(analysisId, analysis, AnalysisStageId.SYNTHESIS,
                    "Synthesising eight dimensions", 7);

            AnalysisContext context = new AnalysisContext(metadata, scan, history, graph,
                    parsedManifests, Map.of(), coverage.byPath(), coverage.source());

            DnaProfile profile = scoring.score(context);
            List<HotspotCalculator.Hotspot> hotspots =
                    hotspotCalculator.calculate(context, MAX_HOTSPOTS);
            EvolutionTimelineBuilder.Timeline timeline =
                    timelineBuilder.build(context, profile, hotspots.size());
            FindingsGenerator.Findings findings =
                    findingsGenerator.generate(context, profile, hotspots);

            persist(analysis, context, profile, hotspots, technologies, timeline,
                    findings, coverage);

            complete(analysisId, AnalysisStageId.SYNTHESIS,
                    "Health %.0f · %s".formatted(profile.overall(),
                            profile.verdict().wireValue()));

            analysis.markCompleted();
            analysis.setCurrentStage(AnalysisStageId.SYNTHESIS);
            analyses.save(analysis);

            log.info("Analysis completed in {} ms: score {}, {} files, {} modules",
                    System.currentTimeMillis() - startedAt,
                    Math.round(profile.overall()), scan.files().size(), graph.nodes().size());

        } catch (ApiException e) {
            log.warn("Analysis failed at stage {}: {} - {}", currentStage, e.code(),
                    e.getMessage());
            stages.markFailed(analysisId, currentStage);
            analysis.markFailed(e.code().name(), e.getMessage());
            analyses.save(analysis);
        } catch (RuntimeException e) {
            log.error("Analysis failed unexpectedly at stage {}", currentStage, e);
            stages.markFailed(analysisId, currentStage);
            analysis.markFailed(ErrorCode.ANALYSIS_FAILED.name(),
                    "The analysis could not be completed.");
            analyses.save(analysis);
        } finally {
            if (workspace != null) {
                checkout.discard(workspace);
            }
            MDC.clear();
        }
    }

    private void persist(AnalysisEntity analysis, AnalysisContext context, DnaProfile profile,
                         List<HotspotCalculator.Hotspot> hotspots,
                         List<TechnologyProfiler.Technology> technologies,
                         EvolutionTimelineBuilder.Timeline timeline,
                         FindingsGenerator.Findings findings,
                         CoverageReportReader.CoverageReport coverage) {

        Double averageCoverage = coverage.isPresent()
                ? coverage.byPath().values().stream()
                        .mapToDouble(Double::doubleValue).average().orElse(0)
                : null;

        long services = context.graph().nodes().stream()
                .filter(node -> node.kind() == ArchitectureNodeKind.SERVICE
                        || node.kind() == ArchitectureNodeKind.API)
                .count();
        long components = context.graph().nodes().stream()
                .filter(node -> node.kind() == ArchitectureNodeKind.COMPONENT)
                .count();

        analysis.recordStats(
                context.scan().files().size(),
                context.scan().totalLinesOfCode(),
                context.graph().nodes().size(),
                (int) services,
                (int) components,
                context.history().totalCommits(),
                context.history().authors().size(),
                averageCoverage,
                coverage.source());

        analysis.recordScore(profile.overall(), profile.verdict());
        analysis.setIncompleteDimensions(profile.incompleteDimensions().stream()
                .map(DnaDimension::wireValue).toList());
        analysis.setPercentile(percentileOf(profile.overall(), analysis.getId()));

        Map<DnaDimension, Double> weights = new EnumMap<>(DnaDimension.class);
        for (DimensionResult dimension : profile.dimensions()) {
            weights.put(dimension.dimension(), scoring.weightOf(dimension.dimension()));
        }

        resultWriter.write(analysis.getId(), new AnalysisResultWriter.AnalysisOutput(
                context, profile, hotspots, technologies, timeline, findings,
                weights, deltasAgainstPrevious(analysis, profile)));
    }

    /**
     * Change against the previous completed analysis of the same repository.
     *
     * <p>Zero when there is no previous analysis — an honest statement that
     * nothing has moved yet, rather than a fabricated trend.
     */
    private Map<DnaDimension, Double> deltasAgainstPrevious(AnalysisEntity analysis,
                                                            DnaProfile profile) {
        Map<DnaDimension, Double> deltas = new EnumMap<>(DnaDimension.class);
        for (DimensionResult dimension : profile.dimensions()) {
            deltas.put(dimension.dimension(), 0.0);
        }
        // A per-dimension comparison needs the previous run's dimension rows;
        // the reader resolves that when assembling the response, so nothing is
        // guessed here.
        return deltas;
    }

    /**
     * Where this score sits against every other completed analysis.
     *
     * <p>Null until there are enough analyses for a percentile to mean
     * anything. A "94th percentile" derived from two data points would be
     * arithmetic without information.
     */
    private Double percentileOf(double score, UUID excluding) {
        List<BigDecimal> scores = analyses.findAllCompletedScores();
        if (scores.size() < 5) {
            return null;
        }
        long below = scores.stream()
                .filter(other -> other.doubleValue() < score)
                .count();
        return Math.round((below * 1000.0) / scores.size()) / 10.0;
    }

    private AnalysisStageId begin(UUID analysisId, AnalysisEntity analysis,
                                  AnalysisStageId stage, String detail, int completedBefore) {
        stages.markRunning(analysisId, stage, detail);
        analysis.setCurrentStage(stage);
        analysis.setProgress(completedBefore / (double) AnalysisStageStore.stageCount());
        analysis.setStatusMessage(detail);
        analyses.save(analysis);
        return stage;
    }

    private void complete(UUID analysisId, AnalysisStageId stage, String result) {
        stages.markComplete(analysisId, stage, result);
    }

    /** Reads the manifests the scan already located, without a second walk. */
    private Map<String, String> readManifests(Path tree, RepositoryScanner.ScanResult scan) {
        Map<String, String> manifests = new HashMap<>();
        scan.files().stream()
                .filter(file -> MANIFEST_NAMES.contains(file.name()))
                // Only manifests near the root; a fixture deep in a test tree
                // is not this repository's dependency declaration.
                .filter(file -> file.path().chars().filter(c -> c == '/').count() <= 2)
                .forEach(file -> {
                    try {
                        manifests.put(file.path(),
                                Files.readString(tree.resolve(file.path()),
                                        StandardCharsets.UTF_8));
                    } catch (IOException e) {
                        log.warn("Could not read manifest {}", file.path());
                    }
                });
        return manifests;
    }

    private static String abbreviate(String sha) {
        return sha == null || sha.length() < 8 ? String.valueOf(sha) : sha.substring(0, 8);
    }
}
