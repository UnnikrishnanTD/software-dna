package com.softwaredna.codebase.analyzer;

import com.softwaredna.codebase.model.Language;
import com.softwaredna.codebase.model.SourceMetrics;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Analyses TypeScript and JavaScript.
 *
 * <p>There is no production-grade TypeScript parser for the JVM, so this is a
 * lexical analyzer rather than a syntactic one. It is honest about what that
 * means:
 *
 * <ul>
 *   <li>It runs over {@link SourceText}-masked source, so keywords inside
 *       comments, strings and template literals are never counted. This is the
 *       difference between a usable heuristic and a misleading one.</li>
 *   <li>Decision-point counting yields the same number as a real McCabe
 *       calculation for ordinary code. It will differ where control flow is
 *       expressed through constructs it does not model.</li>
 *   <li>It does not resolve types, follow re-exports, or understand
 *       declaration merging. Import specifiers are reported exactly as
 *       written; resolving them is the architecture builder's job.</li>
 * </ul>
 *
 * <p>Results are still marked {@code structuralAnalysis = true}: the counts are
 * measured from the file, not estimated from its size.
 */
@Component
public class TypeScriptLanguageAnalyzer implements LanguageAnalyzer {

    /** `import ... from 'x'`, `export ... from 'x'`, and bare `import 'x'`. */
    private static final Pattern IMPORT_FROM = Pattern.compile(
            "(?:^|[;}\\s])(?:import|export)\\s[^;'\"]*?from\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern BARE_IMPORT = Pattern.compile(
            "(?:^|[;}\\s])import\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern REQUIRE = Pattern.compile(
            "require\\s*\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)");
    private static final Pattern DYNAMIC_IMPORT = Pattern.compile(
            "import\\s*\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)");

    private static final Pattern TYPE_DECLARATION = Pattern.compile(
            "(?:^|[;}\\s])(?:export\\s+)?(?:default\\s+)?(?:abstract\\s+)?"
                    + "(class|interface|enum|type)\\s+([A-Za-z_$][A-Za-z0-9_$]*)");

    /** Named functions, methods, and arrow-function assignments. */
    private static final Pattern FUNCTION_LIKE = Pattern.compile(
            "(?:^|[;{}\\s])(?:async\\s+)?function\\s*\\*?\\s*[A-Za-z_$][A-Za-z0-9_$]*\\s*\\("
                    + "|=>"
                    + "|(?:^|[;{}])\\s*(?:public|private|protected|static|async|\\*|\\s)*"
                    + "[A-Za-z_$][A-Za-z0-9_$]*\\s*\\([^)]*\\)\\s*\\{");

    /**
     * Decision points. Word-boundary anchored so `iffy` is not an `if`, and
     * `?.` is excluded from the ternary count because optional chaining is not
     * a branch in the McCabe sense.
     */
    private static final Pattern DECISION_KEYWORD = Pattern.compile(
            "\\b(?:if|for|while|case|catch)\\b");
    private static final Pattern LOGICAL_OPERATOR = Pattern.compile("&&|\\|\\||\\?\\?");
    private static final Pattern TERNARY = Pattern.compile("\\?(?![.?:])");

    @Override
    public Set<Language> supportedLanguages() {
        return Set.of(Language.TYPESCRIPT, Language.JAVASCRIPT);
    }

    @Override
    public Result analyse(String source, String filePath) {
        SourceText text = SourceText.lexCFamily(source, true);
        // Keyword and operator counting must not see comments or string
        // contents; import extraction must see string contents, because the
        // specifier is one.
        String code = text.masked();
        List<String> imports = extractImports(text.withoutComments());
        List<String> declaredTypes = extractDeclaredTypes(code);

        SourceMetrics metrics = new SourceMetrics(
                text.linesTotal(),
                text.linesOfCode(),
                text.linesComment(),
                text.linesBlank(),
                cyclomaticComplexity(code),
                declaredTypes.size(),
                countMatches(FUNCTION_LIKE, code),
                imports.size(),
                maxNestingDepth(code),
                true);

        return new Result(metrics, imports, declaredTypes);
    }

    private List<String> extractImports(String code) {
        // A set: re-importing the same module twice is one dependency.
        Set<String> specifiers = new LinkedHashSet<>();
        for (Pattern pattern : List.of(IMPORT_FROM, BARE_IMPORT, REQUIRE, DYNAMIC_IMPORT)) {
            Matcher matcher = pattern.matcher(code);
            while (matcher.find()) {
                String specifier = matcher.group(1).trim();
                if (!specifier.isEmpty()) {
                    specifiers.add(specifier);
                }
            }
        }
        return List.copyOf(specifiers);
    }

    private List<String> extractDeclaredTypes(String code) {
        List<String> names = new ArrayList<>();
        Matcher matcher = TYPE_DECLARATION.matcher(code);
        while (matcher.find()) {
            names.add(matcher.group(2));
        }
        return names;
    }

    private int cyclomaticComplexity(String code) {
        int decisions = countMatches(DECISION_KEYWORD, code)
                + countMatches(LOGICAL_OPERATOR, code)
                + countMatches(TERNARY, code);
        return 1 + decisions;
    }

    /**
     * Deepest brace nesting, ignoring the outermost level.
     *
     * <p>A lexical approximation: it cannot distinguish an object literal from
     * a block. It is used only as a supporting signal for maintainability, not
     * as a headline metric.
     */
    private int maxNestingDepth(String code) {
        int depth = 0;
        int deepest = 0;
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c == '{') {
                depth++;
                deepest = Math.max(deepest, depth);
            } else if (c == '}') {
                depth = Math.max(0, depth - 1);
            }
        }
        return Math.max(0, deepest - 1);
    }

    private static int countMatches(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }
}
