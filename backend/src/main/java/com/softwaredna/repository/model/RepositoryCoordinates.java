package com.softwaredna.repository.model;

import com.softwaredna.common.exception.ApiException;
import com.softwaredna.common.exception.ErrorCode;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A validated {@code owner/name} pair on a supported provider.
 *
 * <p>Parsing extracts the host explicitly and checks it against an allow-list,
 * rather than trying to filter bad input out of one large pattern. That is
 * what makes server-side request forgery impossible here: a reference is only
 * accepted if it demonstrably names {@code github.com}, so an internal
 * address, a link-local metadata endpoint or a {@code file://} path cannot be
 * expressed in a form this parser will return.
 *
 * <p>The resulting owner and name are also constrained to GitHub's own
 * character set, so neither can carry a path separator, a shell metacharacter
 * or a {@code ..} segment into the clone or the workspace path.
 */
public record RepositoryCoordinates(String provider, String owner, String name) {

    public static final String GITHUB = "github";

    private static final Set<String> ALLOWED_HOSTS = Set.of("github.com", "www.github.com");

    /** GitHub's own rules: alphanumerics, dot, dash, underscore; 1-100 chars. */
    private static final Pattern SEGMENT =
            Pattern.compile("^[A-Za-z0-9](?:[A-Za-z0-9._-]{0,98}[A-Za-z0-9])?$");

    private static final Pattern SCHEME = Pattern.compile("^([A-Za-z][A-Za-z0-9+.-]*)://");

    private static final int MAX_REFERENCE_LENGTH = 512;

    public RepositoryCoordinates {
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("provider is required");
        }
        if (owner == null || owner.isBlank() || name == null || name.isBlank()) {
            throw new IllegalArgumentException("owner and name are required");
        }
    }

    public static RepositoryCoordinates parse(String reference) {
        String remainder = sanitise(reference);

        // 1. Strip any scheme, and reject schemes that are not plain HTTP(S)
        //    or SSH. `file://`, `git://` and friends never reach the network
        //    layer we intend to use.
        var schemeMatcher = SCHEME.matcher(remainder);
        if (schemeMatcher.find()) {
            String scheme = schemeMatcher.group(1).toLowerCase(Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https") && !scheme.equals("ssh")) {
                throw new ApiException(ErrorCode.UNSUPPORTED_HOST,
                        "Only https and ssh GitHub references are supported.");
            }
            remainder = remainder.substring(schemeMatcher.end());
        }

        // 2. Strip user info. `git@github.com:owner/repo` and
        //    `https://token@github.com/owner/repo` both arrive here.
        int at = remainder.indexOf('@');
        int firstSeparator = indexOfFirst(remainder, '/', ':');
        if (at >= 0 && (firstSeparator < 0 || at < firstSeparator)) {
            remainder = remainder.substring(at + 1);
        }

        // 3. If a host is present it must be one we allow. A reference with no
        //    host at all is the bare `owner/repo` form.
        String host = extractHost(remainder);
        if (host != null) {
            if (!ALLOWED_HOSTS.contains(host.toLowerCase(Locale.ROOT))) {
                throw new ApiException(ErrorCode.UNSUPPORTED_HOST,
                        "Only github.com repositories can be analysed.");
            }
            remainder = remainder.substring(host.length());
            if (!remainder.isEmpty() && (remainder.charAt(0) == '/' || remainder.charAt(0) == ':')) {
                remainder = remainder.substring(1);
            }
        }

        // 4. What is left must be exactly `owner/name`, nothing deeper. This
        //    rejects tree/blob URLs as well as any traversal attempt.
        String path = trimSlashes(remainder);
        String[] segments = path.split("/");
        if (segments.length != 2) {
            throw new ApiException(ErrorCode.INVALID_REPOSITORY_URL,
                    "That does not look like a GitHub repository. "
                            + "Try github.com/owner/repository.");
        }

        String owner = segments[0];
        String name = stripGitSuffix(segments[1]);

        if (!SEGMENT.matcher(owner).matches() || !SEGMENT.matcher(name).matches()) {
            throw new ApiException(ErrorCode.INVALID_REPOSITORY_URL,
                    "That does not look like a GitHub repository. "
                            + "Try github.com/owner/repository.");
        }

        return new RepositoryCoordinates(GITHUB, owner, name);
    }

    private static String sanitise(String reference) {
        if (reference == null || reference.isBlank()) {
            throw new ApiException(ErrorCode.INVALID_REPOSITORY_URL,
                    "A repository reference is required.");
        }
        String trimmed = reference.trim();
        if (trimmed.length() > MAX_REFERENCE_LENGTH) {
            throw new ApiException(ErrorCode.INVALID_REPOSITORY_URL,
                    "That repository reference is too long.");
        }
        // Control characters, including the NUL and newline tricks used to
        // smuggle extra arguments into downstream tooling.
        if (trimmed.chars().anyMatch(c -> c < 0x20 || c == 0x7F)) {
            throw new ApiException(ErrorCode.INVALID_REPOSITORY_URL,
                    "That repository reference contains invalid characters.");
        }
        return trimmed;
    }

    /**
     * Returns the host portion, or null when the reference carries no host.
     *
     * A leading segment counts as a host when it contains a dot, a port, or is
     * literally {@code localhost} — which is precisely the shape that would
     * otherwise let an internal address through.
     */
    private static String extractHost(String remainder) {
        int end = indexOfFirst(remainder, '/', ':');
        String candidate = end < 0 ? remainder : remainder.substring(0, end);
        if (candidate.isEmpty()) {
            return null;
        }

        boolean followedByPort = end >= 0 && remainder.charAt(end) == ':';
        boolean looksLikeHost = candidate.indexOf('.') >= 0
                || candidate.equalsIgnoreCase("localhost")
                || followedByPort;

        if (!looksLikeHost) {
            return null;
        }
        // A host with a port is never GitHub, and is rejected by the
        // allow-list check that follows.
        return candidate;
    }

    private static int indexOfFirst(String value, char a, char b) {
        int first = value.indexOf(a);
        int second = value.indexOf(b);
        if (first < 0) return second;
        if (second < 0) return first;
        return Math.min(first, second);
    }

    private static String trimSlashes(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && value.charAt(start) == '/') start++;
        while (end > start && value.charAt(end - 1) == '/') end--;
        return value.substring(start, end);
    }

    private static String stripGitSuffix(String value) {
        return value.length() > 4 && value.regionMatches(true, value.length() - 4, ".git", 0, 4)
                ? value.substring(0, value.length() - 4)
                : value;
    }

    public String fullName() {
        return owner + "/" + name;
    }

    /** Lower-cased key for equality and lookups; GitHub names are case-insensitive. */
    public String key() {
        return (provider + ":" + owner + "/" + name).toLowerCase(Locale.ROOT);
    }

    public String httpsUrl() {
        return "https://github.com/" + owner + "/" + name;
    }

    public String cloneUrl() {
        return httpsUrl() + ".git";
    }
}
