package com.softwaredna.architecture;

import com.softwaredna.codebase.model.Language;
import com.softwaredna.codebase.model.ScannedFile;
import com.softwaredna.common.domain.ArchitectureEdgeKind;
import com.softwaredna.common.domain.ArchitectureLayer;
import com.softwaredna.common.domain.ArchitectureNodeKind;
import com.softwaredna.common.domain.ComplexityBand;
import com.softwaredna.common.domain.RiskLevel;
import com.softwaredna.config.AnalysisLimits;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Builds the module dependency graph from scanned files and their imports.
 *
 * <p>Nodes are directories, not files. A file-level graph of a real repository
 * runs to thousands of nodes and communicates nothing; the module level is
 * where architectural coupling becomes visible and actionable. The grouping
 * depth is chosen automatically — the deepest level that still fits within the
 * configured node budget — so a small repository is shown in detail and a large
 * one is shown coarsely rather than truncated.
 *
 * <p>Edges are real import relationships resolved by {@link ImportResolver}.
 * An import that does not resolve to a file in the repository is external and
 * contributes no edge, so the graph never implies a dependency the source does
 * not state.
 */
@Component
public class ArchitectureGraphBuilder {

    private static final Logger log = LoggerFactory.getLogger(ArchitectureGraphBuilder.class);

    private static final int MAX_GROUPING_DEPTH = 6;

    /**
     * Directory prefixes that describe build layout rather than architecture.
     * Stripped before grouping so {@code src/main/java} does not consume the
     * whole depth budget, and so a class and its test land in the same module.
     */
    private static final List<String> SOURCE_ROOTS = List.of(
            "src/main/java/", "src/test/java/", "src/main/kotlin/", "src/test/kotlin/",
            "src/main/scala/", "src/test/scala/", "src/main/groovy/",
            "src/main/resources/", "src/test/resources/",
            "app/src/main/java/", "app/src/test/java/",
            "src/main/", "src/test/", "src/");

    private final AnalysisLimits limits;

    public ArchitectureGraphBuilder(AnalysisLimits limits) {
        this.limits = limits;
    }

    public ArchitectureGraph build(List<ScannedFile> files,
                                   Map<String, Integer> changesByPath) {
        List<ScannedFile> sources = files.stream()
                .filter(ScannedFile::isProgramming)
                .filter(file -> !file.generated())
                .toList();

        if (sources.isEmpty()) {
            return ArchitectureGraph.empty();
        }

        // Normalise paths before grouping: strip build-layout roots, then strip
        // whatever directory prefix every file shares. A prefix common to the
        // whole repository distinguishes nothing, and leaving it in place is
        // what collapses a deep package structure into a single module.
        Map<String, String> normalisedPaths = normalisePaths(sources);

        int depth = chooseGroupingDepth(sources, normalisedPaths);
        boolean aggregated = depth < deepestDepth(normalisedPaths.values());

        Map<String, List<ScannedFile>> byModule = new TreeMap<>();
        for (ScannedFile file : sources) {
            byModule.computeIfAbsent(
                            moduleKey(normalisedPaths.get(file.path()), depth),
                            key -> new ArrayList<>())
                    .add(file);
        }

        ImportResolver resolver = new ImportResolver(sources);
        Map<String, String> moduleOfFile = new HashMap<>();
        byModule.forEach((module, moduleFiles) ->
                moduleFiles.forEach(file -> moduleOfFile.put(file.path(), module)));

        // Edge weight is how many imports cross the module boundary.
        Map<ArchitectureGraph.ModulePair, Integer> edgeWeights = new LinkedHashMap<>();
        for (ScannedFile file : sources) {
            String sourceModule = moduleOfFile.get(file.path());
            for (String specifier : file.imports()) {
                Optional<String> target = resolver.resolve(file, specifier);
                if (target.isEmpty()) {
                    continue;   // External package: not an internal edge.
                }
                String targetModule = moduleOfFile.get(target.get());
                if (targetModule == null || targetModule.equals(sourceModule)) {
                    continue;   // Within a module is cohesion, not coupling.
                }
                edgeWeights.merge(
                        new ArchitectureGraph.ModulePair(sourceModule, targetModule),
                        1, Integer::sum);
            }
        }

        Map<String, Integer> fanOut = new HashMap<>();
        Map<String, Integer> fanIn = new HashMap<>();
        List<ArchitectureGraph.Edge> edges = new ArrayList<>(edgeWeights.size());
        edgeWeights.forEach((pair, weight) -> {
            edges.add(new ArchitectureGraph.Edge(pair.source(), pair.target(),
                    ArchitectureEdgeKind.IMPORTS, Math.min(weight, 10)));
            fanOut.merge(pair.source(), 1, Integer::sum);
            fanIn.merge(pair.target(), 1, Integer::sum);
        });

        List<ArchitectureGraph.Node> nodes = new ArrayList<>(byModule.size());
        Map<String, List<String>> filesByNode = new LinkedHashMap<>();

        byModule.forEach((module, moduleFiles) -> {
            nodes.add(toNode(module, moduleFiles,
                    fanOut.getOrDefault(module, 0),
                    fanIn.getOrDefault(module, 0),
                    changesByPath));
            filesByNode.put(module, moduleFiles.stream().map(ScannedFile::path).toList());
        });

        List<List<String>> cycles = findCycles(nodes, edges);

        log.info("Architecture graph: {} modules, {} edges, {} cycles (grouping depth {})",
                nodes.size(), edges.size(), cycles.size(), depth);

        return new ArchitectureGraph(List.copyOf(nodes), List.copyOf(edges),
                cycles, Map.copyOf(filesByNode), aggregated);
    }

