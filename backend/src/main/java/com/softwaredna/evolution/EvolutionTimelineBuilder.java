package com.softwaredna.evolution;

import com.softwaredna.analysis.engine.AnalysisContext;
import com.softwaredna.analysis.engine.DnaProfile;
import com.softwaredna.common.domain.MilestoneKind;
import com.softwaredna.evolution.git.GitHistory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the evolution timeline from the commit graph.
 *
 * <p><b>What is real and what is absent.</b> Every per-year figure here —
 * commits, active contributors, files touched — is counted from history and is
 * exact. Per-year <em>dimension scores</em> are not: reconstructing them would
 * mean checking out each year's tree and re-running the whole engine against
 * it, which multiplies analysis time by the age of the repository. Rather than
 * interpolate or invent them, the timeline carries scores only for the year the
 * analysis actually ran, and leaves earlier years' score maps empty.
 *
 * <p>Milestones are derived from measurable inflections in the history, and
 * each one records the evidence that produced it.
 */
@Component
public class EvolutionTimelineBuilder {

    public record TimelinePoint(
            int year,
            Double overall,
            int contributors,
            int commits,
            int linesOfCode,
            int modules,
            int hotspots,
            Double testCoverage,
            Map<String, Double> scores
    ) {
    }

    public record Milestone(
            int year,
            double offsetInYear,
            MilestoneKind kind,
            String title,
            String description,
            double impact,
            Map<String, String> evidence
    ) {
    }

    public record Timeline(List<TimelinePoint> points, List<Milestone> milestones) {
    }

    public Timeline build(AnalysisContext context, DnaProfile profile, int hotspotCount) {
        GitHistory history = context.history();
        if (history.isEmpty()) {
            return new Timeline(List.of(), List.of());
        }

        List<GitHistory.YearStats> years = history.byYear().values().stream()
                .sorted(Comparator.comparingInt(GitHistory.YearStats::year))
                .toList();

        int currentYear = years.isEmpty() ? 0 : years.get(years.size() - 1).year();

        List<TimelinePoint> points = new ArrayList<>(years.size());
        for (GitHistory.YearStats year : years) {
            boolean isCurrent = year.year() == currentYear;

            // Only the year the analysis ran carries dimension scores; earlier
            // years would require re-analysing their trees.
            Map<String, Double> scores = new LinkedHashMap<>();
            if (isCurrent) {
                profile.dimensions().stream()
                        .filter(dimension -> dimension.isAvailable())
                        .forEach(dimension -> scores.put(
                                dimension.dimension().wireValue(), dimension.score()));
            }

            points.add(new TimelinePoint(
                    year.year(),
                    isCurrent ? profile.overall() : null,
                    year.activeAuthors(),
                    year.commits(),
                    isCurrent ? context.scan().totalLinesOfCode() : 0,
                    isCurrent ? context.graph().nodes().size() : 0,
                    isCurrent ? hotspotCount : 0,
                    isCurrent && context.hasCoverage() ? averageCoverage(context) : null,
                    Map.copyOf(scores)));
        }

        return new Timeline(List.copyOf(points), detectMilestones(years, history));
    }

    /**
     * Detects inflections that are visible in the commit record itself.
     *
     * <p>Each milestone states the numbers behind it, so a reader can check the
     * claim. Nothing is emitted that the history does not support.
     */
    private List<Milestone> detectMilestones(List<GitHistory.YearStats> years,
                                             GitHistory history) {
        List<Milestone> milestones = new ArrayList<>();
        if (years.isEmpty()) {
            return milestones;
        }

        GitHistory.YearStats first = years.get(0);
        milestones.add(new Milestone(first.year(), 0.1, MilestoneKind.ARCHITECTURE,
                "Repository history begins",
                "The earliest commit in the analysed history dates from %d, with %d %s that year."
                        .formatted(first.year(), first.commits(),
                                first.commits() == 1 ? "commit" : "commits"),
                0,
                Map.of("commits", String.valueOf(first.commits()))));

        for (int i = 1; i < years.size(); i++) {
            GitHistory.YearStats previous = years.get(i - 1);
            GitHistory.YearStats current = years.get(i);

            // --- Contributor growth ---
            if (previous.activeAuthors() > 0
                    && current.activeAuthors() >= previous.activeAuthors() * 2
                    && current.activeAuthors() >= 3) {
                milestones.add(new Milestone(current.year(), 0.4, MilestoneKind.SCALE,
                        "Contributor base grew",
                        "Active contributors rose from %d to %d."
                                .formatted(previous.activeAuthors(), current.activeAuthors()),
                        4,
                        Map.of("from", String.valueOf(previous.activeAuthors()),
                                "to", String.valueOf(current.activeAuthors()))));
            }

            // --- Commit surge ---
            if (previous.commits() > 10 && current.commits() >= previous.commits() * 2) {
                milestones.add(new Milestone(current.year(), 0.55, MilestoneKind.SCALE,
                        "Development accelerated",
                        "Commits more than doubled, from %d to %d."
                                .formatted(previous.commits(), current.commits()),
                        3,
                        Map.of("from", String.valueOf(previous.commits()),
                                "to", String.valueOf(current.commits()))));
            }

            // --- Broad restructuring ---
            if (previous.filesTouched() > 0
                    && current.filesTouched() >= previous.filesTouched() * 2
                    && current.filesTouched() > 40) {
                milestones.add(new Milestone(current.year(), 0.3, MilestoneKind.ARCHITECTURE,
                        "Large-scale restructuring",
                        "%d distinct files changed, up from %d the year before."
                                .formatted(current.filesTouched(), previous.filesTouched()),
                        2,
                        Map.of("filesTouched", String.valueOf(current.filesTouched()))));
            }

            // --- Defect pressure ---
            double fixShare = current.commits() == 0 ? 0
                    : (current.bugFixCommits() * 100.0) / current.commits();
            if (fixShare > 35 && current.commits() > 20) {
                milestones.add(new Milestone(current.year(), 0.7, MilestoneKind.INCIDENT,
                        "High proportion of fixes",
                        "%d%% of commits that year were fixes, against %d total."
                                .formatted((int) fixShare, current.commits()),
                        -3,
                        Map.of("fixShare", "%d%%".formatted((int) fixShare))));
            }

            // --- Dormancy ---
            if (current.year() - previous.year() > 1) {
                milestones.add(new Milestone(previous.year() + 1, 0.5, MilestoneKind.QUALITY,
                        "No recorded activity",
                        "No commits between %d and %d."
                                .formatted(previous.year(), current.year()),
                        -2,
                        Map.of("gapYears",
                                String.valueOf(current.year() - previous.year() - 1))));
            }
        }

        milestones.sort(Comparator.comparingInt(Milestone::year)
                .thenComparingDouble(Milestone::offsetInYear));
        return List.copyOf(milestones);
    }

    private Double averageCoverage(AnalysisContext context) {
        return context.coverageByPath().values().stream()
                .mapToDouble(Double::doubleValue).average().orElse(0);
    }
}
