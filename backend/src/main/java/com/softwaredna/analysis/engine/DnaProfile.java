package com.softwaredna.analysis.engine;

import com.softwaredna.common.domain.DnaDimension;
import com.softwaredna.common.domain.HealthVerdict;

import java.util.List;

/**
 * The assembled Software DNA.
 *
 * <p>{@code incompleteDimensions} names every dimension that could not be
 * assessed. The overall score is computed only from dimensions that were,
 * with their weights renormalised, so a missing dimension neither drags the
 * total down nor is silently treated as perfect.
 */
public record DnaProfile(
        double overall,
        HealthVerdict verdict,
        List<DimensionResult> dimensions,
        List<DnaDimension> incompleteDimensions,
        /** Weighted mean of the confidence of the dimensions that were scored. */
        double confidence
) {
}