    private ArchitectureGraph.Node toNode(String module, List<ScannedFile> files,
                                          int fanOut, int fanIn,
                                          Map<String, Integer> changesByPath) {
        int linesOfCode = files.stream().mapToInt(file -> file.metrics().linesOfCode()).sum();

        // Mean complexity per file: the band thresholds are calibrated for a
        // single file, so summing would push every module to "very high".
        List<ScannedFile> analysed = files.stream()
                .filter(file -> file.metrics().structuralAnalysis())
                .toList();
        int complexity = analysed.isEmpty() ? 0
                : (int) Math.round(analysed.stream()
                        .mapToInt(file -> file.metrics().cyclomaticComplexity())
                        .average().orElse(0));

        int changes = files.stream()
                .mapToInt(file -> changesByPath.getOrDefault(file.path(), 0))
                .sum();

        Language dominant = dominantLanguage(files);
        ArchitectureLayer layer = ArchitectureClassifier.layerFor(module);
        ArchitectureNodeKind kind = ArchitectureClassifier.kindFor(module, files);

        return new ArchitectureGraph.Node(
                module,
                ArchitectureClassifier.displayName(module),
                kind,
                layer,
                module,
                dominant.displayName(),
                complexity,
                ComplexityBand.forScore(complexity),
                linesOfCode,
                files.size(),
                riskOf(complexity, fanOut, changes, files.size()),
                fanIn,
                fanOut,
                describe(files.size(), linesOfCode, fanOut, fanIn));
    }

    /**
     * Risk from three measured signals: how tangled the module is, how much of
     * the system it reaches, and how often it changes.
     *
     * <p>Test coverage is deliberately absent. It cannot be measured by static
     * analysis, and substituting a default would make every module look either
     * better or worse than the evidence supports.
     */
    private RiskLevel riskOf(int complexity, int fanOut, int changes, int fileCount) {
        double complexityPressure = Math.min(complexity / 30.0, 1.0);
        double couplingPressure = Math.min(fanOut / 12.0, 1.0);
        double churnPressure = fileCount == 0 ? 0
                : Math.min((changes / (double) fileCount) / 40.0, 1.0);

        double score = complexityPressure * 0.45
                + couplingPressure * 0.35
                + churnPressure * 0.20;

        return RiskLevel.forScore(score * 100);
    }

    private String describe(int fileCount, int linesOfCode, int fanOut, int fanIn) {
        return "%d %s, %,d lines. Imports %d %s, imported by %d.".formatted(
                fileCount, fileCount == 1 ? "file" : "files",
                linesOfCode,
                fanOut, fanOut == 1 ? "module" : "modules",
                fanIn);
    }

    private Language dominantLanguage(List<ScannedFile> files) {
        Map<Language, Integer> lines = new HashMap<>();
        for (ScannedFile file : files) {
            lines.merge(file.language(), file.metrics().linesOfCode(), Integer::sum);
        }
        return lines.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(Language.OTHER);
    }

    /**
     * The deepest grouping that still fits the node budget.
     *
     * <p>Deeper means more, smaller modules. Starting deep and backing off
     * yields the most detailed graph a repository can support, rather than a
     * fixed depth that is too coarse for small projects and too fine for large
     * ones.
     */
    private int chooseGroupingDepth(List<ScannedFile> files,
                                    Map<String, String> normalisedPaths) {
        int deepest = Math.min(deepestDepth(normalisedPaths.values()), MAX_GROUPING_DEPTH);
        for (int depth = deepest; depth > 1; depth--) {
            Set<String> modules = new HashSet<>();
            for (ScannedFile file : files) {
                modules.add(moduleKey(normalisedPaths.get(file.path()), depth));
            }
            if (modules.size() <= limits.maxGraphNodes()) {
                return depth;
            }
        }
        return 1;
    }

    private int deepestDepth(java.util.Collection<String> paths) {
        int deepest = 1;
        for (String path : paths) {
            int slashes = (int) path.chars().filter(character -> character == '/').count();
            deepest = Math.max(deepest, slashes);
        }
        return Math.max(deepest, 1);
    }

