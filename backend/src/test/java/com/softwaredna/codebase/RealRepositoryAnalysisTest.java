package com.softwaredna.codebase;

import com.softwaredna.codebase.analyzer.JavaLanguageAnalyzer;
import com.softwaredna.codebase.analyzer.LanguageAnalyzer;
import com.softwaredna.codebase.analyzer.LineCountingAnalyzer;
import com.softwaredna.codebase.analyzer.TypeScriptLanguageAnalyzer;
import com.softwaredna.codebase.model.Language;
import com.softwaredna.codebase.model.ScannedFile;
import com.softwaredna.codebase.scan.RepositoryScanner;
import com.softwaredna.architecture.ArchitectureGraph;
import com.softwaredna.architecture.ArchitectureGraphBuilder;
import com.softwaredna.architecture.ImportResolver;
import com.softwaredna.config.AnalysisLimits;
import com.softwaredna.evolution.git.GitHistory;
import com.softwaredna.evolution.git.GitHistoryAnalyzer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Scans and reads the history of a genuine repository: this project's own
 * Angular frontend, which sits alongside the backend in the same working tree.
 *
 * <p>Using a repository whose contents are known makes the assertions
 * meaningful — the frontend really is TypeScript, really does contain more
 * source than test files, and really does import Angular.
 */
class RealRepositoryAnalysisTest {

    private static final AnalysisLimits LIMITS = new AnalysisLimits(
            500L * 1024 * 1024, 2L * 1024 * 1024, 25_000, 20_000,
            Duration.ofMinutes(5), 300);

    private RepositoryScanner scanner() {
        List<LanguageAnalyzer> analyzers = List.of(
                new JavaLanguageAnalyzer(), new TypeScriptLanguageAnalyzer());
        return new RepositoryScanner(analyzers, new LineCountingAnalyzer(), LIMITS);
    }

