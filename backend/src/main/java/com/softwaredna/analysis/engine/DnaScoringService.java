package com.softwaredna.analysis.engine;

import com.softwaredna.common.domain.DnaDimension;
import com.softwaredna.common.domain.HealthVerdict;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Runs the analysis modules and rolls their results into one profile.
 *
 * <p>The overall score is a weighted mean over the dimensions that produced a
 * score, with the weights of any unavailable dimensions removed and the
 * remainder renormalised. The alternative — treating an unmeasured dimension
 * as zero, or as fifty, or as the average of the others — would invent a
 * number, which is the one thing the engine must never do.
 *
 * <p>A module that throws is recorded as unavailable rather than failing the
 * whole analysis: one broken measurement should not cost the user the other
 * seven.
 */
@Service
public class DnaScoringService {

    private static final Logger log = LoggerFactory.getLogger(DnaScoringService.class);

    private final List<AnalysisModule> modules;

    public DnaScoringService(List<AnalysisModule> modules) {
        this.modules = modules.stream()
                .sorted(Comparator.comparing(module -> module.dimension().ordinal()))
                .toList();
    }

    public DnaProfile score(AnalysisContext context) {
        List<DimensionResult> results = new ArrayList<>(modules.size());
        List<DnaDimension> incomplete = new ArrayList<>();

        for (AnalysisModule module : modules) {
            long startedAt = System.currentTimeMillis();
            DimensionResult result;
            try {
                result = module.analyse(context);
            } catch (RuntimeException e) {
                log.error("Analysis module {} failed", module.dimension(), e);
                result = DimensionResult.unavailable(module.dimension(),
                        "This dimension could not be assessed because its analysis failed.",
                        List.of());
            }
            log.debug("Module {} completed in {} ms (score {})", module.dimension(),
                    System.currentTimeMillis() - startedAt, result.score());

            results.add(result);
            if (!result.isAvailable()) {
                incomplete.add(module.dimension());
            }
        }

        double weightedTotal = 0;
        double weightSum = 0;
        double confidenceTotal = 0;

        for (int i = 0; i < modules.size(); i++) {
            DimensionResult result = results.get(i);
            if (!result.isAvailable()) {
                continue;
            }
            double weight = modules.get(i).weight();
            weightedTotal += result.score() * weight;
            confidenceTotal += result.confidence() * weight;
            weightSum += weight;
        }

        // Renormalisation: the surviving weights are rescaled to sum to one.
        double overall = weightSum == 0 ? 0 : weightedTotal / weightSum;
        double confidence = weightSum == 0 ? 0 : confidenceTotal / weightSum;

        log.info("DNA scored {} ({}), {} of {} dimensions available, confidence {}",
                Math.round(overall), HealthVerdict.forScore(overall),
                results.size() - incomplete.size(), results.size(), Math.round(confidence));

        return new DnaProfile(round(overall), HealthVerdict.forScore(overall),
                List.copyOf(results), List.copyOf(incomplete), round(confidence));
    }

    /** The weight a dimension carries, for display alongside its score. */
    public double weightOf(DnaDimension dimension) {
        return modules.stream()
                .filter(module -> module.dimension() == dimension)
                .findFirst()
                .map(AnalysisModule::weight)
                .orElse(0.0);
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