    /** Original path to the path used for grouping. */
    private Map<String, String> normalisePaths(List<ScannedFile> files) {
        Map<String, String> stripped = new HashMap<>(files.size());
        for (ScannedFile file : files) {
            stripped.put(file.path(), stripSourceRoot(file.path()));
        }

        String commonPrefix = longestCommonDirectoryPrefix(stripped.values());
        if (commonPrefix.isEmpty()) {
            return stripped;
        }

        Map<String, String> normalised = new HashMap<>(files.size());
        int cut = commonPrefix.length() + 1;
        stripped.forEach((original, path) -> normalised.put(original,
                path.length() > cut ? path.substring(cut) : path));
        return normalised;
    }

    private String stripSourceRoot(String path) {
        for (String root : SOURCE_ROOTS) {
            if (path.startsWith(root)) {
                return path.substring(root.length());
            }
        }
        return path;
    }

    /**
     * The deepest directory path shared by every file, never including the
     * final segment so at least one level of grouping always survives.
     */
    private String longestCommonDirectoryPrefix(java.util.Collection<String> paths) {
        String[] prefix = null;
        for (String path : paths) {
            int lastSlash = path.lastIndexOf('/');
            String[] directories = lastSlash < 0
                    ? new String[0]
                    : path.substring(0, lastSlash).split("/");

            if (prefix == null) {
                prefix = directories;
                continue;
            }
            int shared = 0;
            while (shared < prefix.length && shared < directories.length
                    && prefix[shared].equals(directories[shared])) {
                shared++;
            }
            prefix = Arrays.copyOfRange(prefix, 0, shared);
            if (prefix.length == 0) {
                return "";
            }
        }
        return prefix == null ? "" : String.join("/", prefix);
    }

    /** The first {@code depth} directory segments of a file path. */
    private String moduleKey(String path, int depth) {
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash < 0) {
            return "(root)";
        }
        String[] segments = path.substring(0, lastSlash).split("/");
        int take = Math.min(depth, segments.length);
        return String.join("/", Arrays.copyOfRange(segments, 0, take));
    }

    /**
     * Finds dependency cycles between modules using Tarjan's algorithm.
     *
     * <p>Iterative rather than recursive: a deep graph would otherwise risk a
     * stack overflow on exactly the pathological repositories most worth
     * analysing.
     */
    private List<List<String>> findCycles(List<ArchitectureGraph.Node> nodes,
                                          List<ArchitectureGraph.Edge> edges) {
        Map<String, List<String>> adjacency = new HashMap<>();
        for (ArchitectureGraph.Node node : nodes) {
            adjacency.put(node.key(), new ArrayList<>());
        }
        for (ArchitectureGraph.Edge edge : edges) {
            adjacency.computeIfAbsent(edge.source(), key -> new ArrayList<>())
                    .add(edge.target());
        }

        TarjanState state = new TarjanState();
        for (ArchitectureGraph.Node node : nodes) {
            if (!state.index.containsKey(node.key())) {
                strongConnect(node.key(), adjacency, state);
            }
        }

        // Only components with more than one member are cycles between modules;
        // a self-edge was already filtered out as intra-module cohesion.
        state.cycles.sort(Comparator.comparingInt(List::size));
        return List.copyOf(state.cycles);
    }

    private static final class TarjanState {
        final Map<String, Integer> index = new HashMap<>();
        final Map<String, Integer> lowLink = new HashMap<>();
        final Set<String> onStack = new HashSet<>();
        final Deque<String> stack = new ArrayDeque<>();
        final List<List<String>> cycles = new ArrayList<>();
        int counter;
    }

    private record Frame(String node, Iterator<String> neighbours) {
    }

    private void strongConnect(String start, Map<String, List<String>> adjacency,
                               TarjanState state) {
        Deque<Frame> frames = new ArrayDeque<>();
        push(start, adjacency, state, frames);

        while (!frames.isEmpty()) {
            Frame frame = frames.peek();
            if (frame.neighbours().hasNext()) {
                String neighbour = frame.neighbours().next();
                if (!state.index.containsKey(neighbour)) {
                    push(neighbour, adjacency, state, frames);
                } else if (state.onStack.contains(neighbour)) {
                    state.lowLink.put(frame.node(), Math.min(
                            state.lowLink.get(frame.node()), state.index.get(neighbour)));
                }
                continue;
            }

            frames.pop();
            if (!frames.isEmpty()) {
                String parent = frames.peek().node();
                state.lowLink.put(parent, Math.min(
                        state.lowLink.get(parent), state.lowLink.get(frame.node())));
            }

            if (state.lowLink.get(frame.node()).equals(state.index.get(frame.node()))) {
                List<String> component = new ArrayList<>();
                String member;
                do {
                    member = state.stack.pop();
                    state.onStack.remove(member);
                    component.add(member);
                } while (!member.equals(frame.node()));

                if (component.size() > 1) {
                    state.cycles.add(List.copyOf(component));
                }
            }
        }
    }

    private void push(String node, Map<String, List<String>> adjacency,
                      TarjanState state, Deque<Frame> frames) {
        state.index.put(node, state.counter);
        state.lowLink.put(node, state.counter);
        state.counter++;
        state.stack.push(node);
        state.onStack.add(node);
        frames.push(new Frame(node, adjacency.getOrDefault(node, List.of()).iterator()));
    }
}
