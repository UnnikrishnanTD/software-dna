package com.softwaredna.ai;

import com.softwaredna.api.dto.AnalysisDtos;
import com.softwaredna.api.dto.DoctorDtos;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Answers from the analysis itself, with no language model involved.
 *
 * <p>This is the default provider, and it is not a placeholder. Every figure it
 * quotes is read out of the stored analysis, so its answers are correct by
 * construction and cost nothing. It reports itself as {@code deterministic} on
 * every message, so a caller can always tell that no model was consulted.
 *
 * <p>Intent matching is keyword-based. When nothing matches, it says so rather
 * than producing a plausible-sounding answer to a question it did not
 * understand.
 */
@Component
public class DeterministicAiProvider implements AiProvider {

    private enum Intent {
        PRIORITY, RISK, ARCHITECTURE, GRAPH, HISTORY, TESTING, SECURITY,
        PERFORMANCE, DEPENDENCIES, SUMMARY, UNKNOWN
    }

    private static final List<Map.Entry<Intent, List<String>>> KEYWORDS = List.of(
            Map.entry(Intent.PRIORITY, List.of("fix first", "priority", "prioritise",
                    "prioritize", "where do i start", "what should i do", "next step")),
            Map.entry(Intent.RISK, List.of("biggest risk", "riskiest", "most risk",
                    "dangerous", "hotspot", "worst file")),
            Map.entry(Intent.GRAPH, List.of("graph", "dependency map", "topology",
                    "connected", "coupling")),
            Map.entry(Intent.HISTORY, List.of("last year", "changed over", "history",
                    "evolution", "trend", "over time")),
            Map.entry(Intent.TESTING, List.of("test", "coverage", "spec", "untested")),
            Map.entry(Intent.SECURITY, List.of("security", "vulnerab", "advisor", "cve",
                    "secure", "secret")),
            Map.entry(Intent.PERFORMANCE, List.of("performance", "slow", "latency", "fast")),
            Map.entry(Intent.DEPENDENCIES, List.of("dependenc", "package", "library",
                    "outdated", "upgrade")),
            Map.entry(Intent.ARCHITECTURE, List.of("architecture", "structure", "modular",
                    "design", "layer")),
            Map.entry(Intent.SUMMARY, List.of("summary", "overview", "how healthy",
                    "overall", "tell me about")));

