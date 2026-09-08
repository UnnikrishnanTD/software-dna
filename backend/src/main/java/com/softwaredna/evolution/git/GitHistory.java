package com.softwaredna.evolution.git;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Everything the analysis learns from the repository's history.
 *
 * <p>All of it is measured from the commit graph. Where a signal is a
 * heuristic rather than a fact — bug-fix detection reads commit subjects — the
 * field name and its documentation say so.
 */
public record GitHistory(
        int totalCommits,
        int commitsScanned,
        boolean truncated,
        Instant firstCommitAt,
        Instant lastCommitAt,
        List<AuthorStats> authors,
        Map<String, FileHistory> fileHistories,
        Map<Integer, YearStats> byYear
) {

    /** Per-author totals, keyed by a hash of the commit email. */
    public record AuthorStats(
            String displayName,
            String emailHash,
            int commits,
            Instant firstCommitAt,
            Instant lastCommitAt,
            int filesTouched
    ) {
    }

    /**
     * Per-file history.
     *
     * @param bugFixCommits commits whose subject matches a fix convention.
     *                      A heuristic, and treated as a weak signal.
     */
    public record FileHistory(
            String path,
            int changes,
            int bugFixCommits,
            int distinctAuthors,
            Instant lastChangedAt,
            String primaryAuthor
    ) {
    }

    /** Repository-wide totals for one calendar year. */
    public record YearStats(
            int year,
            int commits,
            int activeAuthors,
            int filesTouched,
            int bugFixCommits
    ) {
    }

    public boolean isEmpty() {
        return commitsScanned == 0;
    }
}
