package com.softwaredna.codebase.model;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Languages the scanner recognises, and how to recognise them.
 *
 * <p>Recognition is by extension. That is deliberate: content sniffing is
 * slower and, for source code, rarely more accurate than the extension the
 * ecosystem itself relies on.
 */
public enum Language {

    JAVA("Java", true),
    KOTLIN("Kotlin", true),
    TYPESCRIPT("TypeScript", true),
    JAVASCRIPT("JavaScript", true),
    PYTHON("Python", true),
    GO("Go", true),
    RUBY("Ruby", true),
    CSHARP("C#", true),
    RUST("Rust", true),
    PHP("PHP", true),
    SCALA("Scala", true),
    SWIFT("Swift", true),
    HTML("HTML", false),
    CSS("CSS", false),
    SCSS("SCSS", false),
    SQL("SQL", false),
    SHELL("Shell", false),
    YAML("YAML", false),
    JSON("JSON", false),
    XML("XML", false),
    MARKDOWN("Markdown", false),
    DOCKERFILE("Dockerfile", false),
    OTHER("Other", false);

    private final String displayName;
    /** Whether structural metrics (complexity, declarations) are meaningful. */
    private final boolean programming;

    Language(String displayName, boolean programming) {
        this.displayName = displayName;
        this.programming = programming;
    }

    public String displayName() {
        return displayName;
    }

    public boolean isProgramming() {
        return programming;
    }

    private static final Map<String, Language> BY_EXTENSION = Map.ofEntries(
            Map.entry("java", JAVA),
            Map.entry("kt", KOTLIN), Map.entry("kts", KOTLIN),
            Map.entry("ts", TYPESCRIPT), Map.entry("tsx", TYPESCRIPT),
            Map.entry("mts", TYPESCRIPT), Map.entry("cts", TYPESCRIPT),
            Map.entry("js", JAVASCRIPT), Map.entry("jsx", JAVASCRIPT),
            Map.entry("mjs", JAVASCRIPT), Map.entry("cjs", JAVASCRIPT),
            Map.entry("py", PYTHON),
            Map.entry("go", GO),
            Map.entry("rb", RUBY),
            Map.entry("cs", CSHARP),
            Map.entry("rs", RUST),
            Map.entry("php", PHP),
            Map.entry("scala", SCALA),
            Map.entry("swift", SWIFT),
            Map.entry("html", HTML), Map.entry("htm", HTML),
            Map.entry("css", CSS),
            Map.entry("scss", SCSS), Map.entry("sass", SCSS), Map.entry("less", SCSS),
            Map.entry("sql", SQL),
            Map.entry("sh", SHELL), Map.entry("bash", SHELL), Map.entry("zsh", SHELL),
            Map.entry("yml", YAML), Map.entry("yaml", YAML),
            Map.entry("json", JSON),
            Map.entry("xml", XML),
            Map.entry("md", MARKDOWN), Map.entry("markdown", MARKDOWN));

    private static final Set<String> DOCKERFILE_NAMES =
            Set.of("dockerfile", "containerfile");

    public static Language forFile(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (DOCKERFILE_NAMES.contains(lower) || lower.startsWith("dockerfile.")) {
            return DOCKERFILE;
        }
        return BY_EXTENSION.getOrDefault(extensionOf(lower), OTHER);
    }

    public static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 || dot == fileName.length() - 1
                ? ""
                : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
