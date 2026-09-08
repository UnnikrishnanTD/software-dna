package com.softwaredna.architecture;

import com.softwaredna.common.domain.ArchitectureEdgeKind;
import com.softwaredna.common.domain.ArchitectureLayer;
import com.softwaredna.common.domain.ArchitectureNodeKind;
import com.softwaredna.common.domain.ComplexityBand;
import com.softwaredna.common.domain.RiskLevel;

import java.util.List;
import java.util.Map;

/** The dependency graph of a repository, aggregated to module level. */
public record ArchitectureGraph(
        List<Node> nodes,
        List<Edge> edges,
        List<List<String>> cycles,
        /** Files assigned to each node key, for cross-linking the codebase view. */
        Map<String, List<String>> filesByNode,
        /** True when modules were merged to stay within the node budget. */
        boolean aggregated
) {

    public record Node(
            String key,
            String name,
            ArchitectureNodeKind kind,
            ArchitectureLayer layer,
            String path,
            String language,
            int complexityScore,
            ComplexityBand complexityBand,
            int linesOfCode,
            int fileCount,
            RiskLevel risk,
            int fanIn,
            int fanOut,
            String description
    ) {
    }

    public record Edge(
            String source,
            String target,
            ArchitectureEdgeKind kind,
            int weight
    ) {
    }

    /** A directed pair, used as a map key while counting edge weights. */
    record ModulePair(String source, String target) {
    }

    public static ArchitectureGraph empty() {
        return new ArchitectureGraph(List.of(), List.of(), List.of(), Map.of(), false);
    }
}
