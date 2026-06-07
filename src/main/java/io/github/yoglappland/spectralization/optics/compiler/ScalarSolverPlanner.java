package io.github.yoglappland.spectralization.optics.compiler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ScalarSolverPlanner {
    private static final int SMALL_EXACT_FEEDBACK_BETA1 = 8;
    private static final int SMALL_EXACT_FEEDBACK_NODES = 64;

    public static ScalarSolverPlan plan(CompiledPortGraph graph) {
        if (graph.nodes().isEmpty()) {
            return ScalarSolverPlan.empty();
        }

        if (graph.feedbackSccCount() == 0 && graph.beta1() == 0) {
            return acyclicPlan(graph);
        }

        ChordFeedbackPlan chordFeedbackPlan = ChordFeedbackPlanner.plan(graph);
        List<ScalarSolverRegion> regions = new ArrayList<>();
        Set<PortGraphNode> feedbackNodes = new HashSet<>();
        Set<Integer> feedbackEdgeIds = new HashSet<>();
        int feedbackRegionCount = 0;
        int fallbackRegionCount = 0;
        int maxBeta1 = 0;
        boolean allFeedbackRegionsExact = true;
        boolean allFeedbackRegionsChord = true;
        boolean allFeedbackRegionsExecutable = true;

        for (PortGraphScc scc : graph.sccs()) {
            if (!scc.feedback()) {
                continue;
            }

            feedbackNodes.addAll(scc.nodes());
            feedbackEdgeIds.addAll(scc.edgeIds());
            feedbackRegionCount++;
            maxBeta1 = Math.max(maxBeta1, scc.beta1());
            ScalarSolverKind preferredKind = preferredFeedbackSolver(scc);
            ScalarSolverKind executionKind = executionFeedbackSolver(chordFeedbackPlan, scc, preferredKind);
            allFeedbackRegionsExact &= executionKind == ScalarSolverKind.FEEDBACK_SCC_EXACT;
            allFeedbackRegionsChord &= executionKind == ScalarSolverKind.FEEDBACK_CHORD;
            allFeedbackRegionsExecutable &= executionKind != ScalarSolverKind.ITERATIVE_FIXED_POINT;

            if (preferredKind != executionKind) {
                fallbackRegionCount++;
            }

            regions.add(new ScalarSolverRegion(
                    regions.size(),
                    scc.id(),
                    scc.nodes(),
                    scc.edgeIds(),
                    scc.beta1(),
                    true,
                    preferredKind,
                    executionKind
            ));
        }

        Set<PortGraphNode> dagNodes = new HashSet<>(graph.nodes());
        dagNodes.removeAll(feedbackNodes);
        List<Integer> dagEdgeIds = nonFeedbackEdgeIds(graph, feedbackEdgeIds);
        int dagRegionCount = 0;

        if (!dagNodes.isEmpty() || !dagEdgeIds.isEmpty()) {
            dagRegionCount = 1;
            regions.add(new ScalarSolverRegion(
                    regions.size(),
                    -1,
                    dagNodes,
                    dagEdgeIds,
                    0,
                    false,
                    ScalarSolverKind.TOPOLOGICAL_DAG,
                    ScalarSolverKind.TOPOLOGICAL_DAG
            ));
        }

        return new ScalarSolverPlan(
                primarySolverKind(allFeedbackRegionsExact, allFeedbackRegionsChord, allFeedbackRegionsExecutable),
                regions,
                dagRegionCount,
                feedbackRegionCount,
                fallbackRegionCount,
                maxBeta1,
                chordFeedbackPlan
        );
    }

    private static ScalarSolverPlan acyclicPlan(CompiledPortGraph graph) {
        List<Integer> edgeIds = graph.edges().stream()
                .map(PortGraphEdge::id)
                .toList();
        ScalarSolverRegion region = new ScalarSolverRegion(
                0,
                -1,
                Set.copyOf(graph.nodes()),
                edgeIds,
                0,
                false,
                ScalarSolverKind.TOPOLOGICAL_DAG,
                ScalarSolverKind.TOPOLOGICAL_DAG
        );

        return new ScalarSolverPlan(
                ScalarSolverKind.TOPOLOGICAL_DAG,
                List.of(region),
                1,
                0,
                0,
                0,
                ChordFeedbackPlan.empty()
        );
    }

    private static ScalarSolverKind preferredFeedbackSolver(PortGraphScc scc) {
        if (scc.beta1() <= SMALL_EXACT_FEEDBACK_BETA1) {
            return ScalarSolverKind.FEEDBACK_SCC_EXACT;
        }

        return ScalarSolverKind.FEEDBACK_CHORD;
    }

    private static ScalarSolverKind executionFeedbackSolver(
            ChordFeedbackPlan chordFeedbackPlan,
            PortGraphScc scc,
            ScalarSolverKind preferredKind
    ) {
        if (preferredKind == ScalarSolverKind.FEEDBACK_SCC_EXACT
                && FeedbackSccScalarSolver.implemented()
                && scc.nodes().size() <= SMALL_EXACT_FEEDBACK_NODES) {
            return ScalarSolverKind.FEEDBACK_SCC_EXACT;
        }

        if (preferredKind == ScalarSolverKind.FEEDBACK_CHORD
                && ChordFeedbackScalarSolver.planningImplemented()
                && ChordFeedbackScalarSolver.hasCompilableSystem(chordFeedbackPlan, scc.id())) {
            return ScalarSolverKind.FEEDBACK_CHORD;
        }

        return ScalarSolverKind.ITERATIVE_FIXED_POINT;
    }

    private static ScalarSolverKind primarySolverKind(
            boolean allFeedbackRegionsExact,
            boolean allFeedbackRegionsChord,
            boolean allFeedbackRegionsExecutable
    ) {
        if (allFeedbackRegionsExact) {
            return ScalarSolverKind.FEEDBACK_SCC_EXACT;
        }

        if (allFeedbackRegionsChord) {
            return ScalarSolverKind.FEEDBACK_CHORD;
        }

        if (allFeedbackRegionsExecutable) {
            return ScalarSolverKind.MIXED_REGION;
        }

        return ScalarSolverKind.ITERATIVE_FIXED_POINT;
    }

    private static List<Integer> nonFeedbackEdgeIds(CompiledPortGraph graph, Set<Integer> feedbackEdgeIds) {
        List<Integer> edgeIds = new ArrayList<>();

        for (PortGraphEdge edge : graph.edges()) {
            if (!feedbackEdgeIds.contains(edge.id())) {
                edgeIds.add(edge.id());
            }
        }

        return edgeIds;
    }

    private ScalarSolverPlanner() {
    }
}
