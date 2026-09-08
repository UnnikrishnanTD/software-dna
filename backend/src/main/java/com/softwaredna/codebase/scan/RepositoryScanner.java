package com.softwaredna.codebase.scan;

import com.softwaredna.codebase.analyzer.LanguageAnalyzer;
import com.softwaredna.codebase.analyzer.LineCountingAnalyzer;
import com.softwaredna.codebase.model.Language;
import com.softwaredna.codebase.model.ScannedFile;
import com.softwaredna.codebase.model.SourceMetrics;
import com.softwaredna.common.exception.ApiException;
import com.softwaredna.common.exception.ErrorCode;
import com.softwaredna.config.AnalysisLimits;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Walks a checked-out repository and measures every source file.
 *
 * <p>Three properties matter here, and all three are security or resilience
 * properties rather than analytical ones:
 *
 * <ul>
 *   <li>Symbolic links are never followed. A repository can contain a link to
 *       {@code /etc/passwd} or a loop back to its own parent; neither is
 *       traversed, so the scan cannot escape the workspace or spin forever.</li>
 *   <li>Files are read with a hard size cap and decoded strictly. A binary
 *       that happens to carry a source extension fails to decode and is
 *       skipped rather than producing nonsense metrics.</li>
 *   <li>The file count is capped. Reaching the cap is recorded on the result
 *       so downstream scoring can lower its confidence instead of silently
 *       reporting a partial repository as a whole one.</li>
 * </ul>
 */
@Component
public class RepositoryScanner {

    private static final Logger log = LoggerFactory.getLogger(RepositoryScanner.class);

    /** Directories that are never source, and are frequently enormous. */
    private static final Set<String> IGNORED_DIRECTORIES = Set.of(
            ".git", ".hg", ".svn", "node_modules", "bower_components", "vendor",
            "target", "build", "out", "bin", "obj", "dist", ".next", ".nuxt",
            ".angular", ".gradle", ".mvn", ".idea", ".vscode", "__pycache__",
            ".venv", "venv", "env", ".tox", ".cache", "coverage", ".nyc_output",
            "Pods", "DerivedData", ".terraform", ".serverless");

    /** Path fragments that mark a file as a test. */
    private static final Set<String> TEST_DIRECTORY_NAMES = Set.of(
            "test", "tests", "spec", "specs", "__tests__", "__mocks__", "testing");

    private final List<LanguageAnalyzer> analyzers;
    private final LineCountingAnalyzer fallback;
    private final AnalysisLimits limits;

    public RepositoryScanner(List<LanguageAnalyzer> analyzers,
                             LineCountingAnalyzer fallback,
                             AnalysisLimits limits) {
        this.analyzers = analyzers;
        this.fallback = fallback;
        this.limits = limits;
    }

    /** The outcome of one scan, including what it had to leave out. */
    public record ScanResult(
            List<ScannedFile> files,
            Map<Language, Integer> linesByLanguage,
            int filesSkippedTooLarge,
            int filesSkippedUnreadable,
            boolean truncated
    ) {
        public int totalLinesOfCode() {
            return files.stream()
                    .filter(ScannedFile::isProgramming)
                    .mapToInt(file -> file.metrics().linesOfCode())
                    .sum();
        }
    }

    public ScanResult scan(Path checkoutRoot) {
        Path root = checkoutRoot.toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new ApiException(ErrorCode.ANALYSIS_FAILED,
                    "The checked-out repository is not a directory.");
        }

        Map<Language, LanguageAnalyzer> byLanguage = indexAnalyzers();
        List<ScannedFile> files = new ArrayList<>();
        Map<Language, Integer> linesByLanguage = new EnumMap<>(Language.class);

        int skippedTooLarge = 0;
        int skippedUnreadable = 0;
        boolean truncated = false;

