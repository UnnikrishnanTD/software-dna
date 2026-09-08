package com.softwaredna.analysis.engine;

import com.softwaredna.common.domain.DnaDimension;

import java.util.List;
import java.util.Map;

/**
 * One dimension's verdict, plus the evidence behind it.
 *
 * <p>A null {@code score} means the dimension could not be assessed at all;
 * the caller lists it as incomplete rather than substituting a number.
 * {@code confidence} carries the softer case: the dimension was assessed, but
 * some inputs were missing, and the reader should weigh it accordingly.
 */
public record DimensionResult(
        DnaDimension dimension,
        Double score,
        double confidence,
        String headline,
        String summary,
        List<String> strengths,
        List<String> watchItems,
        List<Measurement> measurements,
        Map<String, String> evidence
) {

    public boolean isAvailable() {
        return score != null;
    }

    public static DimensionResult unavailable(DnaDimension dimension, String reason,
                                              List<Measurement> measurements) {
        return new DimensionResult(dimension, null, 0, reason,
                reason, List.of(), List.of(), measurements, Map.of());
    }
}
