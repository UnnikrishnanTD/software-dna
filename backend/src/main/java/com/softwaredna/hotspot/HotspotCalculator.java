package com.softwaredna.hotspot;

import com.softwaredna.analysis.engine.AnalysisContext;
import com.softwaredna.codebase.model.ScannedFile;
import com.softwaredna.common.domain.ComplexityBand;
import com.softwaredna.common.domain.RiskLevel;
import com.softwaredna.evolution.git.GitHistory;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Ranks the files where technical risk concentrates.
 *
 * <p>A hotspot is the intersection of complexity and change frequency. Ranking
 * by size alone finds big files; ranking by churn alone finds configuration.
 * Neither is where risk lives.
 *
 * <p><b>Composite score</b>, each term normalised to 0-1 and weighted:
 * <ul>
 *   <li><b>Change frequency</b> (28%), relative to the busiest file in the
 *       repository rather than an absolute threshold, so the ranking adapts to
 *       projects of very different ages.</li>
 *   <li><b>Complexity</b> (26%), saturating at 34.</li>
 *   <li><b>Coupling</b> (16%), the file's import count.</li>
 *   <li><b>Defect history</b> (16%), commits whose subject matches a fix
 *       convention — a heuristic, and weighted as one.</li>
 *   <li><b>Authorship spread</b> (14%), distinct authors touching the file.</li>
 * </ul>
 *
 * <p>Coverage is absent from the formula because it cannot be measured. Where
 * a coverage report is present it is attached to the result for display, but
 * it never silently changes the ranking of a repository that has none.
 */
@Component
public class HotspotCalculator {

    /** A file with no history and no complexity cannot be a hotspot. */
    private static final double MINIMUM_SCORE = 8.0;

    public record Hotspot(
            String path,
            String name,
            int changes,
            int complexityScore,
            ComplexityBand complexityBand,
            int dependencies,
            int bugFixes,
            int linesOfCode,
            Double coverage,
            int contributors,
            double riskScore,
            RiskLevel severity,
            String rationale,
            String recommendation,
            String architectureNodeKey
    ) {
    }

    public List<Hotspot> calculate(AnalysisContext context, int limit) {
        List<ScannedFile> candidates = context.productionFiles();
        if (candidates.isEmpty()) {
            return List.of();
        }

        Map<String, GitHistory.FileHistory> histories = context.history().fileHistories();

        int busiestFile = histories.values().stream()
                .mapToInt(GitHistory.FileHistory::changes)
                .max().orElse(1);

        Map<String, String> nodeOfFile = nodeIndex(context);

        return candidates.stream()
                .map(file -> toHotspot(file, histories.get(file.path()), busiestFile,
                        context, nodeOfFile))
                .filter(hotspot -> hotspot.riskScore() >= MINIMUM_SCORE)
                .sorted(Comparator.comparingDouble(Hotspot::riskScore).reversed())
                .limit(limit)
                .toList();
    }

    private Hotspot toHotspot(ScannedFile file, GitHistory.FileHistory history,
                              int busiestFile, AnalysisContext context,
                              Map<String, String> nodeOfFile) {
        int changes = history == null ? 0 : history.changes();
        int bugFixes = history == null ? 0 : history.bugFixCommits();
        int contributors = history == null ? 0 : history.distinctAuthors();

        int complexity = file.metrics().cyclomaticComplexity();
        int dependencies = file.metrics().importCount();

        double churnTerm = busiestFile == 0 ? 0 : Math.min(changes / (double) busiestFile, 1);
        double complexityTerm = Math.min(complexity / 34.0, 1);
        double couplingTerm = Math.min(dependencies / 24.0, 1);
        double defectTerm = Math.min(bugFixes / 8.0, 1);
        double spreadTerm = Math.min(contributors / 8.0, 1);

        double composite = churnTerm * 0.28
                + complexityTerm * 0.26
                + couplingTerm * 0.16
                + defectTerm * 0.16
                + spreadTerm * 0.14;

        double riskScore = Math.round(composite * 100.0 * 100.0) / 100.0;
        ComplexityBand band = ComplexityBand.forScore(complexity);
        Double coverage = context.coverageByPath().get(file.path());

        return new Hotspot(
                file.path(),
                file.name(),
                changes,
                complexity,
                band,
                dependencies,
                bugFixes,
                file.metrics().linesOfCode(),
                coverage,
                contributors,
                riskScore,
                RiskLevel.forScore(riskScore),
                rationale(file, changes, complexity, bugFixes, dependencies, busiestFile),
                recommendation(file, complexity, bugFixes, dependencies),
                nodeOfFile.get(file.path()));
    }

    /**
     * States which measured signals put this file on the list. Every clause is
     * conditional on the number that justifies it, so the explanation cannot
     * drift from the data.
     */
    private String rationale(ScannedFile file, int changes, int complexity,
                             int bugFixes, int dependencies, int busiestFile) {
        StringBuilder reason = new StringBuilder();

        boolean highChurn = busiestFile > 0 && changes >= busiestFile * 0.5;
        boolean highComplexity = complexity >= 20;

        if (highChurn && highComplexity) {
            reason.append("This file changes often (%d commits) and is structurally complex "
                    .formatted(changes))
                    .append("(cyclomatic complexity %d). Those two properties together are ".formatted(complexity))
                    .append("what makes a file risky: every change is both likely and hard to reason about.");
        } else if (highComplexity) {
            reason.append("Cyclomatic complexity of %d makes this file hard to change safely."
                    .formatted(complexity));
        } else if (highChurn) {
            reason.append("This file has changed %d times, more than most of the repository."
                    .formatted(changes));
        } else {
            reason.append("This file combines moderate complexity (%d) with %d recorded changes."
                    .formatted(complexity, changes));
        }

        if (bugFixes > 0) {
            reason.append(" %d of those commits look like fixes based on their subject line."
                    .formatted(bugFixes));
        }
        if (dependencies >= 15) {
            reason.append(" It imports %d modules, so a change here reaches widely."
                    .formatted(dependencies));
        }
        return reason.toString();
    }

    private String recommendation(ScannedFile file, int complexity, int bugFixes,
                                  int dependencies) {
        if (complexity >= 28 && dependencies >= 15) {
            return "Split %s along its responsibilities before changing it further; it is both "
                    .formatted(file.name())
                    + "the most tangled and the most widely connected file in this group.";
        }
        if (complexity >= 28) {
            return "Extract the branching in %s into smaller units so each path can be "
                    .formatted(file.name()) + "reasoned about and tested independently.";
        }
        if (bugFixes >= 3) {
            return "Cover the paths in %s that have needed fixes before making further changes."
                    .formatted(file.name());
        }
        if (dependencies >= 15) {
            return "Reduce the %d imports in %s; its coupling is what spreads the cost of a change."
                    .formatted(dependencies, file.name());
        }
        return "Keep an eye on %s: it is changing steadily and is worth covering before it grows."
                .formatted(file.name());
    }

    private Map<String, String> nodeIndex(AnalysisContext context) {
        Map<String, String> index = new java.util.HashMap<>();
        context.graph().filesByNode().forEach((node, paths) ->
                paths.forEach(path -> index.put(path, node)));
        return index;
    }
}
