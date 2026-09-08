package com.softwaredna.api;

import com.softwaredna.api.dto.AnalysisDtos;
import com.softwaredna.api.dto.ComparisonDtos;
import com.softwaredna.common.domain.DnaDimension;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Compares two completed analyses.
 *
 * <p>A dimension that is unavailable on either side produces no winner and no
 * delta. Treating an unmeasured dimension as a loss would make a repository
 * that simply did not commit a coverage report look worse than one that did,
 * which is a statement about reporting rather than about the code.
 */
@Service
public class ComparisonService {

    private final AnalysisReadService reads;

    public ComparisonService(AnalysisReadService reads) {
        this.reads = reads;
    }

    @Transactional(readOnly = true)
    public ComparisonDtos.ComparisonResult compare(UUID leftId, UUID rightId) {
        AnalysisDtos.RepositoryAnalysisDto left = reads.getAnalysis(leftId);
        AnalysisDtos.RepositoryAnalysisDto right = reads.getAnalysis(rightId);

        Map<DnaDimension, AnalysisDtos.DnaDimensionDto> rightByKey =
                right.dna().dimensions().stream()
                        .collect(Collectors.toMap(AnalysisDtos.DnaDimensionDto::key,
                                Function.identity()));

        List<ComparisonDtos.ComparisonRow> rows = new ArrayList<>();
        for (AnalysisDtos.DnaDimensionDto leftDimension : left.dna().dimensions()) {
            AnalysisDtos.DnaDimensionDto rightDimension = rightByKey.get(leftDimension.key());

            Double leftScore = leftDimension.score();
            Double rightScore = rightDimension == null ? null : rightDimension.score();

            Double delta = leftScore == null || rightScore == null
                    ? null : round(leftScore - rightScore);
            String leader = delta == null ? "tie"
                    : delta > 0 ? "left" : delta < 0 ? "right" : "tie";

            rows.add(new ComparisonDtos.ComparisonRow(
                    leftDimension.key(), leftDimension.label(),
                    leftScore, rightScore, delta, leader));
        }

        return new ComparisonDtos.ComparisonResult(
                toSummary(left), toSummary(right), rows, verdict(left, right, rows));
    }

    private ComparisonDtos.ComparisonVerdict verdict(
            AnalysisDtos.RepositoryAnalysisDto left,
            AnalysisDtos.RepositoryAnalysisDto right,
            List<ComparisonDtos.ComparisonRow> rows) {

        long leftWins = rows.stream().filter(row -> "left".equals(row.leader())).count();
        long rightWins = rows.stream().filter(row -> "right".equals(row.leader())).count();
        long comparable = rows.stream().filter(row -> row.delta() != null).count();

        String leftName = left.repository().displayName();
        String rightName = right.repository().displayName();
        double gap = left.dna().overall() - right.dna().overall();

        String headline = Math.abs(gap) < 1
                ? "Evenly matched overall, but strong in different places."
                : "%s leads overall by %.0f points.".formatted(
                        gap > 0 ? leftName : rightName, Math.abs(gap));

        String uncomparable = comparable < rows.size()
                ? " %d of %d dimensions could not be compared because one side did not "
                        .formatted(rows.size() - comparable, rows.size())
                        + "measure them."
                : "";

        ComparisonDtos.ComparisonRow biggestLeft = rows.stream()
                .filter(row -> "left".equals(row.leader()))
                .max(Comparator.comparingDouble(row -> row.delta())).orElse(null);
        ComparisonDtos.ComparisonRow biggestRight = rows.stream()
                .filter(row -> "right".equals(row.leader()))
                .min(Comparator.comparingDouble(row -> row.delta())).orElse(null);

        return new ComparisonDtos.ComparisonVerdict(
                headline,
                ("%s wins %d of %d comparable dimensions, %s wins %d.%s")
                        .formatted(leftName, leftWins, comparable, rightName, rightWins,
                                uncomparable),
                takeaway(leftName, rightName, biggestLeft, biggestRight, true),
                takeaway(rightName, leftName, biggestRight, biggestLeft, false));
    }

    private String takeaway(String subject, String other,
                            ComparisonDtos.ComparisonRow strongest,
                            ComparisonDtos.ComparisonRow weakest,
                            boolean isLeft) {
        if (strongest == null) {
            return "%s does not lead %s on any measured dimension.".formatted(subject, other);
        }
        String strongText = "Strongest advantage: %s, ahead by %.0f points."
                .formatted(strongest.label(), Math.abs(strongest.delta()));
        if (weakest == null) {
            return strongText;
        }
        return strongText + " Weakest relative to %s: %s.".formatted(other, weakest.label());
    }

    private AnalysisDtos.AnalysisSummary toSummary(AnalysisDtos.RepositoryAnalysisDto analysis) {
        return new AnalysisDtos.AnalysisSummary(
                analysis.id(),
                analysis.repository().displayName(),
                analysis.repository().owner(),
                analysis.repository().name(),
                analysis.repository().url(),
                analysis.dna().overall(),
                analysis.repository().primaryLanguage(),
                analysis.generatedAt());
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
