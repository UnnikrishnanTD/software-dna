package com.softwaredna.analysis.findings;

import com.softwaredna.analysis.engine.AnalysisContext;
import com.softwaredna.analysis.engine.DimensionResult;
import com.softwaredna.analysis.engine.DnaProfile;
import com.softwaredna.architecture.ArchitectureGraph;
import com.softwaredna.codebase.model.ScannedFile;
import com.softwaredna.codebase.scan.SignalDetector;
import com.softwaredna.common.domain.DnaDimension;
import com.softwaredna.common.domain.Effort;
import com.softwaredna.common.domain.InsightKind;
import com.softwaredna.common.domain.IssueSeverity;
import com.softwaredna.hotspot.HotspotCalculator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns measurements into findings a developer can act on.
 *
 * <p>Every issue names the files it concerns and carries the numbers that
 * produced it, so the claim can be checked rather than taken on trust. Nothing
 * here is generated from a template that is not conditioned on a measured
 * value: if the evidence is absent, the finding is not emitted.
 *
 * <p>Recommendations are ranked by expected gain divided by effort, so the
 * order is the order a team would actually work in rather than the order the
 * dimensions happen to be listed.
 */
@Component
public class FindingsGenerator {

    public record Issue(
            String code,
            IssueSeverity severity,
            String category,
            DnaDimension dimension,
            String title,
            String description,
            Map<String, String> evidence,
            List<String> affectedPaths,
            List<String> relatedNodes,
            String recommendation,
            double confidence
    ) {
    }

    public record Insight(
            InsightKind kind,
            IssueSeverity severity,
            String title,
            String body,
            DnaDimension dimension,
            List<String> relatedNodeKeys,
            double confidence
    ) {
    }

    public record Recommendation(
            int rank,
            String title,
            String detail,
            Effort effort,
            double expectedGain,
            DnaDimension dimension
    ) {
    }

    public record Findings(List<Issue> issues, List<Insight> insights,
                          List<Recommendation> recommendations) {
    }

    public Findings generate(AnalysisContext context, DnaProfile profile,
                             List<HotspotCalculator.Hotspot> hotspots) {
        List<Issue> issues = new ArrayList<>();

        issues.addAll(circularDependencyIssues(context));
        issues.addAll(complexityIssues(context));
        issues.addAll(largeFileIssues(context));
        issues.addAll(couplingIssues(context));
        issues.addAll(secretIssues(context));
        issues.addAll(testingIssues(context));
        issues.addAll(dependencyIssues(context));
        issues.addAll(documentationIssues(context));
        issues.addAll(hotspotIssues(hotspots));

        issues.sort(Comparator.comparingInt((Issue issue) -> issue.severity().ordinal()));

        return new Findings(List.copyOf(issues),
                insights(profile, hotspots, context),
                recommendations(profile, issues));
    }

    // ---- Issues -----------------------------------------------------------

    private List<Issue> circularDependencyIssues(AnalysisContext context) {
        List<List<String>> cycles = context.graph().cycles();
        if (cycles.isEmpty()) {
            return List.of();
        }
        List<Issue> issues = new ArrayList<>();
        for (List<String> cycle : cycles) {
            issues.add(new Issue(
                    "ARCH_CIRCULAR_DEPENDENCY",
                    cycle.size() > 2 ? IssueSeverity.HIGH : IssueSeverity.MEDIUM,
                    "architecture",
                    DnaDimension.ARCHITECTURE,
                    "Circular dependency between %d modules".formatted(cycle.size()),
                    ("%s form a dependency cycle. Neither module can be understood, tested or "
                            + "deployed without the other, and the cycle will spread as each "
                            + "grows.").formatted(String.join(" ↔ ", cycle)),
                    Map.of("cycleLength", String.valueOf(cycle.size()),
                            "modules", String.join(", ", cycle)),
                    List.of(),
                    List.copyOf(cycle),
                    "Move the shared abstraction into a module both can depend on, so the "
                            + "dependency runs one way.",
                    100));
        }
        return issues;
    }

