package com.softwaredna.evolution.git;

import com.softwaredna.common.exception.ApiException;
import com.softwaredna.common.exception.ErrorCode;
import com.softwaredna.config.AnalysisLimits;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Reads a repository's history with JGit.
 *
 * <p>Walks the first-parent chain of the analysed branch and diffs each commit
 * against its parent to attribute changes to files. That is the expensive part
 * of the analysis, so it is bounded by {@link AnalysisLimits#maxCommitsScanned()}
 * and reports when it stopped early rather than presenting a partial history as
 * a complete one.
 *
 * <p>Merge commits are skipped for attribution. Diffing a merge against its
 * first parent re-attributes every change from the merged branch, which would
 * inflate churn on exactly the files that are already busiest.
 *
 * <p>Author email addresses are hashed rather than stored. Counting distinct
 * contributors and attributing ownership needs a stable identifier, not a
 * contact detail.
 */
@Component
public class GitHistoryAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(GitHistoryAnalyzer.class);

    /**
     * Conventional-commit prefixes and the plain-English forms teams use.
     * Anchored to the start of the subject so "prefix the fix" is not a match.
     */
    private static final Pattern BUG_FIX_SUBJECT = Pattern.compile(
            "^(?:fix|bugfix|hotfix|patch)\\b"
                    + "|^fix\\(|^\\[fix\\]"
                    + "|^revert\\b"
                    + "|\\bfixe[sd]\\b"
                    + "|\\bresolve[sd]\\s+#"
                    + "|\\bclose[sd]\\s+#\\d+.*\\bbug\\b",
            Pattern.CASE_INSENSITIVE);

    private final AnalysisLimits limits;

    public GitHistoryAnalyzer(AnalysisLimits limits) {
        this.limits = limits;
    }

    public GitHistory analyse(Path checkout) {
        try (Git git = Git.open(checkout.toFile())) {
            Repository repository = git.getRepository();
            return walk(git, repository);
        } catch (IOException e) {
            throw new ApiException(ErrorCode.ANALYSIS_FAILED,
                    "The repository history could not be read.", e);
        }
    }

    private GitHistory walk(Git git, Repository repository) throws IOException {
        Map<String, MutableFile> files = new HashMap<>();
        Map<String, MutableAuthor> authors = new HashMap<>();
        Map<Integer, MutableYear> years = new TreeMap<>();

        int scanned = 0;
        int total = 0;
        boolean truncated = false;
        Instant first = null;
        Instant last = null;

        try (DiffFormatter diffs = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
            diffs.setRepository(repository);
            diffs.setDetectRenames(true);

            Iterable<RevCommit> commits;
            try {
                commits = git.log().call();
            } catch (GitAPIException e) {
                throw new ApiException(ErrorCode.ANALYSIS_FAILED,
                        "The repository history could not be walked.", e);
            }

            for (RevCommit commit : commits) {
                total++;
                if (scanned >= limits.maxCommitsScanned()) {
                    truncated = true;
                    continue;   // Keep counting to report a true total.
                }
                scanned++;

                PersonIdent author = commit.getAuthorIdent();
                Instant when = author == null
                        ? Instant.ofEpochSecond(commit.getCommitTime())
                        : author.getWhenAsInstant();

                if (first == null || when.isBefore(first)) first = when;
                if (last == null || when.isAfter(last)) last = when;

                String subject = commit.getShortMessage();
                boolean bugFix = subject != null && BUG_FIX_SUBJECT.matcher(subject).find();

                int year = when.atZone(ZoneOffset.UTC).getYear();
                MutableYear yearStats = years.computeIfAbsent(year, MutableYear::new);
                yearStats.commits++;
                if (bugFix) yearStats.bugFixCommits++;

                MutableAuthor authorStats = authors.computeIfAbsent(
                        emailHash(author), key -> new MutableAuthor(displayName(author), key));
                authorStats.commits++;
                authorStats.observe(when);
                yearStats.authors.add(authorStats.emailHash);

                // Merge commits would double-count the branch they merge.
                if (commit.getParentCount() > 1) {
                    continue;
                }

                for (String path : changedPaths(diffs, repository, commit)) {
                    MutableFile file = files.computeIfAbsent(path, MutableFile::new);
                    file.changes++;
                    if (bugFix) file.bugFixCommits++;
                    file.authors.merge(authorStats.emailHash, 1, Integer::sum);
                    file.authorNames.putIfAbsent(authorStats.emailHash, authorStats.displayName);
                    if (file.lastChangedAt == null || when.isAfter(file.lastChangedAt)) {
                        file.lastChangedAt = when;
                    }
                    authorStats.filesTouched.add(path);
                    yearStats.filesTouched.add(path);
                }
            }
        }

        if (scanned == 0) {
            throw new ApiException(ErrorCode.REPOSITORY_EMPTY,
                    "The repository has no commits to analyse.");
        }
        if (truncated) {
            log.warn("History truncated at {} of {} commits", scanned, total);
        }

        return new GitHistory(
                total,
                scanned,
                truncated,
                first,
                last,
                authors.values().stream().map(MutableAuthor::toRecord).toList(),
                files.entrySet().stream().collect(HashMap::new,
                        (map, entry) -> map.put(entry.getKey(), entry.getValue().toRecord()),
                        HashMap::putAll),
                years.values().stream().collect(TreeMap::new,
                        (map, value) -> map.put(value.year, value.toRecord()),
                        TreeMap::putAll));
    }

    private List<String> changedPaths(DiffFormatter diffs, Repository repository,
                                      RevCommit commit) throws IOException {
        AbstractTreeIterator oldTree = commit.getParentCount() == 0
                ? new EmptyTreeIterator()
                : treeOf(repository, commit.getParent(0));
        AbstractTreeIterator newTree = treeOf(repository, commit);

        List<String> paths = new ArrayList<>();
        for (DiffEntry entry : diffs.scan(oldTree, newTree)) {
            // For a delete the new path is /dev/null, so fall back to the old.
            String path = entry.getChangeType() == DiffEntry.ChangeType.DELETE
                    ? entry.getOldPath()
                    : entry.getNewPath();
            if (path != null && !path.equals(DiffEntry.DEV_NULL)) {
                paths.add(path);
            }
        }
        return paths;
    }

    private AbstractTreeIterator treeOf(Repository repository, RevCommit commit)
            throws IOException {
        try (var reader = repository.newObjectReader()) {
            CanonicalTreeParser parser = new CanonicalTreeParser();
            parser.reset(reader, commit.getTree().getId());
            return parser;
        }
    }

    private static String displayName(PersonIdent author) {
        if (author == null) return "unknown";
        String name = author.getName();
        return name == null || name.isBlank() ? "unknown" : name.trim();
    }

    /**
     * SHA-256 of the lower-cased email. Stable enough to count and attribute,
     * and not a stored contact detail.
     */
    private static String emailHash(PersonIdent author) {
        String email = author == null || author.getEmailAddress() == null
                ? "unknown"
                : author.getEmailAddress().trim().toLowerCase(Locale.ROOT);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(email.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required but unavailable", e);
        }
    }

    // ---- Mutable accumulators, converted to records once the walk ends ----

    private static final class MutableFile {
        final String path;
        int changes;
        int bugFixCommits;
        Instant lastChangedAt;
        final Map<String, Integer> authors = new HashMap<>();
        final Map<String, String> authorNames = new HashMap<>();

        MutableFile(String path) {
            this.path = path;
        }

        GitHistory.FileHistory toRecord() {
            String primary = authors.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(entry -> authorNames.getOrDefault(entry.getKey(), "unknown"))
                    .orElse("unknown");
            return new GitHistory.FileHistory(
                    path, changes, bugFixCommits, authors.size(), lastChangedAt, primary);
        }
    }

    private static final class MutableAuthor {
        final String displayName;
        final String emailHash;
        int commits;
        Instant firstCommitAt;
        Instant lastCommitAt;
        final Set<String> filesTouched = new HashSet<>();

        MutableAuthor(String displayName, String emailHash) {
            this.displayName = displayName;
            this.emailHash = emailHash;
        }

        void observe(Instant when) {
            if (firstCommitAt == null || when.isBefore(firstCommitAt)) firstCommitAt = when;
            if (lastCommitAt == null || when.isAfter(lastCommitAt)) lastCommitAt = when;
        }

        GitHistory.AuthorStats toRecord() {
            return new GitHistory.AuthorStats(displayName, emailHash, commits,
                    firstCommitAt, lastCommitAt, filesTouched.size());
        }
    }

    private static final class MutableYear {
        final int year;
        int commits;
        int bugFixCommits;
        final Set<String> authors = new HashSet<>();
        final Set<String> filesTouched = new HashSet<>();

        MutableYear(int year) {
            this.year = year;
        }

        GitHistory.YearStats toRecord() {
            return new GitHistory.YearStats(year, commits, authors.size(),
                    filesTouched.size(), bugFixCommits);
        }
    }
}
