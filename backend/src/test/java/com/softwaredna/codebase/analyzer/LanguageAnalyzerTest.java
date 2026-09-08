package com.softwaredna.codebase.analyzer;

import com.softwaredna.codebase.model.SourceMetrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * These analyzers produce the numbers every score is later derived from, so
 * the tests check exact values against hand-counted sources rather than
 * asserting that something merely ran.
 */
class LanguageAnalyzerTest {

    private final JavaLanguageAnalyzer java = new JavaLanguageAnalyzer();
    private final TypeScriptLanguageAnalyzer typescript = new TypeScriptLanguageAnalyzer();

    @Nested
    @DisplayName("Java, parsed from a real syntax tree")
    class Java {

        @Test
        void countsDecisionPointsExactly() {
            String source = """
                    package com.example;

                    class Router {
                        String route(int code, boolean secure) {
                            if (code == 1) {                 // +1
                                return "one";
                            } else if (code == 2 && secure) { // +1 if, +1 &&
                                return "two";
                            }
                            for (int i = 0; i < 3; i++) {    // +1
                                if (i == code) {             // +1
                                    return "loop";
                                }
                            }
                            return secure ? "yes" : "no";    // +1 ternary
                        }
                    }
                    """;

            SourceMetrics metrics = java.analyse(source, "Router.java").metrics();

            // 1 (base) + 6 decision points
            assertThat(metrics.cyclomaticComplexity()).isEqualTo(7);
            assertThat(metrics.structuralAnalysis()).isTrue();
        }

        @Test
        void ignoresKeywordsInsideCommentsAndStrings() {
            String source = """
                    package com.example;

                    class Quiet {
                        // if this counted, the complexity would be wrong
                        /* for while case catch && || */
                        String describe() {
                            return "if (a && b) { for (;;) {} }";
                        }
                    }
                    """;

            SourceMetrics metrics = java.analyse(source, "Quiet.java").metrics();

            assertThat(metrics.cyclomaticComplexity()).isEqualTo(1);
        }

        @Test
        void countsSwitchLabelsButNotDefault() {
            String source = """
                    class Switcher {
                        int pick(int value) {
                            switch (value) {
                                case 1: return 10;    // +1
                                case 2: return 20;    // +1
                                default: return 0;    // no branch
                            }
                        }
                    }
                    """;

            assertThat(java.analyse(source, "Switcher.java").metrics().cyclomaticComplexity())
                    .isEqualTo(3);
        }

        @Test
        void extractsImportsAndDeclaredTypes() {
            String source = """
                    package com.example;

                    import java.util.List;
                    import java.util.Map;
                    import com.example.other.Thing;

                    public class Outer {
                        private record Inner(String name) {}
                        interface Behaviour {}
                        enum Mode { A, B }
                    }
                    """;

            LanguageAnalyzer.Result result = java.analyse(source, "Outer.java");

            assertThat(result.imports())
                    .containsExactly("java.util.List", "java.util.Map",
                            "com.example.other.Thing");
            assertThat(result.declaredTypes())
                    .containsExactlyInAnyOrder("Outer", "Behaviour", "Inner", "Mode");
            assertThat(result.metrics().importCount()).isEqualTo(3);
        }

        @Test
        void countsMethodsAndConstructors() {
            String source = """
                    class Service {
                        Service() {}
                        Service(int x) {}
                        void a() {}
                        int b() { return 1; }
                    }
                    """;

            assertThat(java.analyse(source, "Service.java").metrics().functionCount())
                    .isEqualTo(4);
        }

        @Test
        void measuresControlFlowNestingRatherThanBraceDepth() {
            String source = """
                    class Deep {
                        void run(int n) {
                            if (n > 0) {
                                for (int i = 0; i < n; i++) {
                                    while (i > 2) {
                                        i--;
                                    }
                                }
                            }
                        }
                    }
                    """;

            assertThat(java.analyse(source, "Deep.java").metrics().maxNestingDepth())
                    .isEqualTo(3);
        }

        @Test
        void separatesCodeCommentAndBlankLines() {
            String source = """
                    package com.example;

                    // a comment
                    class Small {
                        /* block
                           comment */
                        int x = 1;
                    }
                    """;

            SourceMetrics metrics = java.analyse(source, "Small.java").metrics();

            assertThat(metrics.linesComment()).isEqualTo(3);
            assertThat(metrics.linesBlank()).isEqualTo(1);
            assertThat(metrics.linesTotal())
                    .isEqualTo(metrics.linesOfCode() + metrics.linesComment()
                            + metrics.linesBlank());
        }

