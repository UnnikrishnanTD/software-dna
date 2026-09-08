package com.softwaredna.architecture;

import com.softwaredna.codebase.model.Language;
import com.softwaredna.codebase.model.ScannedFile;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves an import specifier to a file inside the repository.
 *
 * <p>Two ecosystems are handled, because they are the two the analyzers parse:
 *
 * <ul>
 *   <li><b>JavaScript and TypeScript.</b> Relative specifiers are resolved
 *       against the importing file, trying the extension list and index files
 *       the ecosystem itself tries. A specifier that resolves to nothing in the
 *       repository is external — that is how third-party packages are
 *       identified, rather than by guessing from the name.</li>
 *   <li><b>Java.</b> Imports are fully-qualified type names, so resolution is a
 *       lookup of the declared type against the set of types the repository
 *       declares. Anything not declared here is external.</li>
 * </ul>
 *
 * <p>No attempt is made to honour path aliases from {@code tsconfig.json} or
 * bundler configuration. Those imports resolve to nothing and are counted as
 * external, which understates internal coupling rather than inventing it.
 */
public final class ImportResolver {

    /** Extensions tried for an extensionless relative specifier, in order. */
    private static final List<String> JS_EXTENSIONS = List.of(
            ".ts", ".tsx", ".mts", ".cts", ".js", ".jsx", ".mjs", ".cjs", ".vue", ".json");

    private final Set<String> filePaths;
    /** Fully-qualified Java type name to the path that declares it. */
    private final Map<String, String> javaTypeToPath;

    public ImportResolver(List<ScannedFile> files) {
        this.filePaths = new HashSet<>(files.size());
        this.javaTypeToPath = new HashMap<>();

        for (ScannedFile file : files) {
            filePaths.add(file.path());
            if (file.language() == Language.JAVA) {
                indexJavaTypes(file);
            }
        }
    }

    private void indexJavaTypes(ScannedFile file) {
        String packageName = javaPackageOf(file.path());
        for (String type : file.declaredTypes()) {
            String qualified = packageName.isEmpty() ? type : packageName + "." + type;
            javaTypeToPath.putIfAbsent(qualified, file.path());
        }
    }

    /**
     * Derives a package name from the path, using the conventional source roots.
     * A file outside any recognised root contributes its directory path, which
     * still yields a stable key even if it is not a real package name.
     */
    private static String javaPackageOf(String path) {
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash < 0) {
            return "";
        }
        String directory = path.substring(0, lastSlash);
        for (String root : List.of("src/main/java/", "src/test/java/", "src/main/kotlin/",
                "src/test/kotlin/", "app/src/main/java/")) {
            int index = directory.indexOf(root);
            if (index >= 0) {
                return directory.substring(index + root.length()).replace('/', '.');
            }
        }
        return directory.replace('/', '.');
    }

    /**
     * Resolves one import.
     *
     * @return the repository path of the imported file, or empty when the
     *         import is external or unresolvable
     */
    public Optional<String> resolve(ScannedFile importingFile, String specifier) {
        if (importingFile.language() == Language.JAVA) {
            return resolveJava(specifier);
        }
        return resolveJavaScript(importingFile.path(), specifier);
    }

    private Optional<String> resolveJava(String specifier) {
        String direct = javaTypeToPath.get(specifier);
        if (direct != null) {
            return Optional.of(direct);
        }
        // A wildcard import names a package; attribute it to any type in it.
        if (specifier.endsWith(".*")) {
            String packagePrefix = specifier.substring(0, specifier.length() - 1);
            return javaTypeToPath.entrySet().stream()
                    .filter(entry -> entry.getKey().startsWith(packagePrefix))
                    .map(Map.Entry::getValue)
                    .findFirst();
        }
        // A static import names a member of a type: strip the trailing segment.
        int lastDot = specifier.lastIndexOf('.');
        if (lastDot > 0) {
            String enclosing = specifier.substring(0, lastDot);
            return Optional.ofNullable(javaTypeToPath.get(enclosing));
        }
        return Optional.empty();
    }

    private Optional<String> resolveJavaScript(String importingPath, String specifier) {
        // Bare specifiers are packages; only relative ones can be internal.
        if (!specifier.startsWith(".")) {
            return Optional.empty();
        }

        String directory = importingPath.contains("/")
                ? importingPath.substring(0, importingPath.lastIndexOf('/'))
                : "";
        String combined = normalise(directory.isEmpty()
                ? specifier
                : directory + "/" + specifier);
        if (combined == null) {
            return Optional.empty();
        }

        if (filePaths.contains(combined)) {
            return Optional.of(combined);
        }
        for (String extension : JS_EXTENSIONS) {
            String candidate = combined + extension;
            if (filePaths.contains(candidate)) {
                return Optional.of(candidate);
            }
        }
        for (String extension : JS_EXTENSIONS) {
            String candidate = combined + "/index" + extension;
            if (filePaths.contains(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    /**
     * Collapses {@code .} and {@code ..} segments.
     *
     * @return null when the path climbs above the repository root, which means
     *         the specifier does not name anything we can analyse
     */
    private static String normalise(String path) {
        java.util.Deque<String> stack = new java.util.ArrayDeque<>();
        for (String segment : path.split("/")) {
            if (segment.isEmpty() || segment.equals(".")) {
                continue;
            }
            if (segment.equals("..")) {
                if (stack.isEmpty()) {
                    return null;
                }
                stack.removeLast();
                continue;
            }
            stack.addLast(segment);
        }
        return String.join("/", stack);
    }

    /** True when the specifier names a third-party package rather than a path. */
    public static boolean isExternalPackage(String specifier) {
        return !specifier.startsWith(".") && !specifier.startsWith("/");
    }

    /**
     * The package name a bare specifier belongs to, e.g. {@code @angular/core}
     * from {@code @angular/core/testing}, or {@code lodash} from
     * {@code lodash/debounce}.
     */
    public static String packageNameOf(String specifier) {
        String cleaned = specifier.startsWith("node:")
                ? specifier.substring("node:".length())
                : specifier;
        String[] segments = cleaned.split("/");
        if (cleaned.startsWith("@") && segments.length >= 2) {
            return segments[0] + "/" + segments[1];
        }
        return segments[0];
    }
}
