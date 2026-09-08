package com.softwaredna.analysis.engine;

import com.softwaredna.common.domain.DnaDimension;

/**
 * Scores one dimension of the Software DNA.
 *
 * <p>Modules are independent and registered as beans, so a new dimension or a
 * replacement scoring approach is an added class rather than an edit to a
 * central service. Each must:
 *
 * <ul>
 *   <li>derive its score only from measurements it records, so the number can
 *       always be explained;</li>
 *   <li>return an unavailable result rather than a default when its inputs are
 *       missing;</li>
 *   <li>lower its confidence when it is working from partial evidence.</li>
 * </ul>
 */
public interface AnalysisModule {

    DnaDimension dimension();

    /** Relative contribution to the overall score. Weights are normalised. */
    double weight();

    DimensionResult analyse(AnalysisContext context);
}
