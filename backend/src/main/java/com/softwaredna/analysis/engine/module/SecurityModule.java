package com.softwaredna.analysis.engine.module;

import com.softwaredna.analysis.engine.AnalysisContext;
import com.softwaredna.analysis.engine.AnalysisModule;
import com.softwaredna.analysis.engine.DimensionResult;
import com.softwaredna.analysis.engine.Measurement;
import com.softwaredna.analysis.engine.ScoreCard;
import com.softwaredna.codebase.model.ScannedFile;
import com.softwaredna.codebase.scan.SignalDetector;
import com.softwaredna.common.domain.DnaDimension;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Scores security posture from what static analysis can actually establish.
 *
 * <p><b>What this deliberately does not do.</b> It does not claim a dependency
 * is vulnerable. Doing so requires a real advisory database, and none is
 * consulted unless one is configured; asserting "no known vulnerabilities"
 * from an unconsulted database would be the most dangerous possible false
 * reassurance. The dimension records that gap and lowers its confidence by 25
 * points rather than quietly scoring as though the check had passed.
 *
 * <p><b>Formula.</b> Starting from 100:
 * <ul>
 *   <li><b>Committed credentials</b> (up to −45). Each file matching a
 *       high-specificity credential shape costs 15. These patterns match key
 *       formats issued by known providers, not generic {@code password =}
 *       assignments, so a match is worth acting on.</li>
 *   <li><b>Injection-prone SQL</b> (up to −20). String-concatenated queries.</li>
 *   <li><b>Dangerous execution and deserialisation APIs</b> (up to −15),
 *       measured as a share of files rather than an absolute count.</li>
 *   <li><b>Missing supply-chain tooling</b> (up to −10). No dependency update
 *       automation and no security workflow in CI.</li>
 *   <li><b>Confirmed advisories</b> (up to −40), applied only when a
 *       vulnerability source was actually consulted.</li>
 * </ul>
 */
@Component
public class SecurityModule implements AnalysisModule {

    @Override
    public DnaDimension dimension() {
        return DnaDimension.SECURITY;
    }

    @Override
    public double weight() {
        return 0.14;
    }

