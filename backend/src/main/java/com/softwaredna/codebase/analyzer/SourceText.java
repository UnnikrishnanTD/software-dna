package com.softwaredna.codebase.analyzer;

/**
 * Splits source text into code, comments and blank lines, and produces a copy
 * with comments and string literals blanked out.
 *
 * <p>This matters more than it looks. Counting decision points on raw text
 * gives wrong answers the moment a file contains {@code // handle if x && y}
 * or {@code "a || b"}. Every C-family analyzer here works on the masked text,
 * so a keyword inside a comment or a string can never be mistaken for control
 * flow.
 *
 * <p>The masking preserves line structure exactly — every newline survives —
 * so counts computed from the masked text still refer to the original file.
 */
public final class SourceText {

    private final String masked;
    private final String withoutComments;
    private final int linesTotal;
    private final int linesCode;
    private final int linesComment;
    private final int linesBlank;

    private SourceText(String masked, String withoutComments, int linesTotal,
                       int linesCode, int linesComment, int linesBlank) {
        this.masked = masked;
        this.withoutComments = withoutComments;
        this.linesTotal = linesTotal;
        this.linesCode = linesCode;
        this.linesComment = linesComment;
        this.linesBlank = linesBlank;
    }

    /**
     * Source with comments <em>and</em> string contents replaced by spaces.
     * Use this for anything that counts keywords or operators.
     */
    public String masked() {
        return masked;
    }

    /**
     * Source with comments removed but string literals intact.
     *
     * <p>Needed because an import specifier <em>is</em> a string literal:
     * masking it would erase the very thing being extracted. Keyword counting
     * must never use this view.
     */
    public String withoutComments() {
        return withoutComments;
    }

    public int linesTotal() {
        return linesTotal;
    }

    public int linesOfCode() {
        return linesCode;
    }

    public int linesComment() {
        return linesComment;
    }

    public int linesBlank() {
        return linesBlank;
    }

    private enum State { CODE, LINE_COMMENT, BLOCK_COMMENT, STRING, CHAR, TEMPLATE }

    /**
     * Lexes C-family source: Java, TypeScript, JavaScript, Go, C#, Rust, Swift.
     *
     * @param source        the file contents
     * @param templateQuote true when backticks start a template literal
     *                      (JavaScript and TypeScript); false elsewhere
     */
    public static SourceText lexCFamily(String source, boolean templateQuote) {
        StringBuilder out = new StringBuilder(source.length());
        StringBuilder literals = new StringBuilder(source.length());
        State state = State.CODE;
        boolean escaped = false;

        // Per-line flags, resolved when the newline is reached.
        boolean lineHasCode = false;
        boolean lineHasComment = false;

        int total = 0;
        int code = 0;
        int comment = 0;
        int blank = 0;

        int i = 0;
        int length = source.length();

        while (i < length) {
            char c = source.charAt(i);
            char next = i + 1 < length ? source.charAt(i + 1) : '\0';

            if (c == '\n') {
                total++;
                if (lineHasCode) {
                    code++;
                } else if (lineHasComment) {
                    comment++;
                } else {
                    blank++;
                }
                lineHasCode = false;
                lineHasComment = false;
                out.append('\n');
                literals.append('\n');
                if (state == State.LINE_COMMENT) {
                    state = State.CODE;
                }
                escaped = false;
                i++;
                continue;
            }

            switch (state) {
                case CODE -> {
                    if (c == '/' && next == '/') {
                        state = State.LINE_COMMENT;
                        if (!lineHasCode) {
                            lineHasComment = true;
                        }
                        out.append("  ");
                        literals.append("  ");
                        i += 2;
                        continue;
                    }
                    if (c == '/' && next == '*') {
                        state = State.BLOCK_COMMENT;
                        if (!lineHasCode) {
                            lineHasComment = true;
                        }
                        out.append("  ");
                        literals.append("  ");
                        i += 2;
                        continue;
                    }
                    if (c == '"') {
                        state = State.STRING;
                        lineHasCode = true;
                        out.append('"');
                        literals.append('"');
                        i++;
                        continue;
                    }
                    if (c == '\'') {
                        state = State.CHAR;
                        lineHasCode = true;
                        out.append('\'');
                        literals.append('\'');
                        i++;
                        continue;
                    }
                    if (templateQuote && c == '`') {
                        state = State.TEMPLATE;
                        lineHasCode = true;
                        out.append('`');
                        literals.append('`');
                        i++;
                        continue;
                    }
                    if (!Character.isWhitespace(c)) {
                        lineHasCode = true;
                    }
                    out.append(c);
                    literals.append(c);
                    i++;
                }
                case LINE_COMMENT -> {
                    out.append(' ');
                    literals.append(' ');
                    i++;
                }
                case BLOCK_COMMENT -> {
                    if (!lineHasCode) {
                        lineHasComment = true;
                    }
                    if (c == '*' && next == '/') {
                        state = State.CODE;
                        out.append("  ");
                        literals.append("  ");
                        i += 2;
                        continue;
                    }
                    out.append(' ');
                    literals.append(' ');
                    i++;
                }
                case STRING, CHAR, TEMPLATE -> {
                    lineHasCode = true;
                    if (escaped) {
                        escaped = false;
                        out.append(' ');
                        literals.append(c);
                        i++;
                        continue;
                    }
                    if (c == '\\') {
                        escaped = true;
                        out.append(' ');
                        literals.append(c);
                        i++;
                        continue;
                    }
                    char terminator = switch (state) {
                        case STRING -> '"';
                        case CHAR -> '\'';
                        default -> '`';
                    };
                    if (c == terminator) {
                        state = State.CODE;
                        out.append(terminator);
                        literals.append(terminator);
                        i++;
                        continue;
                    }
                    out.append(' ');
                    literals.append(c);
                    i++;
                }
            }
        }

        // The final line, when the file does not end with a newline.
        if (length > 0 && source.charAt(length - 1) != '\n') {
            total++;
            if (lineHasCode) {
                code++;
            } else if (lineHasComment) {
                comment++;
            } else {
                blank++;
            }
        }

        return new SourceText(out.toString(), literals.toString(),
                total, code, comment, blank);
    }

    /** Lexes hash-comment languages: Python, Ruby, Shell, YAML. */
    public static SourceText lexHashComments(String source) {
        StringBuilder out = new StringBuilder(source.length());
        int total = 0;
        int code = 0;
        int comment = 0;
        int blank = 0;

        String[] lines = splitLines(source);
        for (String line : lines) {
            total++;
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                blank++;
                out.append('\n');
                continue;
            }
            if (trimmed.startsWith("#")) {
                comment++;
                out.append('\n');
                continue;
            }
            code++;
            int hash = line.indexOf('#');
            out.append(hash < 0 ? line : line.substring(0, hash)).append('\n');
        }

        return new SourceText(out.toString(), out.toString(),
                total, code, comment, blank);
    }

    /** Counts lines only, for formats with no comment syntax we model. */
    public static SourceText countLines(String source) {
        int total = 0;
        int code = 0;
        int blank = 0;
        for (String line : splitLines(source)) {
            total++;
            if (line.isBlank()) {
                blank++;
            } else {
                code++;
            }
        }
        return new SourceText(source, source, total, code, 0, blank);
    }

    /**
     * Splits into lines without inventing a trailing empty line for a file
     * that simply ends with a newline.
     */
    private static String[] splitLines(String source) {
        if (source.isEmpty()) {
            return new String[0];
        }
        String trimmed = source.endsWith("\n")
                ? source.substring(0, source.length() - 1)
                : source;
        return trimmed.split("\n", -1);
    }
}
