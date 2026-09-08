package com.softwaredna.analysis;

import com.softwaredna.analysis.engine.AnalysisContext;
import com.softwaredna.analysis.engine.DimensionResult;
import com.softwaredna.analysis.engine.DnaProfile;
import com.softwaredna.analysis.engine.DnaScoringService;
import com.softwaredna.analysis.engine.Measurement;
import com.softwaredna.analysis.engine.module.ArchitectureModule;
import com.softwaredna.analysis.engine.module.DependenciesModule;
import com.softwaredna.analysis.engine.module.DocumentationModule;
import com.softwaredna.analysis.engine.module.EvolutionModule;
import com.softwaredna.analysis.engine.module.MaintainabilityModule;
import com.softwaredna.analysis.engine.module.PerformanceModule;
import com.softwaredna.analysis.engine.module.SecurityModule;
import com.softwaredna.analysis.engine.module.TestingModule;
import com.softwaredna.architecture.ArchitectureGraph;
import com.softwaredna.architecture.ArchitectureGraphBuilder;
import com.softwaredna.codebase.analyzer.JavaLanguageAnalyzer;
import com.softwaredna.codebase.analyzer.LanguageAnalyzer;
import com.softwaredna.codebase.analyzer.LineCountingAnalyzer;
import com.softwaredna.codebase.analyzer.TypeScriptLanguageAnalyzer;
import com.softwaredna.codebase.scan.RepositoryScanner;
import com.softwaredna.common.domain.DnaDimension;
import com.softwaredna.config.AnalysisLimits;
import com.softwaredna.dependency.ManifestParser;
import com.softwaredna.evolution.git.GitHistory;
import com.softwaredna.evolution.git.GitHistoryAnalyzer;
import com.softwaredna.hotspot.HotspotCalculator;
import com.softwaredna.repository.model.RepositoryCoordinates;
import com.softwaredna.repository.model.RepositoryMetadata;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the whole engine over a genuine repository — this project's own working
 * tree — and asserts that the resulting DNA is internally consistent and
 * honest about what it could not measure.
 *
 * <p>The assertions deliberately avoid pinning exact scores: those move as the
 * repository changes, and a test that has to be updated on every commit stops
 * being read. What is asserted instead are the invariants that must hold for
 * any repository, and the specific claims this repository's contents support.
 */
class EndToEndAnalysisTest {

    private static final AnalysisLimits LIMITS = new AnalysisLimits(
            500L * 1024 * 1024, 2L * 1024 * 1024, 25_000, 20_000,
            Duration.ofMinutes(5), 300);

