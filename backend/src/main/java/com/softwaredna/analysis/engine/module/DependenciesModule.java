package com.softwaredna.analysis.engine.module;

import com.softwaredna.analysis.engine.AnalysisContext;
import com.softwaredna.analysis.engine.AnalysisModule;
import com.softwaredna.analysis.engine.DimensionResult;
import com.softwaredna.analysis.engine.Measurement;
import com.softwaredna.analysis.engine.ScoreCard;
import com.softwaredna.common.domain.DependencyStatus;
import com.softwaredna.common.domain.DnaDimension;
import com.softwaredna.dependency.model.ResolvedDependency;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Scores the dependency surface.
 *
 * <p><b>What is and is not claimed.</b> Only direct dependencies are counted,
 * because only direct dependencies are declared; a transitive total would
 * require resolving the full graph against a registry, which is not done here.
 * Likewise, "outdated" is only reported when a registry lookup actually
 * happened. Without one, freshness is unknown rather than good.
 *
 * <p><b>Formula.</b> Starting from 100:
 * <ul>
 *   <li><b>Surface size</b> (up to −20). Direct dependencies relative to the
 *       size of the codebase: no penalty below one dependency per 400 lines.</li>
 *   <li><b>Version pinning</b> (up to −20). Share of declarations with no
 *       resolvable version, which makes builds irreproducible.</li>
 *   <li><b>Duplication</b> (up to −15). The same package declared at
 *       conflicting versions across manifests.</li>
 *   <li><b>Staleness</b> (up to −30), applied only when versions were checked.</li>
 * </ul>
 */
@Component
public class DependenciesModule implements AnalysisModule {

    @Override
    public DnaDimension dimension() {
        return DnaDimension.DEPENDENCIES;
    }

    @Override
    public double weight() {
        return 0.11;
    }

    @Override
    public DimensionResult analyse(AnalysisContext context) {
        List<ResolvedDependency> dependencies = context.manifests().dependencies();
        ScoreCard card = new ScoreCard();

        if (context.manifests().manifestsFound().isEmpty()) {
            return DimensionResult.unavailable(dimension(),
                    "No dependency manifest was found, so the dependency surface could not "
                            + "be assessed.",
                    List.of(Measurement.unavailable("dependencies.total",
                            "no package.json, pom.xml or build.gradle was found")));
        }

        int direct = dependencies.size();
        int linesOfCode = context.scan().totalLinesOfCode();

        card.measure(Measurement.of("dependencies.direct", direct))
                .measure(Measurement.of("dependencies.manifests",
                        context.manifests().manifestsFound().size()))
                .measure(Measurement.unavailable("dependencies.transitive",
                        "transitive resolution requires a registry lookup, which was not "
                                + "performed"));

        double perThousandLines = linesOfCode == 0 ? 0 : (direct * 1000.0) / linesOfCode;
        card.measure(Measurement.of("dependencies.perThousandLines", perThousandLines));
        card.penalise(ScoreCard.ramp(perThousandLines, 2.5, 12, 20), 20,
                perThousandLines <= 2.5 ? null
                        : "%d direct dependencies for %,d lines of code"
                                .formatted(direct, linesOfCode));
        if (perThousandLines <= 2.5 && direct > 0) {
            card.commend("%d direct dependencies across %d %s".formatted(direct,
                    context.manifests().manifestsFound().size(),
                    context.manifests().manifestsFound().size() == 1 ? "manifest" : "manifests"));
        }

        long unpinned = dependencies.stream()
                .filter(dependency -> isUnpinned(dependency.version()))
                .count();
        double unpinnedShare = direct == 0 ? 0 : (unpinned * 100.0) / direct;
        card.measure(Measurement.of("dependencies.unpinnedShare", unpinnedShare, "%"));
        card.penalise(ScoreCard.ramp(unpinnedShare, 5, 50, 20), 20,
                unpinned == 0 ? null
                        : "%d %s declared without a resolvable version".formatted(
                                unpinned, unpinned == 1 ? "dependency is" : "dependencies are"));
        if (unpinned == 0) {
            card.commend("Every dependency declares a resolvable version");
        }

        Map<String, Long> distinctVersions = dependencies.stream()
                .collect(Collectors.groupingBy(ResolvedDependency::name,
                        Collectors.mapping(ResolvedDependency::version,
                                Collectors.collectingAndThen(Collectors.toSet(),
                                        set -> (long) set.size()))));
        long conflicting = distinctVersions.values().stream().filter(count -> count > 1).count();
        card.measure(Measurement.of("dependencies.conflictingVersions", conflicting));
        card.penalise(conflicting * 5.0, 15,
                conflicting == 0 ? null
                        : "%d %s declared at more than one version".formatted(
                                conflicting, conflicting == 1 ? "package is" : "packages are"));

        boolean versionsChecked = dependencies.stream()
                .anyMatch(dependency -> dependency.latestVersion() != null);
        if (versionsChecked) {
            long behind = dependencies.stream()
                    .filter(dependency -> dependency.status() == DependencyStatus.MAJOR_BEHIND
                            || dependency.status() == DependencyStatus.MINOR_BEHIND)
                    .count();
            double behindShare = direct == 0 ? 0 : (behind * 100.0) / direct;
            card.measure(Measurement.of("dependencies.behindShare", behindShare, "%"));
            card.penalise(ScoreCard.ramp(behindShare, 10, 60, 30), 30,
                    behind == 0 ? null
                            : "%d dependencies have a newer release available".formatted(behind));
        } else {
            card.measure(Measurement.unavailable("dependencies.outdated",
                    "no package registry is configured, so current versions were not checked"));
            card.reduceConfidence(30, "no-registry-lookup");
        }

        if (!context.manifests().manifestsUnreadable().isEmpty()) {
            card.reduceConfidence(15, "unreadable-manifests");
            card.penalise(0, 0, null);
        }

        double score = card.score();
        return new DimensionResult(dimension(), score, card.confidence(),
                headline(direct, unpinned, versionsChecked),
                summary(direct, context, versionsChecked),
                card.strengths(), card.watchItems(), card.measurements(), card.evidence());
    }

    private boolean isUnpinned(String version) {
        return version == null || version.isBlank()
                || version.equals("unspecified") || version.equals("managed")
                || version.equals("*") || version.equalsIgnoreCase("latest");
    }

    private String headline(int direct, long unpinned, boolean versionsChecked) {
        if (unpinned > 0) {
            return "%d %s declared without a pinned version.".formatted(
                    unpinned, unpinned == 1 ? "dependency is" : "dependencies are");
        }
        return versionsChecked
                ? "A lean, current dependency surface."
                : "A lean, well-pinned surface; freshness was not checked.";
    }

    private String summary(int direct, AnalysisContext context, boolean versionsChecked) {
        String manifests = String.join(", ", context.manifests().manifestsFound());
        String base = "%d direct dependencies declared across %s.".formatted(direct, manifests);
        return versionsChecked ? base
                : base + " Transitive dependencies and available updates were not resolved: "
                        + "both require a package registry lookup, which is not configured.";
    }
}
