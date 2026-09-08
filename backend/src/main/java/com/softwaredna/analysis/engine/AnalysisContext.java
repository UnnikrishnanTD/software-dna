package com.softwaredna.analysis.engine;

import com.softwaredna.architecture.ArchitectureGraph;
import com.softwaredna.codebase.model.ScannedFile;
import com.softwaredna.codebase.scan.RepositoryScanner;
import com.softwaredna.dependency.ManifestParser;
import com.softwaredna.evolution.git.GitHistory;
import com.softwaredna.repository.model.RepositoryMetadata;

import java.util.List;
import java.util.Map;

/**
 * Everything the scoring modules are allowed to read.
 *
 * <p>Modules receive this and nothing else: no database, no filesystem, no
 * network. That makes each one a pure function of measured inputs, which is
 * what makes the scores reproducible and the modules testable in isolation.
 */
public record AnalysisContext(
        RepositoryMetadata repository,
        RepositoryScanner.ScanResult scan,
        GitHistory history,
        ArchitectureGraph graph,
        ManifestParser.ManifestResult manifests,
        /** Repository-relative path to contents, for the few files worth re-reading. */
        Map<String, String> keyFileContents,
        /** Coverage by file path, populated only when a report was found. */
        Map<String, Double> coverageByPath,
        String coverageSource
) {

    /** Source files, excluding tests and generated code. */
    public List<ScannedFile> productionFiles() {
        return scan.files().stream()
                .filter(ScannedFile::isProgramming)
                .filter(file -> !file.test())
                .filter(file -> !file.generated())
                .toList();
    }

    public List<ScannedFile> testFiles() {
        return scan.files().stream()
                .filter(ScannedFile::isProgramming)
                .filter(ScannedFile::test)
                .toList();
    }

    /** Files whose metrics came from a real parse rather than a line count. */
    public List<ScannedFile> structurallyAnalysedFiles() {
        return productionFiles().stream()
                .filter(file -> file.metrics().structuralAnalysis())
                .toList();
    }

    public boolean hasCoverage() {
        return coverageSource != null && !coverageByPath.isEmpty();
    }

    public Map<String, Integer> changesByPath() {
        return history.fileHistories().entrySet().stream().collect(
                java.util.stream.Collectors.toMap(Map.Entry::getKey,
                        entry -> entry.getValue().changes()));
    }
}
