package com.softwaredna.codebase.model;

/**
 * Structural measurements for one file.
 *
 * <p>Every field is counted from the file's own text. Nothing here is
 * estimated: when a language has no analyzer capable of a measurement, the
 * value is zero and {@link #structuralAnalysis} is false, so the scoring layer
 * can tell "no decision points" apart from "not analysed".
 */
public record SourceMetrics(
        int linesTotal,
        int linesOfCode,
        int linesComment,
        int linesBlank,
        int cyclomaticComplexity,
        int declarationCount,
        int functionCount,
        int importCount,
        int maxNestingDepth,
        boolean structuralAnalysis
) {

    public static SourceMetrics linesOnly(int total, int code, int comment, int blank) {
        return new SourceMetrics(total, code, comment, blank, 0, 0, 0, 0, 0, false);
    }

    /** Comment lines as a share of non-blank lines, 0-100. */
    public double commentDensity() {
        int meaningful = linesOfCode + linesComment;
        return meaningful == 0 ? 0 : (linesComment * 100.0) / meaningful;
    }
}
