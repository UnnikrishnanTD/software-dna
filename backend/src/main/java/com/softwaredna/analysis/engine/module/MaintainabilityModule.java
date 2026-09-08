package com.softwaredna.analysis.engine.module;

import com.softwaredna.analysis.engine.AnalysisContext;
import com.softwaredna.analysis.engine.AnalysisModule;
import com.softwaredna.analysis.engine.DimensionResult;
import com.softwaredna.analysis.engine.Measurement;
import com.softwaredna.analysis.engine.ScoreCard;
import com.softwaredna.codebase.model.ScannedFile;
import com.softwaredna.common.domain.DnaDimension;
import com.softwaredna.common.util.Statistics;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Scores how hard the code is to change.
 *
 * <p><b>Formula.</b> Starting from 100:
 * <ul>
 *   <li><b>Median complexity</b> (up to −20). No penalty at 8 or below, full
 *       at 25. The median describes the file a developer typically opens.</li>
 *   <li><b>Complexity tail</b> (up to −20). Share of files at or above a
 *       cyclomatic complexity of 25: none at 2%, full at 20%.</li>
 *   <li><b>File size</b> (up to −15). Share of files over 400 lines.</li>
 *   <li><b>Nesting depth</b> (up to −15). 90th-percentile nesting: none at 3,
 *       full at 8.</li>
 *   <li><b>Churn concentration</b> (up to −15). Share of all recorded changes
 *       landing in the top 5% of files by change count — a system where every
 *       change lands in the same place is hard to work in regardless of how
 *       the code reads.</li>
 * </ul>
 *
 * <p>Only files that were structurally parsed contribute to the complexity
 * terms; a file counted by lines alone would otherwise register as trivially
 * simple and flatter the score.
 */
@Component
public class MaintainabilityModule implements AnalysisModule {

    @Override
    public DnaDimension dimension() {
        return DnaDimension.MAINTAINABILITY;
    }

    @Override
    public double weight() {
        return 0.16;
    }

    @Override
    public DimensionResult analyse(AnalysisContext context) {
        List<ScannedFile> analysed = context.structurallyAnalysedFiles();
        ScoreCard card = new ScoreCard();

        if (analysed.isEmpty()) {
            return DimensionResult.unavailable(dimension(),
                    "No files could be parsed structurally, so maintainability could not be assessed.",
                    List.of());
        }

        int[] complexity = Statistics.toIntArray(analysed,
                file -> file.metrics().cyclomaticComplexity());
        int[] lines = Statistics.toIntArray(analysed, file -> file.metrics().linesOfCode());
        int[] nesting = Statistics.toIntArray(analysed, file -> file.metrics().maxNestingDepth());

        double medianComplexity = Statistics.median(complexity);
        double complexTailShare = Statistics.shareAtOrAbove(complexity, 25);
        double largeFileShare = Statistics.shareAtOrAbove(lines, 400);
        double p90Nesting = Statistics.percentile(nesting, 90);
        double medianLines = Statistics.median(lines);

        card.measure(Measurement.of("maintainability.complexity.median", medianComplexity))
                .measure(Measurement.of("maintainability.complexity.p90",
                        Statistics.percentile(complexity, 90)))
                .measure(Measurement.of("maintainability.complexFileShare", complexTailShare, "%"))
                .measure(Measurement.of("maintainability.lines.median", medianLines))
                .measure(Measurement.of("maintainability.largeFileShare", largeFileShare, "%"))
                .measure(Measurement.of("maintainability.nesting.p90", p90Nesting))
                .measure(Measurement.of("maintainability.filesAnalysed", analysed.size()));

        card.penalise(ScoreCard.ramp(medianComplexity, 8, 25, 20), 20,
                medianComplexity <= 8 ? null
                        : "Median file complexity is %d".formatted((int) medianComplexity));
        if (medianComplexity <= 8) {
            card.commend("Median file complexity is %d".formatted((int) medianComplexity));
        }

        long veryComplex = java.util.Arrays.stream(complexity).filter(value -> value >= 25).count();
        card.penalise(ScoreCard.ramp(complexTailShare, 2, 20, 20), 20,
                veryComplex == 0 ? null
                        : "%d %s exceed a complexity of 25".formatted(
                                veryComplex, veryComplex == 1 ? "file" : "files"));

        long largeFiles = java.util.Arrays.stream(lines).filter(value -> value >= 400).count();
        card.penalise(ScoreCard.ramp(largeFileShare, 5, 30, 15), 15,
                largeFiles == 0 ? null
                        : "%d %s longer than 400 lines".formatted(
                                largeFiles, largeFiles == 1 ? "file is" : "files are"));
        if (medianLines <= 200) {
            card.commend("Median file is %d lines".formatted((int) medianLines));
        }

        card.penalise(ScoreCard.ramp(p90Nesting, 3, 8, 15), 15,
                p90Nesting <= 3 ? null
                        : "Deepest files nest control flow %d levels".formatted((int) p90Nesting));

        // --- Churn concentration ---
        var changes = context.changesByPath();
        if (changes.isEmpty()) {
            card.reduceConfidence(15, "no-history");
        } else {
            int[] changeCounts = analysed.stream()
                    .mapToInt(file -> changes.getOrDefault(file.path(), 0))
                    .toArray();
            double concentration = topShare(changeCounts, 0.05);
            card.measure(Measurement.of("maintainability.churnConcentration", concentration, "%"));
            card.penalise(ScoreCard.ramp(concentration, 30, 70, 15), 15,
                    concentration <= 30 ? null
                            : "%d%% of all changes land in the busiest 5%% of files"
                                    .formatted((int) concentration));
        }

        if (context.scan().truncated()) {
            card.reduceConfidence(20, "scan-truncated");
        }

        double score = card.score();
        return new DimensionResult(dimension(), score, card.confidence(),
                headline(score, medianComplexity, veryComplex),
                ("Across %d parsed files the median complexity is %d and the median length is "
                        + "%d lines. %d %s a complexity of 25 or more.")
                        .formatted(analysed.size(), (int) medianComplexity, (int) medianLines,
                                veryComplex, veryComplex == 1 ? "file reaches" : "files reach"),
                card.strengths(), card.watchItems(), card.measurements(), card.evidence());
    }

    /** Share of the total held by the top {@code fraction} of observations. */
    private double topShare(int[] values, double fraction) {
        if (values.length == 0) return 0;
        int[] sorted = values.clone();
        java.util.Arrays.sort(sorted);
        long total = java.util.Arrays.stream(sorted).asLongStream().sum();
        if (total == 0) return 0;
        int take = Math.max(1, (int) Math.round(sorted.length * fraction));
        long top = 0;
        for (int i = sorted.length - take; i < sorted.length; i++) {
            top += sorted[i];
        }
        return (top * 100.0) / total;
    }

    private String headline(double score, double medianComplexity, long veryComplex) {
        if (score >= 85) {
            return "Consistently readable, with complexity kept in check.";
        }
        if (veryComplex > 0) {
            return "Readable overall, with complexity pooling in %d %s.".formatted(
                    veryComplex, veryComplex == 1 ? "file" : "files");
        }
        return "Median complexity of %d leaves room to simplify."
                .formatted((int) medianComplexity);
    }
}
