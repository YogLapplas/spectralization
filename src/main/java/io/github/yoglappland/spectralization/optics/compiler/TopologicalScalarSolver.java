package io.github.yoglappland.spectralization.optics.compiler;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class TopologicalScalarSolver {
    public static ScalarPowerSolution solve(
            CompiledPortGraph graph,
            Map<PortGraphNode, Integer> nodeIds,
            double[] source,
            ScalarSolverPlan solverPlan
    ) {
        int nodeCount = graph.nodes().size();
        double[] powers = new double[nodeCount];
        int[] inDegrees = new int[nodeCount];
        List<List<PortGraphEdge>> outgoingEdges = new ArrayList<>(nodeCount);

        System.arraycopy(source, 0, powers, 0, source.length);

        for (int index = 0; index < nodeCount; index++) {
            outgoingEdges.add(new ArrayList<>());
        }

        for (PortGraphEdge edge : graph.edges()) {
            Integer fromId = nodeIds.get(edge.from());
            Integer toId = nodeIds.get(edge.to());

            if (fromId == null || toId == null) {
                continue;
            }

            outgoingEdges.get(fromId).add(edge);
            inDegrees[toId]++;
        }

        ArrayDeque<Integer> pendingNodes = new ArrayDeque<>();

        for (int index = 0; index < nodeCount; index++) {
            if (inDegrees[index] == 0) {
                pendingNodes.addLast(index);
            }
        }

        int visitedNodes = 0;

        while (!pendingNodes.isEmpty()) {
            int fromId = pendingNodes.removeFirst();
            visitedNodes++;

            for (PortGraphEdge edge : outgoingEdges.get(fromId)) {
                Integer toId = nodeIds.get(edge.to());

                if (toId == null) {
                    continue;
                }

                powers[toId] += powers[fromId] * edge.sampleGain();
                inDegrees[toId]--;

                if (inDegrees[toId] == 0) {
                    pendingNodes.addLast(toId);
                }
            }
        }

        if (visitedNodes != nodeCount) {
            return ScalarPowerSolutions.fromGraphPowers(
                    ScalarSolverKind.TOPOLOGICAL_DAG,
                    solverPlan,
                    false,
                    false,
                    1,
                    0.0,
                    graph,
                    nodeIds,
                    source,
                    powers
            );
        }

        return ScalarPowerSolutions.fromGraphPowers(
                ScalarSolverKind.TOPOLOGICAL_DAG,
                solverPlan,
                true,
                false,
                1,
                0.0,
                graph,
                nodeIds,
                source,
                powers
        );
    }

    private TopologicalScalarSolver() {
    }
}