    private List<Issue> complexityIssues(AnalysisContext context) {
        List<ScannedFile> tooComplex = context.structurallyAnalysedFiles().stream()
                .filter(file -> file.metrics().cyclomaticComplexity() >= 30)
                .sorted(Comparator.comparingInt(
                        (ScannedFile file) -> file.metrics().cyclomaticComplexity()).reversed())
                .limit(10)
                .toList();

        if (tooComplex.isEmpty()) {
            return List.of();
        }

        ScannedFile worst = tooComplex.get(0);
        return List.of(new Issue(
                "QUALITY_HIGH_COMPLEXITY",
                worst.metrics().cyclomaticComplexity() >= 50
                        ? IssueSeverity.HIGH : IssueSeverity.MEDIUM,
                "maintainability",
                DnaDimension.MAINTAINABILITY,
                "%d %s exceed a cyclomatic complexity of 30".formatted(
                        tooComplex.size(), tooComplex.size() == 1 ? "file" : "files"),
                ("The most complex is %s at %d, with %d branches to reason about in a single "
                        + "file. Files at this level are where defects concentrate and where "
                        + "changes are most likely to have unintended effects.")
                        .formatted(worst.path(), worst.metrics().cyclomaticComplexity(),
                                worst.metrics().cyclomaticComplexity() - 1),
                Map.of("maxComplexity",
                        String.valueOf(worst.metrics().cyclomaticComplexity()),
                        "filesAffected", String.valueOf(tooComplex.size())),
                tooComplex.stream().map(ScannedFile::path).toList(),
                List.of(),
                "Extract the distinct responsibilities in %s into separate units."
                        .formatted(worst.name()),
                100));
    }

    private List<Issue> largeFileIssues(AnalysisContext context) {
        List<ScannedFile> large = context.productionFiles().stream()
                .filter(file -> file.metrics().linesOfCode() >= 800)
                .sorted(Comparator.comparingInt(
                        (ScannedFile file) -> file.metrics().linesOfCode()).reversed())
                .limit(10)
                .toList();

        if (large.isEmpty()) {
            return List.of();
        }

        ScannedFile worst = large.get(0);
        return List.of(new Issue(
                "QUALITY_LARGE_FILE",
                IssueSeverity.LOW,
                "maintainability",
                DnaDimension.MAINTAINABILITY,
                "%d %s longer than 800 lines".formatted(
                        large.size(), large.size() == 1 ? "file is" : "files are"),
                "The longest is %s at %,d lines. Files this size usually hold more than one "
                        .formatted(worst.path(), worst.metrics().linesOfCode())
                        + "responsibility, which makes them hard to navigate and to review.",
                Map.of("maxLines", String.valueOf(worst.metrics().linesOfCode()),
                        "filesAffected", String.valueOf(large.size())),
                large.stream().map(ScannedFile::path).toList(),
                List.of(),
                "Split %s along the seams where its responsibilities separate."
                        .formatted(worst.name()),
                100));
    }

    private List<Issue> couplingIssues(AnalysisContext context) {
        List<ArchitectureGraph.Node> hubs = context.graph().nodes().stream()
                .filter(node -> node.fanOut() >= 10)
                .sorted(Comparator.comparingInt(ArchitectureGraph.Node::fanOut).reversed())
                .limit(5)
                .toList();

        if (hubs.isEmpty()) {
            return List.of();
        }

        ArchitectureGraph.Node worst = hubs.get(0);
        return List.of(new Issue(
                "ARCH_HIGH_FAN_OUT",
                worst.fanOut() >= 20 ? IssueSeverity.HIGH : IssueSeverity.MEDIUM,
                "architecture",
                DnaDimension.ARCHITECTURE,
                "%d %s depend on ten or more others".formatted(
                        hubs.size(), hubs.size() == 1 ? "module" : "modules"),
                ("%s depends on %d other modules. A unit with this reach cannot be changed, "
                        + "tested or moved in isolation, and it is the usual reason a codebase "
                        + "becomes hard to work in.")
                        .formatted(worst.name(), worst.fanOut()),
                Map.of("maxFanOut", String.valueOf(worst.fanOut()),
                        "modulesAffected", String.valueOf(hubs.size())),
                List.of(),
                hubs.stream().map(ArchitectureGraph.Node::key).toList(),
                "Introduce an interface between %s and the modules it coordinates, so it "
                        .formatted(worst.name()) + "depends on fewer concrete things.",
                100));
    }