        @Test
        void fallsBackToLineCountingWhenSourceWillNotParse() {
            String broken = "class Broken { this is not java at all ((( }";

            SourceMetrics metrics = java.analyse(broken, "Broken.java").metrics();

            // Marked non-structural so the scoring layer can tell a failed
            // parse apart from a genuinely simple file.
            assertThat(metrics.structuralAnalysis()).isFalse();
            assertThat(metrics.linesTotal()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("TypeScript, lexed with comments and strings masked")
    class TypeScript {

        @Test
        void countsDecisionPointsExactly() {
            String source = """
                    export function route(code: number, secure: boolean): string {
                      if (code === 1) {                    // +1
                        return 'one';
                      } else if (code === 2 && secure) {   // +1 if, +1 &&
                        return 'two';
                      }
                      for (let i = 0; i < 3; i++) {        // +1
                        while (i > 2) { i--; }             // +1
                      }
                      return secure ? 'yes' : 'no';        // +1
                    }
                    """;

            assertThat(typescript.analyse(source, "route.ts").metrics().cyclomaticComplexity())
                    .isEqualTo(7);
        }

        @Test
        void ignoresKeywordsInsideCommentsStringsAndTemplates() {
            String source = """
                    // if for while && || ? case
                    /* if (a && b) for (;;) {} */
                    const message = 'if (a && b) { for (;;) {} }';
                    const other = "case catch ?? ||";
                    const template = `if ${'nothing'} && while`;
                    export const value = 1;
                    """;

            assertThat(typescript.analyse(source, "quiet.ts").metrics().cyclomaticComplexity())
                    .isEqualTo(1);
        }

        @Test
        void doesNotCountOptionalChainingAsABranch() {
            String source = "const name = user?.profile?.name; export {};";

            assertThat(typescript.analyse(source, "chain.ts").metrics().cyclomaticComplexity())
                    .isEqualTo(1);
        }

        @Test
        void doesNotMistakeIdentifiersForKeywords() {
            String source = """
                    const iffy = 1;
                    const forward = 2;
                    const whiles = 3;
                    export { iffy, forward, whiles };
                    """;

            assertThat(typescript.analyse(source, "words.ts").metrics().cyclomaticComplexity())
                    .isEqualTo(1);
        }

        @Test
        void extractsEveryImportForm() {
            String source = """
                    import { Component } from '@angular/core';
                    import Default from './default';
                    import * as path from 'node:path';
                    import './side-effect.css';
                    export { thing } from './re-exported';
                    const legacy = require('lodash');
                    const lazy = await import('./lazy-module');
                    """;

            assertThat(typescript.analyse(source, "imports.ts").imports())
                    .containsExactlyInAnyOrder(
                            "@angular/core", "./default", "node:path",
                            "./side-effect.css", "./re-exported",
                            "lodash", "./lazy-module");
        }

        @Test
        void doesNotCountAnImportSpecifierTwice() {
            String source = """
                    import { a } from './shared';
                    import { b } from './shared';
                    """;

            assertThat(typescript.analyse(source, "dup.ts").imports())
                    .containsExactly("./shared");
        }

        @Test
        void extractsDeclaredTypes() {
            String source = """
                    export class UserService {}
                    export interface User { id: string }
                    export enum Role { Admin }
                    export type Handle = string;
                    """;

            assertThat(typescript.analyse(source, "types.ts").declaredTypes())
                    .containsExactlyInAnyOrder("UserService", "User", "Role", "Handle");
        }

        @Test
        void separatesCodeCommentAndBlankLines() {
            String source = """
                    // one
                    // two

                    export const value = 1;
                    """;

            SourceMetrics metrics = typescript.analyse(source, "lines.ts").metrics();

            assertThat(metrics.linesComment()).isEqualTo(2);
            assertThat(metrics.linesBlank()).isEqualTo(1);
            assertThat(metrics.linesOfCode()).isEqualTo(1);
        }

        @Test
        void reportsStructuralAnalysisBecauseCountsAreMeasured() {
            assertThat(typescript.analyse("export const a = 1;", "a.ts")
                    .metrics().structuralAnalysis()).isTrue();
        }
    }
}
