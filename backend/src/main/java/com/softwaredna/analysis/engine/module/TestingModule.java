package com.softwaredna.analysis.engine.module;

import com.softwaredna.analysis.engine.AnalysisContext;
import com.softwaredna.analysis.engine.AnalysisModule;
import com.softwaredna.analysis.engine.DimensionResult;
import com.softwaredna.analysis.engine.Measurement;
import com.softwaredna.analysis.engine.ScoreCard;
import com.softwaredna.codebase.model.ScannedFile;
import com.softwaredna.common.domain.DnaDimension;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Scores what the repository actually verifies.
 *
 * <p><b>What this cannot measure.</b> Line coverage requires executing the test
 * suite, which this service will never do: running code from an arbitrary
 * repository is exactly the thing the sandbox exists to prevent. Coverage is
 * therefore only reported when the repository commits a report
 * ({@code lcov.info} or a JaCoCo XML), and otherwise the dimension is scored
 * from test <em>presence</em> with confidence reduced by 35 points and the gap
 * stated in the summary.
 *
 * <p><b>Formula.</b> Starting from 100:
 * <ul>
 *   <li><b>Test-to-source file ratio</b> (up to −35). Full marks at 0.5 tests
 *       per source file, full penalty at 0.</li>
 *   <li><b>Module test presence</b> (up to −30). Share of source modules with
 *       no test file anywhere beneath them.</li>
 *   <li><b>Test-to-source line ratio</b> (up to −15). Full marks at 0.4.</li>
 *   <li><b>Measured coverage</b> (up to −40), replacing the ratio terms when a
 *       report is present, because a real number beats a proxy.</li>
 * </ul>
 */
@Component
public class TestingModule implements AnalysisModule {

    @Override
    public DnaDimension dimension() {
        return DnaDimension.TESTING;
    }

    @Override
    public double weight() {
        return 0.12;
    }

    @Override
    public DimensionResult analyse(AnalysisContext context) {
        List<ScannedFile> production = context.productionFiles();
        List<ScannedFile> tests = context.testFiles();
        ScoreCard card = new ScoreCard();

        if (production.isEmpty()) {
            return DimensionResult.unavailable(dimension(),
                    "No production source was found, so testing could not be assessed.",
                    List.of());
        }

        double fileRatio = tests.size() / (double) production.size();
        int testLines = tests.stream().mapToInt(file -> file.metrics().linesOfCode()).sum();
        int productionLines = production.stream()
                .mapToInt(file -> file.metrics().linesOfCode()).sum();
        double lineRatio = productionLines == 0 ? 0 : testLines / (double) productionLines;

        card.measure(Measurement.of("testing.testFiles", tests.size()))
                .measure(Measurement.of("testing.sourceFiles", production.size()))
                .measure(Measurement.of("testing.fileRatio", fileRatio))
                .measure(Measurement.of("testing.lineRatio", lineRatio));

        if (context.hasCoverage()) {
            double coverage = context.coverageByPath().values().stream()
                    .mapToDouble(Double::doubleValue).average().orElse(0);
            card.measure(Measurement.of("testing.coverage", coverage, "%"));
            card.penalise(ScoreCard.inverseRamp(coverage, 80, 20, 40), 40,
                    coverage >= 80 ? null
                            : "Line coverage is %d%%".formatted((int) coverage));
            if (coverage >= 80) {
                card.commend("Line coverage is %d%%".formatted((int) coverage));
            }
        } else {
            card.measure(Measurement.unavailable("testing.coverage",
                    "no coverage report is committed to the repository, and the analyser "
                            + "does not execute the test suite"));
            card.reduceConfidence(35, "no-coverage-report");
        }

        card.penalise(ScoreCard.inverseRamp(fileRatio, 0.5, 0, 35), 35,
                tests.isEmpty()
                        ? "No test files were found"
                        : fileRatio >= 0.5 ? null
                                : "There is roughly one test file for every %d source files"
                                        .formatted(Math.max(1, (int) Math.round(1 / Math.max(fileRatio, 0.01)))));
        if (fileRatio >= 0.5) {
            card.commend("%d test files against %d source files"
                    .formatted(tests.size(), production.size()));
        }

        // --- Which modules have no tests at all ---
        Set<String> testedModules = new HashSet<>();
        for (ScannedFile test : tests) {
            testedModules.add(topLevelModule(test.path()));
        }
        Set<String> sourceModules = new HashSet<>();
        for (ScannedFile file : production) {
            sourceModules.add(topLevelModule(file.path()));
        }
        long untested = sourceModules.stream()
                .filter(module -> !testedModules.contains(module))
                .count();
        double untestedShare = sourceModules.isEmpty() ? 0
                : (untested * 100.0) / sourceModules.size();

        card.measure(Measurement.of("testing.untestedModuleShare", untestedShare, "%"));
        card.penalise(ScoreCard.ramp(untestedShare, 10, 80, 30), 30,
                untested == 0 ? null
                        : "%d of %d source areas have no tests".formatted(
                                untested, sourceModules.size()));

        card.penalise(ScoreCard.inverseRamp(lineRatio, 0.4, 0, 15), 15, null);

        double score = card.score();
        return new DimensionResult(dimension(), score, card.confidence(),
                headline(tests.size(), fileRatio, context.hasCoverage()),
                summary(tests.size(), production.size(), fileRatio, context),
                card.strengths(), card.watchItems(), card.measurements(), card.evidence());
    }

    private String topLevelModule(String path) {
        int slash = path.indexOf('/');
        return slash < 0 ? "(root)" : path.substring(0, slash);
    }

    private String headline(int tests, double ratio, boolean hasCoverage) {
        if (tests == 0) {
            return "No test files were found in the repository.";
        }
        if (!hasCoverage) {
            return "Tests are present; coverage could not be measured without executing them.";
        }
        return ratio >= 0.5
                ? "Well covered, with tests distributed across the codebase."
                : "Tests exist but do not reach much of the code.";
    }

    private String summary(int tests, int production, double ratio, AnalysisContext context) {
        String coverageNote = context.hasCoverage()
                ? "Coverage was read from a %s report committed to the repository."
                        .formatted(context.coverageSource())
                : "Line coverage is not reported: no coverage report is committed, and the "
                        + "analyser does not execute repository code. This dimension is scored "
                        + "from test presence alone and its confidence is reduced accordingly.";

        return "The repository contains %d test %s against %d source files, a ratio of %.2f. %s"
                .formatted(tests, tests == 1 ? "file" : "files", production, ratio, coverageNote);
    }
}
