package com.softwaredna.codebase.scan;

import com.softwaredna.codebase.analyzer.SourceText;
import com.softwaredna.codebase.model.Language;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects named signals in a file during the single scan pass.
 *
 * <p>These are lexical patterns, not proofs. Each one is named for what it
 * actually matches — {@code secret.highEntropyAssignment} rather than
 * "vulnerability" — because a scoring module that treats a pattern match as a
 * confirmed finding will produce confident nonsense.
 *
 * <p>Detection runs over comment-and-string-masked source where the pattern is
 * about code structure, and over the raw text where the pattern is about
 * literal content, such as a committed credential.
 */
public final class SignalDetector {

    private SignalDetector() {
    }

    // ---- Security-relevant signals ----------------------------------------

    /**
     * Credential shapes that are specific enough to be worth reporting. Generic
     * "password = ..." is deliberately excluded: it matches configuration
     * placeholders and test fixtures far more often than real secrets.
     */
    private static final List<Pattern> SECRET_PATTERNS = List.of(
            Pattern.compile("AKIA[0-9A-Z]{16}"),                       // AWS access key
            Pattern.compile("ghp_[A-Za-z0-9]{36}"),                    // GitHub PAT
            Pattern.compile("gho_[A-Za-z0-9]{36}"),
            Pattern.compile("github_pat_[A-Za-z0-9_]{50,}"),
            Pattern.compile("sk-[A-Za-z0-9]{32,}"),                    // OpenAI-style
            Pattern.compile("xox[baprs]-[A-Za-z0-9-]{10,}"),           // Slack
            Pattern.compile("-----BEGIN (?:RSA |EC |OPENSSH |PGP )?PRIVATE KEY-----"),
            Pattern.compile("(?i)aws_secret_access_key\\s*[=:]\\s*[\"']?[A-Za-z0-9/+=]{40}"));

    /**
     * APIs that execute or deserialise attacker-influenced input. Their presence
     * is not a vulnerability, but it is where vulnerabilities concentrate.
     */
    private static final Pattern DANGEROUS_API = Pattern.compile(
            "\\beval\\s*\\("
                    + "|\\bnew\\s+Function\\s*\\("
                    + "|\\bexecSync\\s*\\(|\\bchild_process\\b"
                    + "|\\bRuntime\\s*\\.\\s*getRuntime\\s*\\(\\s*\\)\\s*\\.\\s*exec\\b"
                    + "|\\bProcessBuilder\\b"
                    + "|\\bObjectInputStream\\b"
                    + "|\\bdangerouslySetInnerHTML\\b"
                    + "|\\binnerHTML\\s*="
                    + "|\\bbypassSecurityTrust");

    /** String-concatenated SQL, the classic injection shape. */
    private static final Pattern SQL_CONCATENATION = Pattern.compile(
            "(?i)(?:select|insert|update|delete)\\s+.{0,80}?[\"']\\s*\\+"
                    + "|(?i)executeQuery\\s*\\(\\s*[\"'].{0,80}?[\"']\\s*\\+");

    // ---- Performance-relevant signals -------------------------------------

    /** Awaiting inside a loop serialises work that could run concurrently. */
    private static final Pattern AWAIT_IN_LOOP = Pattern.compile(
            "(?s)\\b(?:for|while)\\s*\\([^)]{0,200}\\)\\s*\\{[^{}]{0,400}\\bawait\\b");

    /** Blocking IO on what is usually a request thread or the event loop. */
    private static final Pattern BLOCKING_IO = Pattern.compile(
            "\\breadFileSync\\b|\\bwriteFileSync\\b|\\bexecSync\\b"
                    + "|\\bThread\\s*\\.\\s*sleep\\s*\\("
                    + "|\\b\\.get\\(\\)\\s*;\\s*//\\s*block");

    /** Unbounded projection, the usual precursor to an N+1 or a full scan. */
    private static final Pattern SELECT_STAR = Pattern.compile(
            "(?i)select\\s+\\*\\s+from");

    // ---- Documentation signals --------------------------------------------

    private static final Pattern DOC_COMMENT = Pattern.compile("/\\*\\*");

    public static final String SECRET = "secret.credentialPattern";
    public static final String DANGEROUS_API_KEY = "security.dangerousApi";
    public static final String SQL_CONCAT = "security.sqlConcatenation";
    public static final String AWAIT_LOOP = "performance.awaitInLoop";
    public static final String BLOCKING_IO_KEY = "performance.blockingIo";
    public static final String SELECT_STAR_KEY = "performance.selectStar";
    public static final String DOC_COMMENTS = "documentation.docComments";

    /**
     * @param raw    file contents as written, for literal patterns
     * @param masked comment- and string-masked source, for structural patterns
     */
    public static Map<String, Integer> detect(String raw, String masked, Language language) {
        Map<String, Integer> signals = new LinkedHashMap<>();

        // Credentials are literal content, so they are searched in the raw text.
        int secrets = 0;
        for (Pattern pattern : SECRET_PATTERNS) {
            secrets += count(pattern, raw);
        }
        put(signals, SECRET, secrets);

        if (language.isProgramming()) {
            put(signals, DANGEROUS_API_KEY, count(DANGEROUS_API, masked));
            put(signals, SQL_CONCAT, count(SQL_CONCATENATION, raw));
            put(signals, AWAIT_LOOP, count(AWAIT_IN_LOOP, masked));
            put(signals, BLOCKING_IO_KEY, count(BLOCKING_IO, masked));
            put(signals, DOC_COMMENTS, count(DOC_COMMENT, raw));
        }
        put(signals, SELECT_STAR_KEY, count(SELECT_STAR, raw));

        return signals;
    }

    /** Produces the masked view the structural patterns need. */
    public static String maskFor(String source, Language language) {
        return switch (language) {
            case JAVA, KOTLIN, TYPESCRIPT, JAVASCRIPT, GO, CSHARP, RUST, SWIFT, SCALA, PHP ->
                    SourceText.lexCFamily(source,
                            language == Language.TYPESCRIPT || language == Language.JAVASCRIPT)
                            .masked();
            case PYTHON, RUBY, SHELL, YAML -> SourceText.lexHashComments(source).masked();
            default -> source;
        };
    }

    private static void put(Map<String, Integer> signals, String key, int count) {
        if (count > 0) {
            signals.put(key, count);
        }
    }

    private static int count(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
            if (count > 500) {
                // A pathological file should not dominate the scan.
                break;
            }
        }
        return count;
    }
}
