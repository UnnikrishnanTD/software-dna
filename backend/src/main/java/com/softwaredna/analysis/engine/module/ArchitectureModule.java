package com.softwaredna.analysis.engine.module;

import com.softwaredna.analysis.engine.AnalysisContext;
import com.softwaredna.analysis.engine.AnalysisModule;
import com.softwaredna.analysis.engine.DimensionResult;
import com.softwaredna.analysis.engine.Measurement;
import com.softwaredna.analysis.engine.ScoreCard;
import com.softwaredna.architecture.ArchitectureGraph;
import com.softwaredna.common.domain.ArchitectureLayer;
import com.softwaredna.common.domain.DnaDimension;
import com.softwaredna.common.util.Statistics;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Scores how the system is put together.
 *
 * <p><b>Formula.</b> Starting from 100:
 * <ul>
 *   <li><b>Circular dependencies</b> (up to −25). Each cycle between modules
 *       costs 8 points. A cycle is the clearest structural defect a static
 *       analysis can prove, so it carries the heaviest single penalty.</li>
 *   <li><b>Coupling</b> (up to −20). Driven by the 90th-percentile fan-out:
 *       no penalty at 4 or below, full penalty at 15. The tail matters more
 *       than the average, because a handful of hubs is what makes a system
 *       hard to change.</li>
 *   <li><b>Layering violations</b> (up to −20). Edges that point from a lower
 *       layer back up to a higher one — infrastructure importing presentation,
 *       for instance — as a share of all edges.</li>
 *   <li><b>Hub concentration</b> (up to −15). Share of modules whose fan-in
 *       exceeds a quarter of all modules.</li>
 *   <li><b>Module granularity</b> (up to −10). Penalises a repository that is
 *       effectively one module, where no structure can be observed.</li>
 * </ul>
 */
@Component
public class ArchitectureModule implements AnalysisModule {

    /** Layer order for detecting an upward, and therefore inverted, dependency. */
    private static final Map<ArchitectureLayer, Integer> LAYER_RANK = Map.of(
            ArchitectureLayer.PRESENTATION, 0,
            ArchitectureLayer.APPLICATION, 1,
            ArchitectureLayer.DOMAIN, 2,
            ArchitectureLayer.INFRASTRUCTURE, 3,
            ArchitectureLayer.EXTERNAL, 4);

    @Override
    public DnaDimension dimension() {
        return DnaDimension.ARCHITECTURE;
    }

    @Override
    public double weight() {
        return 0.18;
    }