    private Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath();
        return current.getFileName().toString().equals("backend")
                ? current.getParent()
                : current;
    }

    private AnalysisContext buildContext() throws IOException {
        Path root = repositoryRoot();

        List<LanguageAnalyzer> analyzers = List.of(
                new JavaLanguageAnalyzer(), new TypeScriptLanguageAnalyzer());
        RepositoryScanner scanner =
                new RepositoryScanner(analyzers, new LineCountingAnalyzer(), LIMITS);

        RepositoryScanner.ScanResult scan = scanner.scan(root);
        GitHistory history = Files.isDirectory(root.resolve(".git"))
                ? new GitHistoryAnalyzer(LIMITS).analyse(root)
                : new GitHistory(0, 0, false, null, null, List.of(), Map.of(), Map.of());

        Map<String, Integer> changes = history.fileHistories().entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,
                        entry -> entry.getValue().changes()));

        ArchitectureGraph graph =
                new ArchitectureGraphBuilder(LIMITS).build(scan.files(), changes);

        Map<String, String> manifestContents = new HashMap<>();
        for (String candidate : List.of("package.json", "backend/pom.xml")) {
            Path path = root.resolve(candidate);
            if (Files.isRegularFile(path)) {
                manifestContents.put(candidate, Files.readString(path, StandardCharsets.UTF_8));
            }
        }
        ManifestParser.ManifestResult manifests =
                new ManifestParser().parseAll(manifestContents);

        RepositoryMetadata metadata = new RepositoryMetadata(
                RepositoryCoordinates.parse("unnikrishnan/software-dna"),
                "Software DNA", "main", "TypeScript", 0, 0, 0,
                false, false, Instant.now(), Instant.now());

        return new AnalysisContext(metadata, scan, history, graph, manifests,
                Map.of(), Map.of(), null);
    }

    private DnaScoringService scoringService() {
        return new DnaScoringService(List.of(
                new ArchitectureModule(),
                new MaintainabilityModule(),
                new SecurityModule(),
                new PerformanceModule(),
                new TestingModule(),
                new DependenciesModule(),
                new DocumentationModule(),
                new EvolutionModule()));
    }

    @Test
    void producesACompleteDnaProfileForARealRepository() throws IOException {
        AnalysisContext context = buildContext();
        DnaProfile profile = scoringService().score(context);

        // ---- Print the profile, so a reader of the build output can see what
        // ---- the engine actually concluded rather than trusting the asserts.
        System.out.printf("%n=== SOFTWARE DNA :: %s ===%n",
                context.repository().coordinates().fullName());
        System.out.printf("Overall %.0f (%s), confidence %.0f%n",
                profile.overall(), profile.verdict().wireValue(), profile.confidence());
        System.out.printf("Files %d, lines %,d, modules %d, edges %d, commits %,d%n",
                context.scan().files().size(), context.scan().totalLinesOfCode(),
                context.graph().nodes().size(), context.graph().edges().size(),
                context.history().totalCommits());
        for (DimensionResult dimension : profile.dimensions()) {
            System.out.printf("  %-16s %s  conf %3.0f  %s%n",
                    dimension.dimension().wireValue(),
                    dimension.score() == null ? " n/a" : "%4.0f".formatted(dimension.score()),
                    dimension.confidence(),
                    dimension.headline());
        }

        assertThat(profile.dimensions()).hasSize(8);
        assertThat(profile.dimensions())
                .extracting(DimensionResult::dimension)
                .containsExactlyInAnyOrder(DnaDimension.values());

        assertThat(profile.overall()).isBetween(0.0, 100.0);
        assertThat(profile.verdict()).isNotNull();
    }

    @Test
    void everyScoreIsBackedByRecordedMeasurements() throws IOException {
        DnaProfile profile = scoringService().score(buildContext());

        for (DimensionResult dimension : profile.dimensions()) {
            if (!dimension.isAvailable()) {
                continue;
            }
            assertThat(dimension.measurements())
                    .as("%s produced a score with no measurements behind it",
                            dimension.dimension())
                    .isNotEmpty();
            assertThat(dimension.evidence())
                    .as("%s recorded no evidence", dimension.dimension())
                    .isNotEmpty();
            assertThat(dimension.headline()).isNotBlank();
            assertThat(dimension.summary()).isNotBlank();
        }
    }

    @Test
    void reportsCoverageAsUnavailableRatherThanInventingIt() throws IOException {
        AnalysisContext context = buildContext();
        DnaProfile profile = scoringService().score(context);

        DimensionResult testing = profile.dimensions().stream()
                .filter(dimension -> dimension.dimension() == DnaDimension.TESTING)
                .findFirst().orElseThrow();

        Measurement coverage = testing.measurements().stream()
                .filter(measurement -> measurement.key().equals("testing.coverage"))
                .findFirst().orElseThrow();

        assertThat(coverage.isAvailable())
                .as("coverage cannot be measured without executing the suite")
                .isFalse();
        assertThat(coverage.unavailableReason()).contains("does not execute");

        // The gap must cost confidence, not be silently ignored.
        assertThat(testing.confidence()).isLessThan(100);
        assertThat(testing.summary()).containsIgnoringCase("coverage");
    }

    @Test
    void reportsDependencyAdvisoriesAsUncheckedRatherThanClean() throws IOException {
        DnaProfile profile = scoringService().score(buildContext());

        DimensionResult security = profile.dimensions().stream()
                .filter(dimension -> dimension.dimension() == DnaDimension.SECURITY)
                .findFirst().orElseThrow();

        Measurement advisories = security.measurements().stream()
                .filter(measurement -> measurement.key().equals("security.advisories"))
                .findFirst().orElseThrow();

        assertThat(advisories.isAvailable())
                .as("no advisory database is configured, so nothing was checked")
                .isFalse();
        assertThat(security.summary())
                .as("the gap must be stated, not implied")
                .containsIgnoringCase("not assessed");
    }

    @Test
    void capsPerformanceConfidenceBecauseNothingIsExecuted() throws IOException {
        DnaProfile profile = scoringService().score(buildContext());

        DimensionResult performance = profile.dimensions().stream()
                .filter(dimension -> dimension.dimension() == DnaDimension.PERFORMANCE)
                .findFirst().orElseThrow();

        assertThat(performance.confidence()).isLessThanOrEqualTo(60);
        assertThat(performance.summary()).containsIgnoringCase("not runtime measurements");
    }

    @Test
    void scoresEvolutionFromThisRepositorysActualHistory() throws IOException {
        AnalysisContext context = buildContext();
        if (context.history().isEmpty()) {
            return;   // Not a git working tree in this environment.
        }

        DimensionResult evolution = scoringService().score(context).dimensions().stream()
                .filter(dimension -> dimension.dimension() == DnaDimension.EVOLUTION)
                .findFirst().orElseThrow();

        assertThat(evolution.isAvailable()).isTrue();
        assertThat(evolution.evidence()).containsKey("evolution.contributors");
        assertThat(evolution.evidence()).containsKey("evolution.topAuthorShare");
        // A brand-new repository is young, and the module must say so rather
        // than scoring it as though it had years of history.
        assertThat(evolution.measurements())
                .anySatisfy(measurement ->
                        assertThat(measurement.key()).isEqualTo("evolution.historyYears"));
    }

    @Test
    void ranksHotspotsByTheIntersectionOfChurnAndComplexity() throws IOException {
        AnalysisContext context = buildContext();
        List<HotspotCalculator.Hotspot> hotspots =
                new HotspotCalculator().calculate(context, 20);

        if (context.history().isEmpty()) {
            return;
        }

        System.out.printf("%n=== HOTSPOTS ===%n");
        hotspots.stream().limit(8).forEach(hotspot ->
                System.out.printf("  %5.1f  %-52s changes=%-4d cx=%-4d deps=%d%n",
                        hotspot.riskScore(), truncate(hotspot.path()), hotspot.changes(),
                        hotspot.complexityScore(), hotspot.dependencies()));

        assertThat(hotspots).isSortedAccordingTo(
                java.util.Comparator.comparingDouble(
                        HotspotCalculator.Hotspot::riskScore).reversed());

        assertThat(hotspots).allSatisfy(hotspot -> {
            assertThat(hotspot.riskScore()).isBetween(0.0, 100.0);
            assertThat(hotspot.rationale())
                    .as("a hotspot must explain itself")
                    .isNotBlank();
            assertThat(hotspot.recommendation()).isNotBlank();
            assertThat(hotspot.severity()).isNotNull();
        });
    }

    @Test
    void overallScoreIgnoresDimensionsItCouldNotMeasure() throws IOException {
        DnaProfile profile = scoringService().score(buildContext());

        // Recompute the roll-up from the available dimensions and confirm the
        // service renormalised rather than defaulting the missing ones.
        Map<DnaDimension, Double> weights = Map.of(
                DnaDimension.ARCHITECTURE, 0.18,
                DnaDimension.MAINTAINABILITY, 0.16,
                DnaDimension.SECURITY, 0.14,
                DnaDimension.TESTING, 0.12,
                DnaDimension.DEPENDENCIES, 0.11,
                DnaDimension.PERFORMANCE, 0.10,
                DnaDimension.EVOLUTION, 0.10,
                DnaDimension.DOCUMENTATION, 0.08);

        double weighted = 0;
        double weightSum = 0;
        for (DimensionResult dimension : profile.dimensions()) {
            if (!dimension.isAvailable()) continue;
            double weight = weights.get(dimension.dimension());
            weighted += dimension.score() * weight;
            weightSum += weight;
        }
        double expected = weightSum == 0 ? 0 : weighted / weightSum;

        assertThat(profile.overall()).isCloseTo(expected,
                org.assertj.core.data.Offset.offset(0.5));
    }

    private static String truncate(String path) {
        return path.length() <= 52 ? path : "..." + path.substring(path.length() - 49);
    }
}
