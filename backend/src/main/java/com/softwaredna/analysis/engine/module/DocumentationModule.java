package com.softwaredna.analysis.engine.module;

import com.softwaredna.analysis.engine.AnalysisContext;
import com.softwaredna.analysis.engine.AnalysisModule;
import com.softwaredna.analysis.engine.DimensionResult;
import com.softwaredna.analysis.engine.Measurement;
import com.softwaredna.analysis.engine.ScoreCard;
import com.softwaredna.codebase.model.Language;
import com.softwaredna.codebase.model.ScannedFile;
import com.softwaredna.codebase.scan.SignalDetector;
import com.softwaredna.common.domain.DnaDimension;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Scores what the repository writes down.
 *
 * <p>This is the dimension static analysis measures best: documentation either
 * exists in the tree or it does not.
 *
 * <p><b>Formula.</b> Starting from 100:
 * <ul>
 *   <li><b>README</b> (up to −25). Absent costs the full amount; a stub under
 *       40 lines costs 12, since a placeholder is barely better than nothing.</li>
 *   <li><b>Decision records</b> (−15). No {@code docs/adr} or equivalent. The
 *       reasoning behind a structure is the part that is hardest to recover
 *       later.</li>
 *   <li><b>Extended documentation</b> (−10). No {@code docs/} directory.</li>
 *   <li><b>API description</b> (−10). No OpenAPI or GraphQL schema, applied
 *       only when the repository looks like it exposes an API.</li>
 *   <li><b>Doc comment coverage</b> (up to −25). Share of source files
 *       carrying at least one doc comment: full marks at 40%.</li>
 *   <li><b>Contribution guidance</b> (−5). No CONTRIBUTING file.</li>
 * </ul>
 */
@Component
public class DocumentationModule implements AnalysisModule {

    @Override
    public DnaDimension dimension() {
        return DnaDimension.DOCUMENTATION;
    }

    @Override
    public double weight() {
        return 0.08;
    }

    @Override
    public DimensionResult analyse(AnalysisContext context) {
        List<ScannedFile> allFiles = context.scan().files();
        List<ScannedFile> production = context.productionFiles();
        ScoreCard card = new ScoreCard();

        ScannedFile readme = allFiles.stream()
                .filter(file -> file.path().toLowerCase(Locale.ROOT).matches("readme(\\.[a-z]+)?"))
                .findFirst()
                .orElse(null);

        int readmeLines = readme == null ? 0 : readme.metrics().linesTotal();
        card.measure(Measurement.of("documentation.readmeLines", readmeLines));

        if (readme == null) {
            card.penalise(25, 25, "The repository has no README");
        } else if (readmeLines < 40) {
            card.penalise(12, 12, "The README is only %d lines".formatted(readmeLines));
        } else {
            card.commend("README covers %d lines".formatted(readmeLines));
        }

        boolean hasAdr = allFiles.stream().anyMatch(file -> {
            String path = file.path().toLowerCase(Locale.ROOT);
            return path.contains("/adr/") || path.contains("architecture-decision")
                    || path.contains("decisions/") || path.matches(".*docs?/adr.*");
        });
        card.measure(Measurement.of("documentation.adr", hasAdr ? 1 : 0));
        if (hasAdr) {
            card.commend("Architecture decision records are kept in the repository");
        } else {
            card.penalise(15, 15, "No architecture decision records were found");
        }

        boolean hasDocsDirectory = allFiles.stream()
                .anyMatch(file -> file.path().toLowerCase(Locale.ROOT).startsWith("docs/")
                        || file.path().toLowerCase(Locale.ROOT).startsWith("doc/"));
        card.measure(Measurement.of("documentation.docsDirectory", hasDocsDirectory ? 1 : 0));
        if (hasDocsDirectory) {
            card.commend("Extended documentation lives alongside the code");
        } else {
            card.penalise(10, 10, "No documentation directory was found");
        }

        boolean looksLikeApi = context.graph().nodes().stream()
                .anyMatch(node -> node.kind() == com.softwaredna.common.domain
                        .ArchitectureNodeKind.API);
        boolean hasApiSpec = allFiles.stream().anyMatch(file -> {
            String path = file.path().toLowerCase(Locale.ROOT);
            return path.contains("openapi") || path.contains("swagger")
                    || path.endsWith(".graphql") || path.endsWith("api-docs.json");
        });
        if (looksLikeApi) {
            card.measure(Measurement.of("documentation.apiSpec", hasApiSpec ? 1 : 0));
            if (hasApiSpec) {
                card.commend("The API surface is described by a machine-readable spec");
            } else {
                card.penalise(10, 10,
                        "The repository exposes an API but ships no API description");
            }
        }

        long documented = production.stream()
                .filter(file -> file.signal(SignalDetector.DOC_COMMENTS) > 0)
                .count();
        long documentable = production.stream()
                .filter(file -> file.language() == Language.JAVA
                        || file.language() == Language.TYPESCRIPT
                        || file.language() == Language.JAVASCRIPT
                        || file.language() == Language.KOTLIN)
                .count();
        double docCommentShare = documentable == 0 ? Double.NaN
                : (documented * 100.0) / documentable;

        if (Double.isNaN(docCommentShare)) {
            card.measure(Measurement.unavailable("documentation.docCommentShare",
                    "no files in a language whose doc-comment convention is recognised"));
            card.reduceConfidence(15, "no-documentable-files");
        } else {
            card.measure(Measurement.of("documentation.docCommentShare",
                    docCommentShare, "%"));
            card.penalise(ScoreCard.inverseRamp(docCommentShare, 40, 0, 25), 25,
                    docCommentShare >= 40 ? null
                            : "%d%% of source files carry a doc comment"
                                    .formatted((int) docCommentShare));
            if (docCommentShare >= 40) {
                card.commend("%d%% of source files carry doc comments"
                        .formatted((int) docCommentShare));
            }
        }

        boolean hasContributing = allFiles.stream().anyMatch(file ->
                file.path().toLowerCase(Locale.ROOT).startsWith("contributing"));
        if (!hasContributing) {
            card.penalise(5, 5, "No contribution guide was found");
        }

        double score = card.score();
        return new DimensionResult(dimension(), score, card.confidence(),
                headline(readme != null, hasAdr, docCommentShare),
                ("The repository %s a README, %s decision records and %s a documentation "
                        + "directory. %s")
                        .formatted(readme == null ? "has no" : "has",
                                hasAdr ? "keeps" : "keeps no",
                                hasDocsDirectory ? "has" : "has no",
                                Double.isNaN(docCommentShare)
                                        ? "Doc-comment density was not applicable."
                                        : "%d%% of source files carry a doc comment."
                                                .formatted((int) docCommentShare)),
                card.strengths(), card.watchItems(), card.measurements(), card.evidence());
    }

    private String headline(boolean hasReadme, boolean hasAdr, double docShare) {
        if (!hasReadme) {
            return "The repository ships without a README.";
        }
        if (hasAdr && docShare >= 40) {
            return "Well documented, including the reasoning behind the structure.";
        }
        if (!hasAdr) {
            return "Good onboarding material; the reasoning behind decisions is not recorded.";
        }
        return "Documentation exists but does not reach much of the source.";
    }
}
