package com.softwaredna.codebase.analyzer;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.softwaredna.codebase.model.Language;
import com.softwaredna.codebase.model.SourceMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Analyses Java from a real abstract syntax tree.
 *
 * <p>Because JavaParser produces an actual parse rather than a text scan, the
 * counts here are exact: an {@code if} inside a string literal is not a branch,
 * a nested class is one declaration, and a lambda body contributes its own
 * decision points.
 *
 * <p>Cyclomatic complexity is the standard McCabe count — one, plus one for
 * every decision point. Boolean operators count because each short-circuit is a
 * distinct path through the method.
 *
 * <p>When a file will not parse — a newer syntax than the configured level, or
 * genuinely broken source — the analyzer falls back to line counting and marks
 * the result as non-structural rather than reporting misleading zeroes.
 */
@Component
public class JavaLanguageAnalyzer implements LanguageAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(JavaLanguageAnalyzer.class);

    private final JavaParser parser = new JavaParser(new ParserConfiguration()
            .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21)
            // The repository is not on our classpath and must never be
            // resolved or loaded; we only need its syntax.
            .setAttributeComments(false)
            .setStoreTokens(false));

    @Override
    public Set<Language> supportedLanguages() {
        return Set.of(Language.JAVA);
    }

    @Override
    public Result analyse(String source, String filePath) {
        SourceText text = SourceText.lexCFamily(source, false);

        ParseResult<CompilationUnit> parsed;
        try {
            synchronized (parser) {
                parsed = parser.parse(source);
            }
        } catch (RuntimeException e) {
            log.debug("Java parse threw for {}: {}", filePath, e.getMessage());
            return lineCountFallback(text);
        }

        if (!parsed.isSuccessful() || parsed.getResult().isEmpty()) {
            log.debug("Java parse failed for {}", filePath);
            return lineCountFallback(text);
        }

        CompilationUnit unit = parsed.getResult().get();

        List<String> imports = unit.getImports().stream()
                .map(imported -> imported.getNameAsString())
                .toList();

        List<String> declaredTypes = new ArrayList<>();
        unit.findAll(ClassOrInterfaceDeclaration.class)
                .forEach(type -> declaredTypes.add(type.getNameAsString()));
        unit.findAll(RecordDeclaration.class)
                .forEach(type -> declaredTypes.add(type.getNameAsString()));
        unit.findAll(EnumDeclaration.class)
                .forEach(type -> declaredTypes.add(type.getNameAsString()));

        int methods = unit.findAll(MethodDeclaration.class).size()
                + unit.findAll(ConstructorDeclaration.class).size();

        SourceMetrics metrics = new SourceMetrics(
                text.linesTotal(),
                text.linesOfCode(),
                text.linesComment(),
                text.linesBlank(),
                cyclomaticComplexity(unit),
                declaredTypes.size(),
                methods,
                imports.size(),
                maxNestingDepth(unit),
                true);

        return new Result(metrics, imports, declaredTypes);
    }

    private Result lineCountFallback(SourceText text) {
        return Result.of(SourceMetrics.linesOnly(
                text.linesTotal(), text.linesOfCode(), text.linesComment(), text.linesBlank()));
    }

    /**
     * McCabe complexity for the whole compilation unit.
     *
     * <p>Reported per file rather than per method because that is the unit the
     * product reasons about; the per-method maximum is available from the same
     * tree if it is needed later.
     */
    private int cyclomaticComplexity(CompilationUnit unit) {
        int decisions = 0;
        decisions += unit.findAll(IfStmt.class).size();
        decisions += unit.findAll(ForStmt.class).size();
        decisions += unit.findAll(ForEachStmt.class).size();
        decisions += unit.findAll(WhileStmt.class).size();
        decisions += unit.findAll(DoStmt.class).size();
        decisions += unit.findAll(CatchClause.class).size();
        decisions += unit.findAll(ConditionalExpr.class).size();

        // A switch entry with no labels is `default`, which adds no branch.
        decisions += unit.findAll(SwitchEntry.class).stream()
                .mapToInt(entry -> entry.getLabels().size())
                .sum();

        decisions += (int) unit.findAll(BinaryExpr.class).stream()
                .filter(expression -> expression.getOperator() == BinaryExpr.Operator.AND
                        || expression.getOperator() == BinaryExpr.Operator.OR)
                .count();

        return 1 + decisions;
    }

    /**
     * Deepest nesting of control-flow constructs within any method.
     *
     * <p>Counts control flow only; class and method braces are structure, not
     * complexity, and including them would penalise every file equally.
     */
    private int maxNestingDepth(CompilationUnit unit) {
        int deepest = 0;
        for (CallableDeclaration<?> callable : unit.findAll(CallableDeclaration.class)) {
            deepest = Math.max(deepest, depthOf(callable, 0));
        }
        return deepest;
    }

    private int depthOf(Node node, int current) {
        int deepest = current;
        for (Node child : node.getChildNodes()) {
            boolean nests = child instanceof IfStmt
                    || child instanceof ForStmt
                    || child instanceof ForEachStmt
                    || child instanceof WhileStmt
                    || child instanceof DoStmt
                    || child instanceof CatchClause;
            deepest = Math.max(deepest, depthOf(child, nests ? current + 1 : current));
        }
        return deepest;
    }
}