    private List<Issue> secretIssues(AnalysisContext context) {
        List<ScannedFile> withSecrets = context.scan().files().stream()
                .filter(file -> file.signal(SignalDetector.SECRET) > 0)
                .toList();

        if (withSecrets.isEmpty()) {
            return List.of();
        }

        return List.of(new Issue(
                "SEC_COMMITTED_CREDENTIAL",
                IssueSeverity.CRITICAL,
                "security",
                DnaDimension.SECURITY,
                "%d %s contain credential-shaped strings".formatted(
                        withSecrets.size(), withSecrets.size() == 1 ? "file" : "files"),
                "These files contain strings matching the key formats issued by known "
                        + "providers. A committed credential remains in the history even after "
                        + "it is removed from the working tree, so it must be rotated as well "
                        + "as deleted.",
                Map.of("filesAffected", String.valueOf(withSecrets.size())),
                withSecrets.stream().map(ScannedFile::path).toList(),
                List.of(),
                "Rotate the affected credentials, then remove them from the history.",
                85));
    }

    private List<Issue> testingIssues(AnalysisContext context) {
        if (!context.testFiles().isEmpty()) {
            return List.of();
        }
        return List.of(new Issue(
                "TEST_NO_TESTS",
                IssueSeverity.HIGH,
                "testing",
                DnaDimension.TESTING,
                "No test files were found",
                "The scan found %d source files and no test files. Without tests there is no "
                        .formatted(context.productionFiles().size())
                        + "automated check that a change preserves existing behaviour.",
                Map.of("sourceFiles", String.valueOf(context.productionFiles().size())),
                List.of(),
                List.of(),
                "Start with the files that change most often; they are where a regression "
                        + "costs the most.",
                100));
    }

