package com.softwaredna.codebase.model;

import java.util.List;
import java.util.Map;

/**
 * One analysed file: what it is, what it contains, and what it imports.
 *
 * <p>{@code imports} holds raw, unresolved specifiers exactly as written in
 * the source. Resolving them to other files in the repository is the
 * architecture builder's job, not the scanner's.
 *
 * <p>{@code signals} holds named lexical detections recorded during the same
 * read, so the security, performance and documentation modules never have to
 * open the file a second time.
 */
public record ScannedFile(
        String path,
        String name,
        String extension,
        Language language,
        boolean test,
        boolean generated,
        long sizeBytes,
        SourceMetrics metrics,
        List<String> imports,
        List<String> declaredTypes,
        Map<String, Integer> signals
) {

    public int signal(String key) {
        return signals.getOrDefault(key, 0);
    }

    public boolean isProgramming() {
        return language.isProgramming();
    }
}