    @Override
    public String name() {
        return "deterministic";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public DoctorDtos.DoctorMessage answer(AnalysisDtos.RepositoryAnalysisDto analysis,
                                           String question) {
        Intent intent = detect(question);
        List<DoctorDtos.DoctorBlock> blocks = new ArrayList<>();
        String lead;

        AnalysisDtos.DnaDimensionDto strongest = extreme(analysis, true);
        AnalysisDtos.DnaDimensionDto weakest = extreme(analysis, false);

        switch (intent) {
            case PRIORITY -> {
                lead = "Fix them in this order. The ranking weighs how much each change moves "
                        + "the score against how much work it is, so the cheap wins are not "
                        + "buried underneath the expensive ones.";
                if (analysis.remediation().isEmpty()) {
                    blocks.add(new DoctorDtos.TextBlock(
                            "The analysis produced no remediation steps: nothing it measured "
                                    + "rose to the level of a recommendation."));
                } else {
                    blocks.add(new DoctorDtos.PlanBlock(
                            analysis.remediation().stream().limit(4).toList()));
                }
            }
            case RISK -> {
                lead = "Risk is concentrated, not spread out.";
                List<AnalysisDtos.HotspotDto> top = analysis.hotspots().stream()
                        .limit(3).toList();
                if (top.isEmpty()) {
                    blocks.add(new DoctorDtos.TextBlock(
                            "No file combines enough complexity and change to register as a "
                                    + "hotspot."));
                } else {
                    blocks.add(new DoctorDtos.NodesBlock("Highest-risk units",
                            top.stream().map(AnalysisDtos.HotspotDto::architectureNodeId)
                                    .filter(java.util.Objects::nonNull).toList()));
                    top.forEach(hotspot -> blocks.add(new DoctorDtos.MetricBlock(
                            hotspot.name() + " risk score", hotspot.riskScore(),
                            100 - hotspot.riskScore())));
                    blocks.add(new DoctorDtos.TextBlock(top.get(0).rationale()));
                    blocks.add(new DoctorDtos.ListBlock(false, top.stream()
                            .map(hotspot -> "%s — %d changes, complexity %d, %d imports"
                                    .formatted(hotspot.name(), hotspot.changes(),
                                            hotspot.complexityScore(), hotspot.dependencies()))
                            .toList()));
                }
            }
            case ARCHITECTURE -> {
                AnalysisDtos.DnaDimensionDto architecture = dimension(analysis, "architecture");
                lead = architecture == null || architecture.score() == null
                        ? "Architecture could not be assessed for this repository."
                        : "Architecture scores %.0f. %s".formatted(
                                architecture.score(), architecture.headline());
                if (architecture != null) {
                    blocks.add(new DoctorDtos.MetricBlock("Architecture",
                            architecture.score() == null ? 0 : architecture.score(),
                            architecture.score() == null ? 0 : architecture.score()));
                    blocks.add(new DoctorDtos.TextBlock(architecture.summary()));
                    if (!architecture.watchItems().isEmpty()) {
                        blocks.add(new DoctorDtos.ListBlock(false, architecture.watchItems()));
                    }
                }
                List<List<String>> cycles = analysis.architecture().circularDependencies();
                blocks.add(new DoctorDtos.ListBlock(false, List.of(
                        "%d modules across %d relationships".formatted(
                                analysis.architecture().nodes().size(),
                                analysis.architecture().edges().size()),
                        cycles.isEmpty() ? "No circular dependencies"
                                : "%d circular %s: %s".formatted(cycles.size(),
                                        cycles.size() == 1 ? "dependency" : "dependencies",
                                        cycles.stream().map(cycle -> String.join(" ↔ ", cycle))
                                                .reduce((a, b) -> a + ", " + b).orElse("")))));
            }
            case GRAPH -> {
                lead = "The graph has %d modules and %d relationships, arranged in layers."
                        .formatted(analysis.architecture().nodes().size(),
                                analysis.architecture().edges().size());
                List<AnalysisDtos.ArchitectureNodeDto> hubs =
                        analysis.architecture().nodes().stream()
                                .sorted(Comparator.comparingInt(
                                        AnalysisDtos.ArchitectureNodeDto::fanOut).reversed())
                                .limit(3).toList();
                if (!hubs.isEmpty()) {
                    blocks.add(new DoctorDtos.NodesBlock("The widest hubs",
                            hubs.stream().map(AnalysisDtos.ArchitectureNodeDto::id).toList()));
                    blocks.add(new DoctorDtos.ListBlock(false, hubs.stream()
                            .map(node -> "%s — depends on %d, depended on by %d"
                                    .formatted(node.name(), node.fanOut(), node.fanIn()))
                            .toList()));
                }
            }
            case HISTORY -> {
                List<AnalysisDtos.EvolutionPointDto> points = analysis.evolution().points();
                if (points.size() < 2) {
                    lead = "There is not enough history to describe a trend.";
                } else {
                    var previous = points.get(points.size() - 2);
                    var latest = points.get(points.size() - 1);
                    lead = "Between %d and %d the repository went from %d commits to %d."
                            .formatted(previous.year(), latest.year(),
                                    previous.commits(), latest.commits());
                    blocks.add(new DoctorDtos.ListBlock(false, List.of(
                            "Active contributors: %d → %d".formatted(
                                    previous.contributors(), latest.contributors()),
                            "Commits: %d → %d".formatted(previous.commits(), latest.commits()))));
                }
                if (!analysis.evolution().milestones().isEmpty()) {
                    blocks.add(new DoctorDtos.ListBlock(false,
                            analysis.evolution().milestones().stream()
                                    .sorted(Comparator.comparingInt(
                                            AnalysisDtos.EvolutionMilestoneDto::year).reversed())
                                    .limit(4)
                                    .map(milestone -> "%d: %s — %s".formatted(milestone.year(),
                                            milestone.title(), milestone.description()))
                                    .toList()));
                }
            }
            case TESTING -> {
                AnalysisDtos.DnaDimensionDto testing = dimension(analysis, "testing");
                lead = testing == null || testing.score() == null
                        ? "Testing could not be assessed."
                        : "Testing scores %.0f. %s".formatted(testing.score(),
                                testing.headline());
                if (testing != null) {
                    blocks.add(new DoctorDtos.MetricBlock("Testing",
                            testing.score() == null ? 0 : testing.score(),
                            testing.score() == null ? 0 : testing.score()));
                    blocks.add(new DoctorDtos.TextBlock(testing.summary()));
                }
                if (analysis.stats().testCoverage() == null) {
                    blocks.add(new DoctorDtos.TextBlock(
                            "Line coverage is not available: no coverage report is committed "
                                    + "to the repository, and the analyser does not execute "
                                    + "repository code to produce one."));
                }
            }
            case SECURITY -> {
                AnalysisDtos.DnaDimensionDto security = dimension(analysis, "security");
                lead = security == null || security.score() == null
                        ? "Security could not be assessed."
                        : "Security scores %.0f. %s".formatted(security.score(),
                                security.headline());
                if (security != null) {
                    blocks.add(new DoctorDtos.MetricBlock("Security",
                            security.score() == null ? 0 : security.score(),
                            security.score() == null ? 0 : security.score()));
                    blocks.add(new DoctorDtos.TextBlock(security.summary()));
                    if (!security.watchItems().isEmpty()) {
                        blocks.add(new DoctorDtos.ListBlock(false, security.watchItems()));
                    }
                }
            }
            case PERFORMANCE -> {
                AnalysisDtos.DnaDimensionDto performance = dimension(analysis, "performance");
                lead = performance == null || performance.score() == null
                        ? "Performance could not be assessed."
                        : "Performance scores %.0f. %s".formatted(performance.score(),
                                performance.headline());
                if (performance != null) {
                    blocks.add(new DoctorDtos.TextBlock(performance.summary()));
                    if (!performance.watchItems().isEmpty()) {
                        blocks.add(new DoctorDtos.ListBlock(false, performance.watchItems()));
                    }
                }
            }
            case DEPENDENCIES -> {
                var profile = analysis.dependencies();
                lead = "%d dependencies were read from the repository's manifests."
                        .formatted(profile.total());
                List<String> facts = new ArrayList<>();
                facts.add("%d direct".formatted(profile.direct()));
                facts.add(profile.transitive() == null
                        ? "Transitive dependencies were not resolved: that needs a registry "
                                + "lookup, which is not configured"
                        : "%d transitive".formatted(profile.transitive()));
                facts.add(profile.outdated() == null
                        ? "Available updates were not checked"
                        : "%d with an update available".formatted(profile.outdated()));
                facts.add(profile.advisories() == null
                        ? "No vulnerability database was consulted, so advisories are unknown"
                        : "%d open advisories".formatted(profile.advisories()));
                blocks.add(new DoctorDtos.ListBlock(false, facts));
            }
            case SUMMARY -> {
                lead = "%s scores %.0f overall.".formatted(
                        analysis.repository().displayName(), analysis.dna().overall());
                addExtremes(blocks, strongest, weakest);
                if (!analysis.remediation().isEmpty()) {
                    blocks.add(new DoctorDtos.PlanBlock(
                            analysis.remediation().stream().limit(3).toList()));
                }
            }
            default -> {
                lead = "I can only reason about what the analysis measured, and I do not have "
                        + "a confident answer to that. Here is what I can tell you about %s."
                        .formatted(analysis.repository().displayName());
                addExtremes(blocks, strongest, weakest);
                blocks.add(new DoctorDtos.ListBlock(false, List.of(
                        "Why a dimension scores the way it does",
                        "What to fix first, and in what order",
                        "Which files carry the most risk",
                        "How the graph is shaped and where change propagates",
                        "What changed over the last year")));
            }
        }

        return new DoctorDtos.DoctorMessage(
                "doctor-" + UUID.randomUUID(), "doctor", lead,
                List.copyOf(blocks), System.currentTimeMillis(), name());
    }

    private void addExtremes(List<DoctorDtos.DoctorBlock> blocks,
                             AnalysisDtos.DnaDimensionDto strongest,
                             AnalysisDtos.DnaDimensionDto weakest) {
        if (strongest != null && strongest.score() != null) {
            blocks.add(new DoctorDtos.MetricBlock("Strongest — " + strongest.label(),
                    strongest.score(), strongest.score()));
        }
        if (weakest != null && weakest.score() != null) {
            blocks.add(new DoctorDtos.MetricBlock("Weakest — " + weakest.label(),
                    weakest.score(), weakest.score()));
        }
    }

    private Intent detect(String question) {
        String text = question.toLowerCase(Locale.ROOT);
        for (var entry : KEYWORDS) {
            for (String keyword : entry.getValue()) {
                if (text.contains(keyword)) {
                    return entry.getKey();
                }
            }
        }
        return Intent.UNKNOWN;
    }

    private AnalysisDtos.DnaDimensionDto dimension(AnalysisDtos.RepositoryAnalysisDto analysis,
                                                   String key) {
        return analysis.dna().dimensions().stream()
                .filter(dimension -> dimension.key().wireValue().equals(key))
                .findFirst().orElse(null);
    }

    private AnalysisDtos.DnaDimensionDto extreme(AnalysisDtos.RepositoryAnalysisDto analysis,
                                                 boolean highest) {
        Comparator<AnalysisDtos.DnaDimensionDto> byScore =
                Comparator.comparingDouble(dimension -> dimension.score());
        return analysis.dna().dimensions().stream()
                .filter(dimension -> dimension.score() != null)
                .sorted(highest ? byScore.reversed() : byScore)
                .findFirst().orElse(null);
    }
}
