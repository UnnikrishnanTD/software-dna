package com.softwaredna.codebase.analyzer;

import com.softwaredna.codebase.model.Language;
import com.softwaredna.codebase.model.SourceMetrics;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Fallback for files with no structural analyzer.
 *
 * <p>Counts lines and, where the comment syntax is unambiguous, separates
 * comments from code. Reports {@code structuralAnalysis = false} so nothing
 * downstream mistakes its zeroes for measured values.
 */
@Component
@Order(Integer.MAX_VALUE)
public class LineCountingAnalyzer implements LanguageAnalyzer {

    private static final Set<Language> HASH_COMMENT_LANGUAGES = Set.of(
            Language.PYTHON, Language.RUBY, Language.SHELL,
            Language.YAML, Language.DOCKERFILE);

    @Override
    public Set<Language> supportedLanguages() {
        // Selected explicitly by the scanner as a fallback, not by language.
        return Set.of();
    }

    @Override
    public Result analyse(String source, String filePath) {
        SourceText text = SourceText.countLines(source);
        return Result.of(SourceMetrics.linesOnly(
                text.linesTotal(), text.linesOfCode(), text.linesComment(), text.linesBlank()));
    }

    /** Counts lines using the comment style appropriate to the language. */
    public Result analyse(String source, Language language) {
        SourceText text = HASH_COMMENT_LANGUAGES.contains(language)
                ? SourceText.lexHashComments(source)
                : SourceText.countLines(source);
        return Result.of(SourceMetrics.linesOnly(
                text.linesTotal(), text.linesOfCode(), text.linesComment(), text.linesBlank()));
    }
}
