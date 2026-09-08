package com.softwaredna.ai;

import com.softwaredna.api.dto.AnalysisDtos;
import com.softwaredna.api.dto.DoctorDtos;

/**
 * Answers a question about one analysis.
 *
 * <p>The abstraction exists so no vendor is wired through the application. A
 * provider receives the <em>structured analysis</em> — scores, measurements,
 * hotspots, the graph — and never the repository's source. That keeps the
 * prompt small enough to be cheap and, more importantly, means a customer's
 * code is never sent to a third party in order to answer a question about it.
 */
public interface AiProvider {

    /** Identifier reported on every answer, so a mock is never mistaken for a model. */
    String name();

    /** False when the provider is configured but missing credentials. */
    boolean isAvailable();

    DoctorDtos.DoctorMessage answer(AnalysisDtos.RepositoryAnalysisDto analysis,
                                    String question);
}
