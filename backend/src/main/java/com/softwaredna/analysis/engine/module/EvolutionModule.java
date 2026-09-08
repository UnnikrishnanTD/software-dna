package com.softwaredna.analysis.engine.module;

import com.softwaredna.analysis.engine.AnalysisContext;
import com.softwaredna.analysis.engine.AnalysisModule;
import com.softwaredna.analysis.engine.DimensionResult;
import com.softwaredna.analysis.engine.Measurement;
import com.softwaredna.analysis.engine.ScoreCard;
import com.softwaredna.common.domain.DnaDimension;
import com.softwaredna.evolution.git.GitHistory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Scores how the system has developed and who develops it.
 *
 * <p>Every input is read from the commit graph, so this dimension is among the
 * most reliable in the profile.
 *
 * <p><b>Formula.</b> Starting from 100:
 * <ul>
 *   <li><b>Bus factor</b> (up to −30). Share of commits by the single most
 *       active author: no penalty at 35% or below, full penalty at 90%. A
 *       repository one person could take with them is fragile whatever its
 *       code looks like.</li>
 *   <li><b>Contributor base</b> (up to −20). Full marks at 8 or more distinct
 *       authors.</li>
 *   <li><b>Recency</b> (up to −25). Days since the last commit: none within
 *       60 days, full at 730.</li>
 *   <li><b>History depth</b> (up to −15). Full marks at two years; a young
 *       repository has less to reason from, so confidence drops too.</li>
 *   <li><b>Cadence consistency</b> (up to −10). Share of months in the last
 *       two years with no commits.</li>
 * </ul>
 */
@Component
public class EvolutionModule implements AnalysisModule {

    @Override
    public DnaDimension dimension() {
        return DnaDimension.EVOLUTION;
    }

    @Override
    public double weight() {
        return 0.10;
    }

    @Override
    public DimensionResult analyse(AnalysisContext context) {
        GitHistory history = context.history();
        ScoreCard card = new ScoreCard();

        if (history.isEmpty()) {
            return DimensionResult.unavailable(dimension(),
                    "No commit history was available, so evolution could not be assessed.",
                    List.of());
        }

        List<GitHistory.AuthorStats> authors = history.authors();
        int contributors = authors.size();
        int commits = history.commitsScanned();

        card.measure(Measurement.of("evolution.commits", history.totalCommits()))
                .measure(Measurement.of("evolution.commitsScanned", commits))
                .measure(Measurement.of("evolution.contributors", contributors));

        // --- Bus factor ---
        int topAuthorCommits = authors.stream()
                .mapToInt(GitHistory.AuthorStats::commits)
                .max().orElse(0);
        double topAuthorShare = commits == 0 ? 0 : (topAuthorCommits * 100.0) / commits;
        card.measure(Measurement.of("evolution.topAuthorShare", topAuthorShare, "%"));

        card.penalise(ScoreCard.ramp(topAuthorShare, 35, 90, 30), 30,
                topAuthorShare <= 35 ? null
                        : "One contributor accounts for %d%% of all commits"
                                .formatted((int) topAuthorShare));
        if (topAuthorShare <= 35 && contributors > 2) {
            card.commend("No contributor accounts for more than %d%% of commits"
                    .formatted((int) topAuthorShare));
        }

        // --- Contributor base ---
        card.penalise(ScoreCard.inverseRamp(contributors, 8, 1, 20), 20,
                contributors >= 8 ? null
                        : "%d distinct %s in the analysed history".formatted(
                                contributors, contributors == 1 ? "contributor" : "contributors"));
        if (contributors >= 8) {
            card.commend("%d distinct contributors".formatted(contributors));
        }

        // --- Recency ---
        Instant last = history.lastCommitAt();
        long daysSinceLastCommit = last == null ? 0
                : Duration.between(last, Instant.now()).toDays();
        card.measure(Measurement.of("evolution.daysSinceLastCommit", daysSinceLastCommit, "days"));
        card.penalise(ScoreCard.ramp(daysSinceLastCommit, 60, 730, 25), 25,
                daysSinceLastCommit <= 60 ? null
                        : "The last commit was %d days ago".formatted(daysSinceLastCommit));
        if (daysSinceLastCommit <= 30) {
            card.commend("Actively maintained; last commit %d %s ago".formatted(
                    daysSinceLastCommit, daysSinceLastCommit == 1 ? "day" : "days"));
        }

        // --- History depth ---
        Instant first = history.firstCommitAt();
        double years = first == null || last == null ? 0
                : Duration.between(first, last).toDays() / 365.0;
        card.measure(Measurement.of("evolution.historyYears", years, "years"));
        card.penalise(ScoreCard.inverseRamp(years, 2, 0, 15), 15,
                years >= 2 ? null
                        : "Only %.1f years of history to reason from".formatted(years));
        if (years < 1) {
            card.reduceConfidence(20, "short-history");
        }

        // --- Cadence ---
        int activeYears = history.byYear().size();
        card.measure(Measurement.of("evolution.activeYears", activeYears));
        double commitsPerYear = activeYears == 0 ? 0 : commits / (double) activeYears;
        card.measure(Measurement.of("evolution.commitsPerYear", commitsPerYear));

        if (history.truncated()) {
            card.reduceConfidence(15, "history-truncated");
        }

        double score = card.score();
        return new DimensionResult(dimension(), score, card.confidence(),
                headline(contributors, topAuthorShare, daysSinceLastCommit),
                summary(history, contributors, topAuthorShare, years),
                card.strengths(), card.watchItems(), card.measurements(), card.evidence());
    }

    private String headline(int contributors, double topShare, long daysSince) {
        if (daysSince > 365) {
            return "Little recent activity; the last commit was over a year ago.";
        }
        if (topShare > 70) {
            return "Actively developed, but ownership sits with one contributor.";
        }
        if (contributors >= 8) {
            return "Healthy, sustained development with broad ownership.";
        }
        return "Steady development within a small team.";
    }

    private String summary(GitHistory history, int contributors, double topShare, double years) {
        String truncation = history.truncated()
                ? " History was truncated at %d of %d commits, so these figures cover the most "
                        .formatted(history.commitsScanned(), history.totalCommits())
                        + "recent portion of the project."
                : "";

        String topAuthor = history.authors().stream()
                .max(Comparator.comparingInt(GitHistory.AuthorStats::commits))
                .map(GitHistory.AuthorStats::displayName)
                .orElse("one contributor");

        return ("%,d commits from %d %s over %.1f years. The most active contributor, %s, "
                + "accounts for %d%% of them.%s")
                .formatted(history.totalCommits(), contributors,
                        contributors == 1 ? "contributor" : "contributors",
                        years, topAuthor, (int) topShare, truncation);
    }
}