    @Override
    public DimensionResult analyse(AnalysisContext context) {
        List<ScannedFile> files = context.scan().files();
        ScoreCard card = new ScoreCard();

        long secretFiles = files.stream()
                .filter(file -> file.signal(SignalDetector.SECRET) > 0)
                .count();
        long sqlConcatFiles = files.stream()
                .filter(file -> file.signal(SignalDetector.SQL_CONCAT) > 0)
                .count();
        long dangerousApiFiles = files.stream()
                .filter(file -> file.signal(SignalDetector.DANGEROUS_API_KEY) > 0)
                .count();

        double dangerousShare = files.isEmpty() ? 0 : (dangerousApiFiles * 100.0) / files.size();

        card.measure(Measurement.of("security.filesWithCredentialPattern", secretFiles))
                .measure(Measurement.of("security.filesWithConcatenatedSql", sqlConcatFiles))
                .measure(Measurement.of("security.filesWithDangerousApi", dangerousApiFiles))
                .measure(Measurement.of("security.dangerousApiShare", dangerousShare, "%"));

        card.penalise(secretFiles * 15.0, 45,
                secretFiles == 0 ? null
                        : "%d %s contain a pattern matching a provider credential format"
                                .formatted(secretFiles, secretFiles == 1 ? "file" : "files"));
        if (secretFiles == 0) {
            card.commend("No committed credentials matching known provider formats");
        }

        card.penalise(sqlConcatFiles * 7.0, 20,
                sqlConcatFiles == 0 ? null
                        : "%d %s build SQL by string concatenation".formatted(
                                sqlConcatFiles, sqlConcatFiles == 1 ? "file" : "files"));

        card.penalise(ScoreCard.ramp(dangerousShare, 2, 20, 15), 15,
                dangerousApiFiles == 0 ? null
                        : "%d %s use execution or deserialisation APIs that warrant review"
                                .formatted(dangerousApiFiles,
                                        dangerousApiFiles == 1 ? "file" : "files"));

        // --- Supply-chain tooling, detected from committed configuration ---
        boolean hasDependencyAutomation = hasAnyPath(files,
                ".github/dependabot.yml", ".github/dependabot.yaml", "renovate.json",
                ".renovaterc", ".renovaterc.json");
        boolean hasSecurityWorkflow = files.stream()
                .filter(file -> file.path().startsWith(".github/workflows/"))
                .anyMatch(file -> {
                    String name = file.path().toLowerCase(Locale.ROOT);
                    return name.contains("security") || name.contains("codeql")
                            || name.contains("scan") || name.contains("audit");
                });

        card.measure(Measurement.of("security.dependencyAutomation",
                hasDependencyAutomation ? 1 : 0))
                .measure(Measurement.of("security.securityWorkflow",
                        hasSecurityWorkflow ? 1 : 0));

        if (hasDependencyAutomation) {
            card.commend("Automated dependency updates are configured");
        } else {
            card.penalise(5, 5, "No automated dependency update configuration was found");
        }
        if (hasSecurityWorkflow) {
            card.commend("A security scanning workflow runs in CI");
        } else {
            card.penalise(5, 5, "No security scanning workflow was found in CI");
        }

        // --- Advisories, only if something actually checked ---
        boolean advisoriesChecked = context.manifests().dependencies().stream()
                .anyMatch(dependency -> dependency.advisoryCount() != null);
        if (advisoriesChecked) {
            long advisories = context.manifests().dependencies().stream()
                    .filter(dependency -> dependency.advisoryCount() != null)
                    .mapToLong(dependency -> dependency.advisoryCount())
                    .sum();
            card.measure(Measurement.of("security.advisories", advisories));
            card.penalise(advisories * 10.0, 40,
                    advisories == 0 ? null
                            : "%d dependency %s reported by the configured advisory source"
                                    .formatted(advisories,
                                            advisories == 1 ? "advisory" : "advisories"));
        } else {
            card.measure(Measurement.unavailable("security.advisories",
                    "no vulnerability database is configured, so dependency advisories were "
                            + "not checked"));
            card.reduceConfidence(25, "no-advisory-source");
        }

        double score = card.score();
        return new DimensionResult(dimension(), score, card.confidence(),
                headline(secretFiles, sqlConcatFiles, advisoriesChecked),
                summary(secretFiles, sqlConcatFiles, dangerousApiFiles, advisoriesChecked),
                card.strengths(), card.watchItems(), card.measurements(), card.evidence());
    }

    private boolean hasAnyPath(List<ScannedFile> files, String... paths) {
        for (String path : paths) {
            for (ScannedFile file : files) {
                if (file.path().equalsIgnoreCase(path)) {
                    return true;
                }
            }
        }
        return false;
    }

    private String headline(long secrets, long sqlConcat, boolean advisoriesChecked) {
        if (secrets > 0) {
            return "Credential-shaped strings are committed to the repository.";
        }
        if (sqlConcat > 0) {
            return "No committed credentials, but SQL is built by concatenation in places.";
        }
        return advisoriesChecked
                ? "No committed credentials and no outstanding advisories."
                : "Clean on the checks performed; dependency advisories were not consulted.";
    }

    private String summary(long secrets, long sqlConcat, long dangerous,
                           boolean advisoriesChecked) {
        String base = ("Static checks found %d %s with credential-shaped content, %d building "
                + "SQL by concatenation, and %d using execution or deserialisation APIs.")
                .formatted(secrets, secrets == 1 ? "file" : "files", sqlConcat, dangerous);

        return advisoriesChecked ? base
                : base + " Dependency vulnerabilities were not assessed: no advisory database "
                        + "is configured, and this dimension does not assume the absence of "
                        + "findings it did not look for.";
    }
}