        // NOFOLLOW is the default for Files.walk, but state it explicitly:
        // following links here would be a sandbox escape.
        try (var walk = Files.walk(root, FileVisitOption.values().length == 0
                ? new FileVisitOption[0] : new FileVisitOption[0])) {

            var iterator = walk.iterator();
            while (iterator.hasNext()) {
                Path path = iterator.next();

                if (files.size() >= limits.maxFilesScanned()) {
                    truncated = true;
                    break;
                }
                if (Files.isSymbolicLink(path)) {
                    continue;
                }
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }

                Path relative = root.relativize(path);
                if (isIgnored(relative)) {
                    continue;
                }

                long size;
                try {
                    size = Files.size(path);
                } catch (IOException e) {
                    skippedUnreadable++;
                    continue;
                }
                if (size > limits.maxFileBytes()) {
                    skippedTooLarge++;
                    continue;
                }

                String content;
                try {
                    content = Files.readString(path, StandardCharsets.UTF_8);
                } catch (MalformedInputException e) {
                    // Binary content wearing a source extension.
                    skippedUnreadable++;
                    continue;
                } catch (IOException e) {
                    skippedUnreadable++;
                    continue;
                }

                ScannedFile scanned = measure(relative, content, size, byLanguage);
                files.add(scanned);
                if (scanned.isProgramming()) {
                    linesByLanguage.merge(scanned.language(),
                            scanned.metrics().linesOfCode(), Integer::sum);
                }
            }
        } catch (IOException e) {
            throw new ApiException(ErrorCode.ANALYSIS_FAILED,
                    "The repository could not be read.", e);
        }

        if (files.isEmpty()) {
            throw new ApiException(ErrorCode.REPOSITORY_EMPTY,
                    "The repository contains no analysable files.");
        }
        if (truncated) {
            log.warn("Scan truncated at {} files; results are partial",
                    limits.maxFilesScanned());
        }

        return new ScanResult(List.copyOf(files), Map.copyOf(linesByLanguage),
                skippedTooLarge, skippedUnreadable, truncated);
    }

    private ScannedFile measure(Path relative, String content, long size,
                                Map<Language, LanguageAnalyzer> byLanguage) {
        String fileName = relative.getFileName().toString();
        String relativePath = toPortablePath(relative);
        Language language = Language.forFile(fileName);

        LanguageAnalyzer analyzer = byLanguage.get(language);
        LanguageAnalyzer.Result result = analyzer != null
                ? analyzer.analyse(content, relativePath)
                : fallback.analyse(content, language);

        SourceMetrics metrics = result.metrics();

        // Signals are detected here, in the same pass that already has the
        // file contents in hand.
        Map<String, Integer> signals = SignalDetector.detect(
                content, SignalDetector.maskFor(content, language), language);

        return new ScannedFile(
                relativePath,
                fileName,
                Language.extensionOf(fileName),
                language,
                looksLikeTest(relativePath, fileName),
                looksGenerated(relativePath, content),
                size,
                metrics,
                result.imports(),
                result.declaredTypes(),
                signals);
    }

    private Map<Language, LanguageAnalyzer> indexAnalyzers() {
        Map<Language, LanguageAnalyzer> index = new HashMap<>();
        for (LanguageAnalyzer analyzer : analyzers) {
            for (Language language : analyzer.supportedLanguages()) {
                index.put(language, analyzer);
            }
        }
        return index;
    }

    private boolean isIgnored(Path relative) {
        for (Path segment : relative) {
            if (IGNORED_DIRECTORIES.contains(segment.toString())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Recognises the naming conventions the major ecosystems actually use:
     * a test directory, a {@code .spec}/{@code .test} infix, or the Maven and
     * Gradle {@code src/test} layout.
     */
    private boolean looksLikeTest(String relativePath, String fileName) {
        String lowerPath = relativePath.toLowerCase(Locale.ROOT);
        String lowerName = fileName.toLowerCase(Locale.ROOT);

        if (lowerPath.contains("src/test/") || lowerPath.contains("src/it/")) {
            return true;
        }
        for (String directory : TEST_DIRECTORY_NAMES) {
            if (lowerPath.contains("/" + directory + "/") || lowerPath.startsWith(directory + "/")) {
                return true;
            }
        }
        return lowerName.contains(".spec.") || lowerName.contains(".test.")
                || lowerName.endsWith("test.java") || lowerName.endsWith("tests.java")
                || lowerName.endsWith("_test.go") || lowerName.startsWith("test_");
    }

    /**
     * Generated files distort every metric they touch, so they are flagged and
     * excluded from quality scoring. Detection is by the conventional banner
     * comment plus a few well-known paths.
     */
    private boolean looksGenerated(String relativePath, String content) {
        String lowerPath = relativePath.toLowerCase(Locale.ROOT);
        if (lowerPath.contains("/generated/") || lowerPath.contains("/gen/")
                || lowerPath.endsWith(".g.dart") || lowerPath.endsWith(".pb.go")
                || lowerPath.endsWith("_pb2.py") || lowerPath.endsWith(".min.js")) {
            return true;
        }
        // Only the head of the file: the marker is a banner by convention.
        String head = content.length() > 512 ? content.substring(0, 512) : content;
        String lowerHead = head.toLowerCase(Locale.ROOT);
        return lowerHead.contains("do not edit")
                || lowerHead.contains("auto-generated")
                || lowerHead.contains("autogenerated")
                || lowerHead.contains("@generated");
    }

    /** Repository paths always use forward slashes, whatever the host OS. */
    private static String toPortablePath(Path relative) {
        return relative.toString().replace(java.io.File.separatorChar, '/');
    }
}