    @Override
    public DimensionResult analyse(AnalysisContext context) {
        ArchitectureGraph graph = context.graph();
        ScoreCard card = new ScoreCard();

        if (graph.nodes().isEmpty()) {
            return DimensionResult.unavailable(dimension(),
                    "No analysable source structure was found, so architecture could not be assessed.",
                    List.of());
        }

        int moduleCount = graph.nodes().size();
        int edgeCount = graph.edges().size();
        int cycleCount = graph.cycles().size();

        card.measure(Measurement.of("architecture.modules", moduleCount))
                .measure(Measurement.of("architecture.edges", edgeCount))
                .measure(Measurement.of("architecture.cycles", cycleCount));

        // --- Circular dependencies ---
        card.penalise(cycleCount * 8.0, 25,
                cycleCount == 0 ? null : "%d circular %s between modules"
                        .formatted(cycleCount, cycleCount == 1 ? "dependency" : "dependencies"));
        if (cycleCount == 0) {
            card.commend("No circular dependencies between modules");
        }

        // --- Coupling, measured at the tail rather than the average ---
        int[] fanOut = Statistics.toIntArray(graph.nodes(), ArchitectureGraph.Node::fanOut);
        double p90FanOut = Statistics.percentile(fanOut, 90);
        double medianFanOut = Statistics.median(fanOut);
        card.measure(Measurement.of("architecture.fanOut.p90", p90FanOut))
                .measure(Measurement.of("architecture.fanOut.median", medianFanOut));

        card.penalise(ScoreCard.ramp(p90FanOut, 4, 15, 20), 20,
                p90FanOut <= 4 ? null
                        : "The most connected modules depend on %d others"
                                .formatted((int) p90FanOut));
        if (medianFanOut <= 3 && moduleCount > 3) {
            card.commend("Median module depends on %d others".formatted((int) medianFanOut));
        }

        // --- Layering violations ---
        long inverted = graph.edges().stream()
                .filter(edge -> isInverted(edge, graph))
                .count();
        double invertedShare = edgeCount == 0 ? 0 : (inverted * 100.0) / edgeCount;
        card.measure(Measurement.of("architecture.invertedEdges", inverted))
                .measure(Measurement.of("architecture.invertedEdgeShare", invertedShare, "%"));

        card.penalise(ScoreCard.ramp(invertedShare, 2, 25, 20), 20,
                inverted == 0 ? null
                        : "%d %s point against the layer flow".formatted(
                                inverted, inverted == 1 ? "dependency" : "dependencies"));
        if (inverted == 0 && edgeCount > 0) {
            card.commend("Every dependency respects the layer ordering");
        }

        // --- Hub concentration ---
        int hubThreshold = Math.max(3, moduleCount / 4);
        long hubs = graph.nodes().stream()
                .filter(node -> node.fanIn() >= hubThreshold)
                .count();
        card.measure(Measurement.of("architecture.hubs", hubs));
        card.penalise(hubs * 5.0, 15,
                hubs == 0 ? null
                        : "%d %s imported by a quarter of the codebase".formatted(
                                hubs, hubs == 1 ? "module is" : "modules are"));

        // --- Granularity ---
        if (moduleCount < 3) {
            card.penalise(10, 10,
                    "The repository has too few modules for structure to be assessed");
            card.reduceConfidence(30, "too-few-modules");
        }

        Set<ArchitectureLayer> layers = graph.nodes().stream()
                .map(ArchitectureGraph.Node::layer)
                .collect(java.util.stream.Collectors.toSet());
        card.measure(Measurement.of("architecture.layers", layers.size()));
        if (layers.size() >= 3) {
            card.commend("Code is separated across %d architectural layers"
                    .formatted(layers.size()));
        }

        if (graph.aggregated()) {
            card.reduceConfidence(10, "graph-aggregated");
        }

        double score = card.score();
        return new DimensionResult(dimension(), score, card.confidence(),
                headline(score, cycleCount, (int) p90FanOut),
                summary(moduleCount, edgeCount, cycleCount, layers.size(), inverted),
                card.strengths(), card.watchItems(), card.measurements(), card.evidence());
    }

    private boolean isInverted(ArchitectureGraph.Edge edge, ArchitectureGraph graph) {
        Integer sourceRank = rankOf(edge.source(), graph);
        Integer targetRank = rankOf(edge.target(), graph);
        return sourceRank != null && targetRank != null && targetRank < sourceRank;
    }

    private Integer rankOf(String nodeKey, ArchitectureGraph graph) {
        return graph.nodes().stream()
                .filter(node -> node.key().equals(nodeKey))
                .findFirst()
                .map(node -> LAYER_RANK.get(node.layer()))
                .orElse(null);
    }

    private String headline(double score, int cycles, int p90FanOut) {
        if (score >= 85 && cycles == 0) {
            return "Clean module boundaries with no circular dependencies.";
        }
        if (cycles > 0) {
            return "Structure is sound apart from %d circular %s.".formatted(
                    cycles, cycles == 1 ? "dependency" : "dependencies");
        }
        if (p90FanOut > 10) {
            return "Layering is respected, but a few modules are heavily coupled.";
        }
        return "Module structure is workable with room to tighten coupling.";
    }

    private String summary(int modules, int edges, int cycles, int layers, long inverted) {
        return ("The repository resolves into %d modules connected by %d import relationships "
                + "across %d %s. %s %s")
                .formatted(modules, edges, layers, layers == 1 ? "layer" : "layers",
                        cycles == 0
                                ? "No dependency cycles were found."
                                : "%d dependency %s were detected between modules."
                                        .formatted(cycles, cycles == 1 ? "cycle" : "cycles"),
                        inverted == 0
                                ? "Every dependency points down the layer stack."
                                : "%d dependencies point back up the layer stack."
                                        .formatted(inverted));
    }
}