    /**
     * Dependency findings.
     *
     * <p>Only ever reports what the manifests actually say. An unpinned version
     * is a fact about the declaration; a missing advisory check is reported as
     * a gap in the analysis rather than as a clean bill of health.
     */
    private List<Issue> dependencyIssues(AnalysisContext context) {
        var dependencies = context.manifests().dependencies();
        if (dependencies.isEmpty()) {
            return List.of();
        }

        List<Issue> issues = new ArrayList<>();

        List<String> unpinned = dependencies.stream()
                .filter(dependency -> {
                    String version = dependency.version();
                    return version == null || version.isBlank()
                            || version.equals("unspecified") || version.equals("managed")
                            || version.equals("*") || version.equalsIgnoreCase("latest");
                })
                .map(dependency -> dependency.name())
                .toList();

        if (!unpinned.isEmpty()) {
            double share = (unpinned.size() * 100.0) / dependencies.size();
            issues.add(new Issue(
                    "DEP_UNPINNED_VERSION",
                    share > 40 ? IssueSeverity.MEDIUM : IssueSeverity.LOW,
                    "dependencies",
                    DnaDimension.DEPENDENCIES,
                    "%d %s declared without a resolvable version".formatted(
                            unpinned.size(), unpinned.size() == 1 ? "dependency is" : "dependencies are"),
                    ("%d of %d declarations (%.0f%%) carry no version this analysis could "
                            + "resolve, usually because the version is inherited from a parent "
                            + "or managed elsewhere. A build whose versions are not visible in "
                            + "the manifest is harder to reproduce and to audit.")
                            .formatted(unpinned.size(), dependencies.size(), share),
                    Map.of("unpinned", String.valueOf(unpinned.size()),
                            "total", String.valueOf(dependencies.size()),
                            "share", "%.0f%%".formatted(share)),
                    unpinned.stream().limit(20).toList(),
                    List.of(),
                    "Record the resolved versions where they are visible to a reader of the "
                            + "manifest, through a lock file or an explicit dependency management "
                            + "block.",
                    100));
        }

        Map<String, Long> versionsPerPackage = dependencies.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        dependency -> dependency.name(),
                        java.util.stream.Collectors.mapping(
                                dependency -> dependency.version(),
                                java.util.stream.Collectors.collectingAndThen(
                                        java.util.stream.Collectors.toSet(),
                                        set -> (long) set.size()))));
        List<String> conflicting = versionsPerPackage.entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .map(Map.Entry::getKey)
                .toList();

        if (!conflicting.isEmpty()) {
            issues.add(new Issue(
                    "DEP_CONFLICTING_VERSIONS",
                    IssueSeverity.MEDIUM,
                    "dependencies",
                    DnaDimension.DEPENDENCIES,
                    "%d %s declared at more than one version".formatted(
                            conflicting.size(), conflicting.size() == 1 ? "package is" : "packages are"),
                    ("The same package is declared at different versions across manifests: %s. "
                            + "Which one wins depends on resolution order, so the build is not "
                            + "fully determined by what is written down.")
                            .formatted(String.join(", ", conflicting.stream().limit(5).toList())),
                    Map.of("packages", String.valueOf(conflicting.size())),
                    conflicting,
                    List.of(),
                    "Align the versions, or declare the intended one centrally.",
                    100));
        }

        return issues;
    }

    /** Documentation gaps, reported only when the file is genuinely absent. */
    private List<Issue> documentationIssues(AnalysisContext context) {
        boolean hasReadme = context.scan().files().stream()
                .anyMatch(file -> file.path().toLowerCase(java.util.Locale.ROOT)
                        .matches("readme(\\.[a-z]+)?"));
        if (hasReadme) {
            return List.of();
        }
        return List.of(new Issue(
                "DOC_NO_README",
                IssueSeverity.MEDIUM,
                "documentation",
                DnaDimension.DOCUMENTATION,
                "The repository has no README",
                "There is no README at the repository root. It is the first thing a new "
                        + "contributor looks for, and its absence costs every one of them the "
                        + "same rediscovery.",
                Map.of(),
                List.of(),
                List.of(),
                "Add a README covering what the project is, how to run it, and how it is "
                        + "structured.",
                100));
    }

    private List<Issue> hotspotIssues(List<HotspotCalculator.Hotspot> hotspots) {
        List<HotspotCalculator.Hotspot> severe = hotspots.stream()
                .filter(hotspot -> hotspot.riskScore() >= 55)
                .limit(5)
                .toList();

        return severe.stream().map(hotspot -> new Issue(
                "RISK_HOTSPOT",
                hotspot.riskScore() >= 72 ? IssueSeverity.HIGH : IssueSeverity.MEDIUM,
                "risk",
                DnaDimension.MAINTAINABILITY,
                "%s is a change hotspot".formatted(hotspot.name()),
                hotspot.rationale(),
                Map.of("riskScore", String.valueOf((int) hotspot.riskScore()),
                        "changes", String.valueOf(hotspot.changes()),
                        "complexity", String.valueOf(hotspot.complexityScore())),
                List.of(hotspot.path()),
                hotspot.architectureNodeKey() == null
                        ? List.of() : List.of(hotspot.architectureNodeKey()),
                hotspot.recommendation(),
                90)).toList();
    }

    // ---- Insights ---------------------------------------------------------

    private List<Insight> insights(DnaProfile profile,
                                   List<HotspotCalculator.Hotspot> hotspots,
                                   AnalysisContext context) {
        List<Insight> insights = new ArrayList<>();

        profile.dimensions().stream()
                .filter(DimensionResult::isAvailable)
                .sorted(Comparator.comparingDouble(DimensionResult::score).reversed())
                .limit(2)
                .filter(dimension -> dimension.score() >= 80)
                .forEach(dimension -> insights.add(new Insight(
                        InsightKind.STRENGTH, IssueSeverity.LOW,
                        dimension.headline(),
                        dimension.summary(),
                        dimension.dimension(),
                        List.of(),
                        dimension.confidence())));

        profile.dimensions().stream()
                .filter(DimensionResult::isAvailable)
                .sorted(Comparator.comparingDouble(DimensionResult::score))
                .limit(2)
                .filter(dimension -> dimension.score() < 70)
                .forEach(dimension -> insights.add(new Insight(
                        InsightKind.RISK,
                        dimension.score() < 45 ? IssueSeverity.HIGH : IssueSeverity.MEDIUM,
                        dimension.headline(),
                        dimension.summary(),
                        dimension.dimension(),
                        List.of(),
                        dimension.confidence())));

        if (!hotspots.isEmpty() && hotspots.get(0).riskScore() >= 50) {
            HotspotCalculator.Hotspot worst = hotspots.get(0);
            insights.add(new Insight(
                    InsightKind.RISK, IssueSeverity.HIGH,
                    "Risk concentrates in %s".formatted(worst.name()),
                    worst.rationale(),
                    DnaDimension.MAINTAINABILITY,
                    worst.architectureNodeKey() == null
                            ? List.of() : List.of(worst.architectureNodeKey()),
                    90));
        }

        if (!profile.incompleteDimensions().isEmpty()) {
            insights.add(new Insight(
                    InsightKind.OBSERVATION, IssueSeverity.INFO,
                    "Some dimensions could not be measured",
                    "%s could not be assessed from static analysis of this repository. "
                            .formatted(profile.incompleteDimensions().stream()
                                    .map(DnaDimension::label)
                                    .reduce((a, b) -> a + " and " + b).orElse(""))
                            + "They are excluded from the overall score rather than assumed.",
                    profile.incompleteDimensions().get(0),
                    List.of(),
                    100));
        }

        return List.copyOf(insights);
    }

    // ---- Recommendations --------------------------------------------------

    /**
     * Ranked by expected gain per unit of effort, so cheap wins are not buried
     * beneath expensive ones. Expected gain is the points the dimension would
     * recover if the issue were resolved, bounded by how far it is from 100.
     */
    private List<Recommendation> recommendations(DnaProfile profile, List<Issue> issues) {
        Map<DnaDimension, Double> headroom = new LinkedHashMap<>();
        profile.dimensions().stream()
                .filter(DimensionResult::isAvailable)
                .forEach(dimension -> headroom.put(dimension.dimension(),
                        100 - dimension.score()));

        record Candidate(Issue issue, Effort effort, double gain) {
        }

        List<Candidate> candidates = new ArrayList<>();
        for (Issue issue : issues) {
            // A real issue is worth acting on even when its dimension already
            // scores well; capping the gain at the remaining headroom keeps the
            // estimate honest without discarding the recommendation.
            double available = Math.max(headroom.getOrDefault(issue.dimension(), 0.0), 1.0);
            Effort effort = effortFor(issue);
            double gain = Math.min(available, switch (issue.severity()) {
                case CRITICAL -> 12;
                case HIGH -> 8;
                case MEDIUM -> 5;
                case LOW -> 3;
                case INFO -> 1;
            });
            candidates.add(new Candidate(issue, effort, gain));
        }

        candidates.sort(Comparator.comparingDouble(
                (Candidate candidate) -> candidate.gain() / effortWeight(candidate.effort()))
                .reversed());

        List<Recommendation> recommendations = new ArrayList<>();
        int rank = 1;
        for (Candidate candidate : candidates) {
            if (rank > 8) {
                break;
            }
            recommendations.add(new Recommendation(
                    rank++,
                    candidate.issue().title(),
                    candidate.issue().recommendation(),
                    candidate.effort(),
                    Math.round(candidate.gain() * 10.0) / 10.0,
                    candidate.issue().dimension()));
        }
        return List.copyOf(recommendations);
    }

    private Effort effortFor(Issue issue) {
        return switch (issue.code()) {
            case "SEC_COMMITTED_CREDENTIAL" -> Effort.LOW;
            case "ARCH_CIRCULAR_DEPENDENCY", "ARCH_HIGH_FAN_OUT" -> Effort.HIGH;
            case "TEST_NO_TESTS" -> Effort.HIGH;
            case "QUALITY_HIGH_COMPLEXITY" -> Effort.MEDIUM;
            case "QUALITY_LARGE_FILE" -> Effort.MEDIUM;
            case "DEP_UNPINNED_VERSION", "DEP_CONFLICTING_VERSIONS", "DOC_NO_README" ->
                    Effort.LOW;
            default -> Effort.MEDIUM;
        };
    }

    private double effortWeight(Effort effort) {
        return switch (effort) {
            case LOW -> 1;
            case MEDIUM -> 2.5;
            case HIGH -> 5;
        };
    }
}
