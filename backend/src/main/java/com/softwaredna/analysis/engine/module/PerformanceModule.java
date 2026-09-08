package com.softwaredna.analysis.engine.module;

import com.softwaredna.analysis.engine.AnalysisContext;
import com.softwaredna.analysis.engine.AnalysisModule;
import com.softwaredna.analysis.engine.DimensionResult;
import com.softwaredna.analysis.engine.Measurement;
import com.softwaredna.analysis.engine.ScoreCard;
import com.softwaredna.codebase.model.ScannedFile;
import com.softwaredna.codebase.scan.SignalDetector;
import com.softwaredna.common.domain.DnaDimension;
import com.softwaredna.common.util.Statistics;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Scores performance risk visible in the source.
 *
 * <p><b>The honest caveat, stated up front.</b> Runtime performance cannot be
 * measured without running the system, and this analyser never executes
 * repository code. What follows are static patterns that correlate with
 * performance problems — not measurements of latency, throughput or memory.
 * The dimension therefore starts with its confidence capped at 60, and its
 * summary says so, so a reader never mistakes it for a benchmark.
 *
 * <p><b>Formula.</b> Starting from 100:
 * <ul>
 *   <li><b>Awaiting inside loops</b> (up to −25). Serialises work that the
 *       code is otherwise structured to do concurrently.</li>
 *   <li><b>Blocking IO</b> (up to −20). Synchronous file and process calls,
 *       and thread sleeps, on paths that usually serve requests.</li>
 *   <li><b>Unbounded queries</b> (up to −15). {@code SELECT *} without a
 *       projection.</li>
 *   <li><b>Very deep nesting</b> (up to −15). 95th-percentile nesting above 5,
 *       a proxy for nested iteration.</li>
 *   <li><b>Oversized modules</b> (up to −15). Share of code concentrated in
 *       the single largest module, which bounds how little can be loaded.</li>
 * </ul>
 */
@Component
public class PerformanceModule implements AnalysisModule {

    /** Static analysis alone cannot justify a high-confidence performance claim. */
    private static final double CONFIDENCE_CEILING = 60;

    @Override
    public DnaDimension dimension() {
        return DnaDimension.PERFORMANCE;
    }

    @Override
    public double weight() {
        return 0.10;
    }

    @Override
    public DimensionResult analyse(AnalysisContext context) {
        List<ScannedFile> production = context.productionFiles();
        ScoreCard card = new ScoreCard();

        if (production.isEmpty()) {
            return DimensionResult.unavailable(dimension(),
                    "No production source was found, so performance signals could not be assessed.",
                    List.of());
        }

        long awaitInLoop = production.stream()
                .mapToLong(file -> file.signal(SignalDetector.AWAIT_LOOP)).sum();
        long blockingIo = production.stream()
                .mapToLong(file -> file.signal(SignalDetector.BLOCKING_IO_KEY)).sum();
        long selectStar = context.scan().files().stream()
                .mapToLong(file -> file.signal(SignalDetector.SELECT_STAR_KEY)).sum();

        card.measure(Measurement.of("performance.awaitInLoop", awaitInLoop))
                .measure(Measurement.of("performance.blockingIo", blockingIo))
                .measure(Measurement.of("performance.selectStar", selectStar));

        card.penalise(awaitInLoop * 4.0, 25,
                awaitInLoop == 0 ? null
                        : "%d %s await inside a loop".formatted(
                                awaitInLoop, awaitInLoop == 1 ? "site awaits" : "sites"));
        card.penalise(blockingIo * 3.0, 20,
                blockingIo == 0 ? null
                        : "%d blocking IO or sleep %s".formatted(
                                blockingIo, blockingIo == 1 ? "call" : "calls"));
        card.penalise(selectStar * 3.0, 15,
                selectStar == 0 ? null
                        : "%d unbounded SELECT * %s".formatted(
                                selectStar, selectStar == 1 ? "query" : "queries"));

        if (awaitInLoop == 0 && blockingIo == 0) {
            card.commend("No serialised awaits or blocking IO detected");
        }

        int[] nesting = Statistics.toIntArray(context.structurallyAnalysedFiles(),
                file -> file.metrics().maxNestingDepth());
        double p95Nesting = nesting.length == 0 ? 0 : Statistics.percentile(nesting, 95);
        card.measure(Measurement.of("performance.nesting.p95", p95Nesting));
        card.penalise(ScoreCard.ramp(p95Nesting, 5, 10, 15), 15,
                p95Nesting <= 5 ? null
                        : "Deepest code nests %d levels, often nested iteration"
                                .formatted((int) p95Nesting));

        // --- Concentration of code in one module ---
        if (!context.graph().nodes().isEmpty()) {
            int totalLines = context.graph().nodes().stream()
                    .mapToInt(node -> node.linesOfCode()).sum();
            int largest = context.graph().nodes().stream()
                    .mapToInt(node -> node.linesOfCode()).max().orElse(0);
            double share = totalLines == 0 ? 0 : (largest * 100.0) / totalLines;
            card.measure(Measurement.of("performance.largestModuleShare", share, "%"));
            card.penalise(ScoreCard.ramp(share, 35, 80, 15), 15,
                    share <= 35 ? null
                            : "%d%% of all code sits in a single module".formatted((int) share));
        }

        card.reduceConfidence(100 - CONFIDENCE_CEILING, "static-analysis-only");

        double score = card.score();
        return new DimensionResult(dimension(), score,
                Math.min(card.confidence(), CONFIDENCE_CEILING),
                headline(awaitInLoop, blockingIo, selectStar),
                ("These are static patterns correlated with performance problems, not runtime "
                        + "measurements: the analyser does not execute repository code. It found "
                        + "%d awaits inside loops, %d blocking IO calls and %d unbounded queries.")
                        .formatted(awaitInLoop, blockingIo, selectStar),
                card.strengths(), card.watchItems(), card.measurements(), card.evidence());
    }

    private String headline(long awaitInLoop, long blockingIo, long selectStar) {
        long total = awaitInLoop + blockingIo + selectStar;
        if (total == 0) {
            return "No common performance anti-patterns found in the source.";
        }
        if (awaitInLoop > 0) {
            return "Work that could run concurrently is serialised in %d %s.".formatted(
                    awaitInLoop, awaitInLoop == 1 ? "place" : "places");
        }
        return "A few blocking or unbounded operations are worth reviewing.";
    }
}
