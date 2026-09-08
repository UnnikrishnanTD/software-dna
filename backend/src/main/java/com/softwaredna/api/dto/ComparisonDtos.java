package com.softwaredna.api.dto;

import com.softwaredna.common.domain.DnaDimension;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Side-by-side comparison of two completed analyses. */
public final class ComparisonDtos {

    private ComparisonDtos() {
    }

    public record ComparisonRow(
            DnaDimension key,
            String label,
            @Schema(description = "Null when the dimension was unavailable on that side")
            Double left,
            Double right,
            @Schema(description = "left minus right; null when either side is unavailable")
            Double delta,
            @Schema(allowableValues = {"left", "right", "tie"}) String leader
    ) {
    }

    public record ComparisonVerdict(
            String headline,
            String body,
            String leftTakeaway,
            String rightTakeaway
    ) {
    }

    public record ComparisonResult(
            AnalysisDtos.AnalysisSummary left,
            AnalysisDtos.AnalysisSummary right,
            List<ComparisonRow> rows,
            ComparisonVerdict verdict
    ) {
    }
}
