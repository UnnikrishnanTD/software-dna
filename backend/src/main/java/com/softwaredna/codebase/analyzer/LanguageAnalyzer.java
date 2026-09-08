package com.softwaredna.codebase.analyzer;

import com.softwaredna.codebase.model.Language;
import com.softwaredna.codebase.model.SourceMetrics;

import java.util.List;
import java.util.Set;

/**
 * Extracts structure from one file's source.
 *
 * <p>Implementations are registered as beans and selected by language, so
 * supporting a new language is an added class rather than a change to the
 * scanner. An analyzer must never guess: if it cannot measure something it
 * reports zero with {@code structuralAnalysis = false} rather than an
 * estimate.
 */
public interface LanguageAnalyzer {

    /** Languages this analyzer can parse. */
    Set<Language> supportedLanguages();

    /** The result of analysing one file. */
    record Result(SourceMetrics metrics, List<String> imports, List<String> declaredTypes) {
        public static Result of(SourceMetrics metrics) {
            return new Result(metrics, List.of(), List.of());
        }
    }

    /**
     * @param source   full file contents
     * @param filePath repository-relative path, for diagnostics only
     */
    Result analyse(String source, String filePath);
}