    /** The repository root, which contains both `src/` (Angular) and `backend/`. */
    private Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath();
        // Tests run from backend/, so the working tree root is its parent.
        return current.getFileName().toString().equals("backend")
                ? current.getParent()
                : current;
    }

    @Test
    void scansThisProjectsOwnAngularSource() {
        Path root = repositoryRoot();
        assertThat(Files.isDirectory(root.resolve("src/app")))
                .as("expected the Angular frontend to be present")
                .isTrue();

        RepositoryScanner.ScanResult result = scanner().scan(root.resolve("src"));

        assertThat(result.files()).isNotEmpty();
        assertThat(result.truncated()).isFalse();

        List<ScannedFile> typescript = result.files().stream()
                .filter(file -> file.language() == Language.TYPESCRIPT)
                .toList();

        assertThat(typescript)
                .as("the frontend is written in TypeScript")
                .hasSizeGreaterThan(30);

        // Every TypeScript file got real structural analysis, not a line count.
        assertThat(typescript).allSatisfy(file ->
                assertThat(file.metrics().structuralAnalysis()).isTrue());

        assertThat(result.totalLinesOfCode())
                .as("a real codebase of this size has thousands of lines")
                .isGreaterThan(3_000);
    }

    @Test
    void separatesTestFilesFromSource() {
        RepositoryScanner.ScanResult result = scanner().scan(repositoryRoot().resolve("src"));

        List<ScannedFile> tests = result.files().stream()
                .filter(ScannedFile::test)
                .toList();

        assertThat(tests)
                .as("the frontend has spec files")
                .isNotEmpty();
        assertThat(tests).allSatisfy(file ->
                assertThat(file.path()).contains(".spec."));

        long sourceCount = result.files().stream()
                .filter(file -> file.language() == Language.TYPESCRIPT && !file.test())
                .count();
        assertThat(sourceCount).isGreaterThan(tests.size());
    }

    @Test
    void extractsRealImportsFromRealFiles() {
        RepositoryScanner.ScanResult result = scanner().scan(repositoryRoot().resolve("src"));

        List<String> allImports = result.files().stream()
                .flatMap(file -> file.imports().stream())
                .toList();

        assertThat(allImports).contains("@angular/core");
        assertThat(allImports).anySatisfy(specifier ->
                assertThat(specifier).startsWith("."));   // relative imports resolve later
    }

    @Test
    void findsTheMostComplexFilesInTheCodebase() {
        RepositoryScanner.ScanResult result = scanner().scan(repositoryRoot().resolve("src"));

        List<ScannedFile> byComplexity = result.files().stream()
                .filter(ScannedFile::isProgramming)
                .sorted(Comparator.comparingInt(
                        (ScannedFile file) -> file.metrics().cyclomaticComplexity()).reversed())
                .limit(5)
                .toList();

        assertThat(byComplexity).isNotEmpty();
        // A real codebase always has some branching somewhere.
        assertThat(byComplexity.get(0).metrics().cyclomaticComplexity())
                .isGreaterThan(1);
    }

    @Test
    void skipsDirectoriesThatAreNeverSource() {
        RepositoryScanner.ScanResult result = scanner().scan(repositoryRoot().resolve("src"));

        assertThat(result.files()).noneSatisfy(file ->
                assertThat(file.path()).contains("node_modules"));
        assertThat(result.files()).noneSatisfy(file ->
                assertThat(file.path()).contains("/.git/"));
    }

    @Test
    void buildsAGraphOfThisProjectsOwnModules() {
        RepositoryScanner.ScanResult scan = scanner().scan(repositoryRoot().resolve("src"));

        ArchitectureGraph graph =
                new ArchitectureGraphBuilder(LIMITS).build(scan.files(), Map.of());

        assertThat(graph.nodes())
                .as("the frontend is organised into feature modules")
                .hasSizeGreaterThan(5);
        assertThat(graph.nodes()).hasSizeLessThanOrEqualTo(LIMITS.maxGraphNodes());

        assertThat(graph.edges())
                .as("relative imports between modules become edges")
                .isNotEmpty();

        // Every edge names nodes that exist.
        Set<String> keys = graph.nodes().stream()
                .map(ArchitectureGraph.Node::key)
                .collect(java.util.stream.Collectors.toSet());
        assertThat(graph.edges()).allSatisfy(edge -> {
            assertThat(keys).contains(edge.source());
            assertThat(keys).contains(edge.target());
        });

        // Fan-in and fan-out are derived from the edge list, so they must agree.
        Map<String, Long> outDegree = graph.edges().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        ArchitectureGraph.Edge::source,
                        java.util.stream.Collectors.counting()));
        assertThat(graph.nodes()).allSatisfy(node ->
                assertThat((long) node.fanOut())
                        .isEqualTo(outDegree.getOrDefault(node.key(), 0L)));
    }

    @Test
    void classifiesTheAngularLayersItCanRecognise() {
        RepositoryScanner.ScanResult scan = scanner().scan(repositoryRoot().resolve("src"));
        ArchitectureGraph graph =
                new ArchitectureGraphBuilder(LIMITS).build(scan.files(), Map.of());

        Set<com.softwaredna.common.domain.ArchitectureLayer> layers = graph.nodes().stream()
                .map(ArchitectureGraph.Node::layer)
                .collect(java.util.stream.Collectors.toSet());

        assertThat(layers)
                .as("a layered frontend should not collapse into a single layer")
                .hasSizeGreaterThan(1);
    }

    @Test
    void treatsThirdPartyPackagesAsExternalRatherThanInternalEdges() {
        RepositoryScanner.ScanResult scan = scanner().scan(repositoryRoot().resolve("src"));
        ImportResolver resolver = new ImportResolver(scan.files());

        ScannedFile anyAngularImporter = scan.files().stream()
                .filter(file -> file.imports().contains("@angular/core"))
                .findFirst()
                .orElseThrow();

        assertThat(resolver.resolve(anyAngularImporter, "@angular/core"))
                .as("a package is not a file in this repository")
                .isEmpty();
        assertThat(ImportResolver.packageNameOf("@angular/core/testing"))
                .isEqualTo("@angular/core");
        assertThat(ImportResolver.packageNameOf("rxjs/operators")).isEqualTo("rxjs");
    }

    @Test
    void readsThisRepositorysOwnGitHistory() throws IOException {
        Path root = repositoryRoot();
        if (!Files.isDirectory(root.resolve(".git"))) {
            // The working tree may not be a git repository in every environment.
            return;
        }

        GitHistory history = new GitHistoryAnalyzer(LIMITS).analyse(root);

        assertThat(history.isEmpty()).isFalse();
        assertThat(history.commitsScanned()).isPositive();
        assertThat(history.authors()).isNotEmpty();
        assertThat(history.firstCommitAt()).isNotNull();
        assertThat(history.lastCommitAt()).isNotNull();
        assertThat(history.lastCommitAt()).isAfterOrEqualTo(history.firstCommitAt());

        // Author identity is hashed, never a stored address.
        assertThat(history.authors()).allSatisfy(author -> {
            assertThat(author.emailHash()).hasSize(64);
            assertThat(author.emailHash()).doesNotContain("@");
        });

        assertThat(history.byYear()).isNotEmpty();
        assertThat(history.fileHistories()).isNotEmpty();
        assertThat(history.fileHistories().values())
                .allSatisfy(file -> assertThat(file.changes()).isPositive());
    }
}
