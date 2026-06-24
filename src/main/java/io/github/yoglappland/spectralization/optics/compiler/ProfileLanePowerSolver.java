package io.github.yoglappland.spectralization.optics.compiler;

import io.github.yoglappland.spectralization.block.FiberOpticInterfaceBlock;
import io.github.yoglappland.spectralization.optics.CoherenceKind;
import io.github.yoglappland.spectralization.optics.FrequencyKey;
import io.github.yoglappland.spectralization.optics.fiber.FiberLikeFaces;
import io.github.yoglappland.spectralization.optics.fiber.FiberOpticalTransfer;
import io.github.yoglappland.spectralization.optics.geometry.BeamGeometryOps;
import io.github.yoglappland.spectralization.optics.geometry.BeamProfileKey;
import io.github.yoglappland.spectralization.optics.geometry.BeamProfileTransfer;
import io.github.yoglappland.spectralization.optics.geometry.PhaseSpaceMap;
import io.github.yoglappland.spectralization.optics.geometry.PhaseSpaceMapSignature;
import io.github.yoglappland.spectralization.optics.geometry.SpatialProfileElement;
import io.github.yoglappland.spectralization.optics.geometry.SpatialTransformContext;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class ProfileLanePowerSolver {
    private static final int MAX_PROFILE_STATES = 4096;
    private static final int MAX_EXACT_SCC_STATES = 384;
    private static final double PROFILE_PATH_GAIN_CUTOFF = 1.0E-8;
    private static final double PIVOT_EPSILON = 1.0E-10;
    private static final double RESIDUAL_EPSILON = 1.0E-6;
    private static final double RELATIVE_RESIDUAL_EPSILON = 1.0E-7;
    private static final double MAX_TOTAL_POWER = 1.0E12;

    public static ScalarPowerSolution solve(
            Level level,
            CompiledPortGraph graph,
            Map<SpectralPowerLane, Map<PortGraphNode, Double>> sourcePowersByLane
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(sourcePowersByLane, "sourcePowersByLane");

        ScalarSolverPlan solverPlan = ScalarSolverPlanner.plan(graph);
        Map<PortGraphNode, Map<SpectralPowerLane, Double>> sources = sourceNodeLanes(sourcePowersByLane, graph.nodes());

        if (sources.isEmpty() || graph.nodes().isEmpty()) {
            return ScalarPowerSolutions.empty(ScalarSolverKind.NONE, solverPlan);
        }

        FiniteProfileSystem system = buildFiniteProfileSystem(level, graph, sources);

        if (system.overflow() || system.states().isEmpty()) {
            return solveProfileCollapsedExact(level, graph, sources, solverPlan, true, system.overflow());
        }

        ExactSolveResult solveResult = solveFiniteSystem(system);

        if (!solveResult.solved()) {
            return solveProfileCollapsedExact(level, graph, sources, solverPlan, true, false);
        }

        double residual = residual(system, solveResult.powers());
        double totalPower = totalPower(solveResult.powers());
        boolean unstable = !Double.isFinite(residual)
                || !Double.isFinite(totalPower)
                || totalPower > MAX_TOTAL_POWER
                || hasMeaningfulNegativePower(solveResult.powers());
        boolean converged = !unstable
                && (residual <= RESIDUAL_EPSILON
                || residual <= RELATIVE_RESIDUAL_EPSILON * Math.max(1.0D, totalPower));

        clampTinyNegativePowers(solveResult.powers());

        return solutionFromStatePowers(
                ScalarSolverKind.PROFILE_STATE_EXACT,
                solverPlan,
                converged,
                unstable,
                1,
                Double.isFinite(residual) ? residual : MAX_TOTAL_POWER,
                system.states(),
                system.readoutProjections(),
                solveResult.powers(),
                false,
                false
        );
    }

    private static ScalarPowerSolution solveProfileCollapsedExact(
            Level level,
            CompiledPortGraph graph,
            Map<PortGraphNode, Map<SpectralPowerLane, Double>> sources,
            ScalarSolverPlan solverPlan,
            boolean fallback,
            boolean overflow
    ) {
        FiniteProfileSystem system = buildProfileCollapsedSystem(level, graph, sources);

        if (system.states().isEmpty()) {
            return unstableSolution(ScalarSolverKind.PROFILE_COLLAPSED_EXACT, solverPlan, fallback, overflow);
        }

        ExactSolveResult solveResult = solveFiniteSystem(system);

        if (!solveResult.solved()) {
            return unstableSolution(ScalarSolverKind.PROFILE_COLLAPSED_EXACT, solverPlan, fallback, overflow);
        }

        double residual = residual(system, solveResult.powers());
        double totalPower = totalPower(solveResult.powers());
        boolean unstable = !Double.isFinite(residual)
                || !Double.isFinite(totalPower)
                || totalPower > MAX_TOTAL_POWER
                || hasMeaningfulNegativePower(solveResult.powers());
        boolean converged = !unstable
                && (residual <= RESIDUAL_EPSILON
                || residual <= RELATIVE_RESIDUAL_EPSILON * Math.max(1.0D, totalPower));

        clampTinyNegativePowers(solveResult.powers());

        return solutionFromStatePowers(
                ScalarSolverKind.PROFILE_COLLAPSED_EXACT,
                solverPlan,
                converged,
                unstable,
                1,
                Double.isFinite(residual) ? residual : MAX_TOTAL_POWER,
                system.states(),
                system.readoutProjections(),
                solveResult.powers(),
                fallback,
                overflow
        );
    }

    private static FiniteProfileSystem buildProfileCollapsedSystem(
            Level level,
            CompiledPortGraph graph,
            Map<PortGraphNode, Map<SpectralPowerLane, Double>> sources
    ) {
        List<SpectralPowerLane> lanes = sources.values().stream()
                .flatMap(lanePowers -> lanePowers.keySet().stream())
                .distinct()
                .sorted(SpectralPowerLane.COMPARATOR)
                .toList();
        Map<ProfileStateKey, Integer> stateIds = new HashMap<>();
        List<ProfileStateKey> states = new ArrayList<>();
        List<ProfileTransition> transitions = new ArrayList<>();

        for (SpectralPowerLane lane : lanes) {
            for (PortGraphNode node : graph.nodes()) {
                ProfileStateKey state = new ProfileStateKey(node, lane);
                stateIds.put(state, states.size());
                states.add(state);
            }
        }

        double[] source = new double[states.size()];

        for (Map.Entry<PortGraphNode, Map<SpectralPowerLane, Double>> nodeEntry : sources.entrySet()) {
            for (Map.Entry<SpectralPowerLane, Double> laneEntry : nodeEntry.getValue().entrySet()) {
                Integer stateId = stateIds.get(new ProfileStateKey(nodeEntry.getKey(), laneEntry.getKey()));

                if (stateId != null && laneEntry.getValue() > 0.0D) {
                    source[stateId] += laneEntry.getValue();
                }
            }
        }

        for (SpectralPowerLane lane : lanes) {
            for (PortGraphEdge edge : graph.edges()) {
                double baseGain = Math.max(0.0D, edge.sampleGainFor(lane.frequency()));

                if (baseGain <= 0.0D) {
                    continue;
                }

                double profileGain = collapsedEquivalentProfileGain(level, graph, edge, lane);
                double gain = baseGain * profileGain;

                if (gain <= 0.0D) {
                    continue;
                }

                Integer from = stateIds.get(new ProfileStateKey(edge.from(), lane));
                Integer to = stateIds.get(new ProfileStateKey(edge.to(), lane));

                if (from != null && to != null) {
                    transitions.add(new ProfileTransition(from, to, gain));
                }
            }
        }

        return new FiniteProfileSystem(
                List.copyOf(states),
                List.copyOf(transitions),
                List.of(),
                source,
                false
        );
    }

    private static double collapsedEquivalentProfileGain(
            Level level,
            CompiledPortGraph graph,
            PortGraphEdge edge,
            SpectralPowerLane lane
    ) {
        BeamProfileTransfer transfer = profileTransfer(
                level,
                graph,
                edge,
                lane,
                profileTransitionSignature(level, graph, edge, lane)
        );
        return BeamGeometryOps.clamp01(transfer.gain());
    }

    private static FiniteProfileSystem buildFiniteProfileSystem(
            Level level,
            CompiledPortGraph graph,
            Map<PortGraphNode, Map<SpectralPowerLane, Double>> sources
    ) {
        FiniteProfileBuilder builder = new FiniteProfileBuilder(level, graph);

        for (Map.Entry<PortGraphNode, Map<SpectralPowerLane, Double>> nodeEntry : sources.entrySet()) {
            for (Map.Entry<SpectralPowerLane, Double> laneEntry : nodeEntry.getValue().entrySet()) {
                builder.addSource(new ProfileStateKey(nodeEntry.getKey(), laneEntry.getKey()), laneEntry.getValue());
            }
        }

        return builder.build();
    }

    private static ExactSolveResult solveFiniteSystem(FiniteProfileSystem system) {
        StateSccGraph sccGraph = StateSccGraph.build(system.states().size(), system.transitions());
        double[] rhs = system.source().clone();
        double[] powers = new double[system.states().size()];
        int[] pendingIncoming = sccGraph.inDegrees().clone();
        ArrayDeque<Integer> pendingSccs = new ArrayDeque<>();
        int visitedSccs = 0;

        for (int sccId = 0; sccId < pendingIncoming.length; sccId++) {
            if (pendingIncoming[sccId] == 0) {
                pendingSccs.addLast(sccId);
            }
        }

        while (!pendingSccs.isEmpty()) {
            int sccId = pendingSccs.removeFirst();
            visitedSccs++;

            if (!solveScc(sccGraph, sccId, rhs, powers)) {
                return ExactSolveResult.unsolved();
            }

            for (ProfileTransition transition : sccGraph.outgoingTransitionsByScc().get(sccId)) {
                rhs[transition.to()] += powers[transition.from()] * transition.gain();
                int targetSccId = sccGraph.sccIdByState()[transition.to()];
                pendingIncoming[targetSccId]--;

                if (pendingIncoming[targetSccId] == 0) {
                    pendingSccs.addLast(targetSccId);
                }
            }
        }

        if (visitedSccs != sccGraph.sccs().size() || hasNonFinitePower(powers)) {
            return ExactSolveResult.unsolved();
        }

        return ExactSolveResult.solved(powers);
    }

    private static boolean solveScc(
            StateSccGraph sccGraph,
            int sccId,
            double[] rhs,
            double[] powers
    ) {
        List<Integer> states = sccGraph.sccs().get(sccId);
        List<ProfileTransition> internalTransitions = sccGraph.internalTransitionsByScc().get(sccId);

        if (states.size() == 1 && internalTransitions.isEmpty()) {
            int stateId = states.getFirst();
            powers[stateId] = rhs[stateId];
            return Double.isFinite(powers[stateId]);
        }

        if (states.size() > MAX_EXACT_SCC_STATES) {
            return false;
        }

        Map<Integer, Integer> localIdByState = new HashMap<>();
        for (int localId = 0; localId < states.size(); localId++) {
            localIdByState.put(states.get(localId), localId);
        }

        int n = states.size();
        double[][] matrix = new double[n][n + 1];

        for (int localId = 0; localId < n; localId++) {
            int stateId = states.get(localId);
            matrix[localId][localId] = 1.0D;
            matrix[localId][n] = rhs[stateId];
        }

        for (ProfileTransition transition : internalTransitions) {
            Integer fromLocalId = localIdByState.get(transition.from());
            Integer toLocalId = localIdByState.get(transition.to());

            if (fromLocalId == null || toLocalId == null) {
                return false;
            }

            matrix[toLocalId][fromLocalId] -= transition.gain();
        }

        LinearSolveResult result = solveLinearSystem(matrix, n);

        if (!result.solved() || hasNonFinitePower(result.solution())) {
            return false;
        }

        for (int localId = 0; localId < n; localId++) {
            powers[states.get(localId)] = result.solution()[localId];
        }

        return true;
    }

    private static LinearSolveResult solveLinearSystem(double[][] matrix, int size) {
        for (int pivotColumn = 0; pivotColumn < size; pivotColumn++) {
            int pivotRow = pivotColumn;
            double pivotMagnitude = Math.abs(matrix[pivotRow][pivotColumn]);

            for (int row = pivotColumn + 1; row < size; row++) {
                double candidateMagnitude = Math.abs(matrix[row][pivotColumn]);

                if (candidateMagnitude > pivotMagnitude) {
                    pivotRow = row;
                    pivotMagnitude = candidateMagnitude;
                }
            }

            if (!Double.isFinite(pivotMagnitude) || pivotMagnitude <= PIVOT_EPSILON) {
                return LinearSolveResult.unsolved();
            }

            if (pivotRow != pivotColumn) {
                double[] swap = matrix[pivotColumn];
                matrix[pivotColumn] = matrix[pivotRow];
                matrix[pivotRow] = swap;
            }

            double pivot = matrix[pivotColumn][pivotColumn];

            for (int column = pivotColumn; column <= size; column++) {
                matrix[pivotColumn][column] /= pivot;
            }

            for (int row = 0; row < size; row++) {
                if (row == pivotColumn) {
                    continue;
                }

                double factor = matrix[row][pivotColumn];

                if (factor == 0.0D) {
                    continue;
                }

                for (int column = pivotColumn; column <= size; column++) {
                    matrix[row][column] -= factor * matrix[pivotColumn][column];
                }
            }
        }

        double[] solution = new double[size];

        for (int row = 0; row < size; row++) {
            solution[row] = matrix[row][size];
        }

        return LinearSolveResult.solved(solution);
    }

    private static BeamProfileTransfer profileTransfer(
            Level level,
            CompiledPortGraph graph,
            PortGraphEdge edge,
            SpectralPowerLane lane,
            ProfileTransitionSignature signature
    ) {
        BeamProfileKey inputProfile = lane.profile();

        if (signature.kind() == ProfileTransitionKind.FREE_SPACE) {
            return BeamProfileTransfer.of(inputProfile.toShape().propagate(edge.distance()).toKey(), 1.0D);
        }

        if (signature.kind() == ProfileTransitionKind.IDENTITY
                || edge.kind() != PortGraphEdgeKind.LOCAL_SCATTERING
                || !level.isLoaded(edge.from().pos())) {
            return BeamProfileTransfer.of(inputProfile, 1.0D);
        }

        if (isFiberTransferEdge(level, edge) && level instanceof ServerLevel serverLevel) {
            return FiberOpticalTransfer.profileTransferForEdge(
                    serverLevel,
                    edge.from().pos(),
                    edge.from().side(),
                    edge.to().pos(),
                    edge.to().side(),
                    inputProfile
            );
        }

        BlockState state = level.getBlockState(edge.from().pos());
        Block block = state.getBlock();

        if (!(block instanceof SpatialProfileElement profileElement)) {
            return BeamProfileTransfer.of(inputProfile, 1.0D);
        }

        return profileElement.transformProfileState(inputProfile, spatialTransformContext(level, graph, edge, lane, state));
    }

    private static ProfileTransitionSignature profileTransitionSignature(
            Level level,
            CompiledPortGraph graph,
            PortGraphEdge edge,
            SpectralPowerLane lane
    ) {
        if (edge.kind() == PortGraphEdgeKind.PROPAGATION) {
            if (FiberLikeFaces.isDirectGuidedAdjacency(
                    level,
                    edge.from().pos(),
                    edge.from().side(),
                    edge.to().pos(),
                    edge.to().side()
            )) {
                return ProfileTransitionSignature.identity();
            }

            return ProfileTransitionSignature.freeSpace(edge.distance());
        }

        if (edge.kind() != PortGraphEdgeKind.LOCAL_SCATTERING || !level.isLoaded(edge.from().pos())) {
            return ProfileTransitionSignature.identity();
        }

        if (isFiberTransferEdge(level, edge)) {
            return new ProfileTransitionSignature(
                    ProfileTransitionKind.FIBER_INTERFACE,
                    PhaseSpaceMapSignature.IDENTITY,
                    0,
                    edge.id(),
                    edge.from() + "->" + edge.to()
            );
        }

        BlockState state = level.getBlockState(edge.from().pos());
        Block block = state.getBlock();

        if (!(block instanceof SpatialProfileElement profileElement)) {
            return ProfileTransitionSignature.identity();
        }

        SpatialTransformContext context = spatialTransformContext(level, graph, edge, lane, state);
        return new ProfileTransitionSignature(
                ProfileTransitionKind.SPATIAL_ELEMENT,
                profileElement.profilePhaseSpaceMap(context).signature(),
                edge.distance(),
                edge.id(),
                profileElement.profileTransitionSignature(context)
        );
    }

    private static SpatialTransformContext spatialTransformContext(
            Level level,
            CompiledPortGraph graph,
            PortGraphEdge edge,
            SpectralPowerLane lane,
            BlockState state
    ) {
        return new SpatialTransformContext(
                level,
                edge.from().pos(),
                state,
                lane.frequency(),
                lane.coherence(),
                edge.from().side(),
                edge.to().side(),
                edge.distance(),
                feedbackSccContains(graph, edge)
        );
    }

    private static boolean isFiberTransferEdge(Level level, PortGraphEdge edge) {
        if (edge.from().pos().equals(edge.to().pos())) {
            return false;
        }

        if (!level.isLoaded(edge.from().pos()) || !level.isLoaded(edge.to().pos())) {
            return false;
        }

        return level.getBlockState(edge.from().pos()).getBlock() instanceof FiberOpticInterfaceBlock
                && level.getBlockState(edge.to().pos()).getBlock() instanceof FiberOpticInterfaceBlock
                && edge.from().waveKind() == PortWaveKind.INCOMING
                && edge.to().waveKind() == PortWaveKind.OUTGOING;
    }

    private static boolean feedbackSccContains(CompiledPortGraph graph, PortGraphEdge edge) {
        for (PortGraphScc scc : graph.sccs()) {
            if (scc.feedback() && scc.nodes().contains(edge.from()) && scc.nodes().contains(edge.to())) {
                return true;
            }
        }

        return false;
    }

    private static Map<PortGraphNode, Map<SpectralPowerLane, Double>> sourceNodeLanes(
            Map<SpectralPowerLane, Map<PortGraphNode, Double>> sourcePowersByLane,
            List<PortGraphNode> graphNodes
    ) {
        Set<PortGraphNode> nodes = new HashSet<>(graphNodes);
        Map<PortGraphNode, Map<SpectralPowerLane, Double>> sources = new HashMap<>();

        for (Map.Entry<SpectralPowerLane, Map<PortGraphNode, Double>> laneEntry : sourcePowersByLane.entrySet()) {
            for (Map.Entry<PortGraphNode, Double> nodeEntry : laneEntry.getValue().entrySet()) {
                if (nodeEntry.getValue() > 0.0D && nodes.contains(nodeEntry.getKey())) {
                    sources.computeIfAbsent(nodeEntry.getKey(), ignored -> new HashMap<>())
                            .merge(laneEntry.getKey(), nodeEntry.getValue(), Double::sum);
                }
            }
        }

        return sources;
    }

    private static Map<PortGraphNode, List<PortGraphEdge>> outgoingEdges(CompiledPortGraph graph) {
        Map<PortGraphNode, List<PortGraphEdge>> outgoing = new HashMap<>();

        for (PortGraphEdge edge : graph.edges()) {
            outgoing.computeIfAbsent(edge.from(), ignored -> new ArrayList<>()).add(edge);
        }

        for (List<PortGraphEdge> edges : outgoing.values()) {
            edges.sort(Comparator.comparingInt(PortGraphEdge::id));
        }

        return outgoing;
    }

    private static double residual(FiniteProfileSystem system, double[] powers) {
        double[] reconstructed = system.source().clone();

        for (ProfileTransition transition : system.transitions()) {
            reconstructed[transition.to()] += powers[transition.from()] * transition.gain();
        }

        double residual = 0.0D;

        for (int index = 0; index < powers.length; index++) {
            residual = Math.max(residual, Math.abs(reconstructed[index] - powers[index]));
        }

        return residual;
    }

    private static double totalPower(double[] powers) {
        double total = 0.0D;

        for (double power : powers) {
            if (power > 0.0D) {
                total += power;
            }
        }

        return total;
    }

    private static boolean hasMeaningfulNegativePower(double[] powers) {
        for (double power : powers) {
            if (power < -RESIDUAL_EPSILON) {
                return true;
            }
        }

        return false;
    }

    private static boolean hasNonFinitePower(double[] powers) {
        for (double power : powers) {
            if (!Double.isFinite(power)) {
                return true;
            }
        }

        return false;
    }

    private static void clampTinyNegativePowers(double[] powers) {
        for (int index = 0; index < powers.length; index++) {
            if (powers[index] < 0.0D && powers[index] >= -RESIDUAL_EPSILON) {
                powers[index] = 0.0D;
            }
        }
    }

    private static ScalarPowerSolution unstableSolution(
            ScalarSolverKind solverKind,
            ScalarSolverPlan solverPlan,
            boolean fallback,
            boolean overflow
    ) {
        return new ScalarPowerSolution(
                solverKind,
                solverPlan,
                false,
                true,
                1,
                MAX_TOTAL_POWER,
                0.0D,
                0.0D,
                Map.of(),
                Map.of(),
                Map.of(),
                List.of(),
                fallback,
                overflow
        );
    }

    private static ScalarPowerSolution solutionFromStatePowers(
            ScalarSolverKind solverKind,
            ScalarSolverPlan solverPlan,
            boolean converged,
            boolean unstable,
            int iterations,
            double residual,
            List<ProfileStateKey> states,
            List<ProfileReadoutProjection> readoutProjections,
            double[] powers,
            boolean fallback,
            boolean overflow
    ) {
        Map<PortGraphNode, Double> powerByNode = new HashMap<>();
        Map<PortGraphNode, Double> coherentPowerByNode = new HashMap<>();
        Map<SpectralPowerLane, Map<PortGraphNode, Double>> powerByLane = new HashMap<>();
        double maxPower = 0.0D;
        double totalPower = 0.0D;

        for (int index = 0; index < states.size(); index++) {
            double power = powers[index];

            if (power <= 0.0D) {
                continue;
            }

            ProfileStateKey state = states.get(index);
            powerByNode.merge(state.node(), power, Double::sum);
            powerByLane.computeIfAbsent(state.lane(), ignored -> new HashMap<>())
                    .merge(state.node(), power, Double::sum);

            if (state.lane().coherence() == CoherenceKind.COHERENT) {
                coherentPowerByNode.merge(state.node(), power, Double::sum);
            }
        }

        for (ProfileReadoutProjection projection : readoutProjections) {
            double sourcePower = powers[projection.from()];

            if (sourcePower <= 0.0D) {
                continue;
            }

            double power = sourcePower * projection.gain();

            if (power <= 0.0D) {
                continue;
            }

            powerByNode.merge(projection.node(), power, Double::sum);
            powerByLane.computeIfAbsent(projection.lane(), ignored -> new HashMap<>())
                    .merge(projection.node(), power, Double::sum);

            if (projection.lane().coherence() == CoherenceKind.COHERENT) {
                coherentPowerByNode.merge(projection.node(), power, Double::sum);
            }
        }

        for (double power : powerByNode.values()) {
            maxPower = Math.max(maxPower, power);
            totalPower += power;
        }

        return new ScalarPowerSolution(
                solverKind,
                solverPlan,
                converged,
                unstable,
                iterations,
                residual,
                maxPower,
                totalPower,
                powerByNode,
                coherentPowerByNode,
                powerByLane,
                List.of(),
                fallback,
                overflow
        );
    }

    private static final class FiniteProfileBuilder {
        private final Level level;
        private final CompiledPortGraph graph;
        private final Map<PortGraphNode, List<PortGraphEdge>> outgoingEdges;
        private final Map<ProfileStateKey, Integer> stateIds = new HashMap<>();
        private final List<ProfileStateKey> states = new ArrayList<>();
        private final List<ProfileTransition> transitions = new ArrayList<>();
        private final List<ProfileReadoutProjection> readoutProjections = new ArrayList<>();
        private final Map<Integer, Double> sourceByState = new HashMap<>();
        private final Map<TransitionCacheKey, BeamProfileTransfer> transitionCache = new HashMap<>();
        private final Map<FoldedPathCacheKey, FoldedProfilePath> foldedPathCache = new HashMap<>();
        private final ArrayDeque<Integer> pendingStates = new ArrayDeque<>();
        private final Set<PortGraphNode> sourceBoundaryNodes = new HashSet<>();
        private boolean overflow;

        private FiniteProfileBuilder(Level level, CompiledPortGraph graph) {
            this.level = level;
            this.graph = graph;
            this.outgoingEdges = outgoingEdges(graph);
        }

        private void addSource(ProfileStateKey state, double power) {
            if (power <= 0.0D || overflow) {
                return;
            }

            int stateId = stateIdFor(state);

            if (stateId >= 0) {
                sourceBoundaryNodes.add(state.node());
                sourceByState.merge(stateId, power, Double::sum);
            }
        }

        private FiniteProfileSystem build() {
            while (!pendingStates.isEmpty() && !overflow) {
                int stateId = pendingStates.removeFirst();
                ProfileStateKey state = states.get(stateId);

                for (PortGraphEdge edge : outgoingEdges.getOrDefault(state.node(), List.of())) {
                    FoldedProfilePath path = foldedPathFor(state, edge);

                    if (path.gain() <= 0.0D) {
                        continue;
                    }

                    ProfileStateKey outputState = new ProfileStateKey(
                            path.outputNode(),
                            path.outputLane()
                    );
                    int outputStateId = stateIdFor(outputState);

                    if (outputStateId < 0) {
                        break;
                    }

                    for (ProfileReadoutStep readout : path.readouts()) {
                        readoutProjections.add(new ProfileReadoutProjection(
                                stateId,
                                readout.node(),
                                readout.lane(),
                                readout.gain()
                        ));
                    }

                    transitions.add(new ProfileTransition(stateId, outputStateId, path.gain()));
                }
            }

            double[] source = new double[states.size()];

            for (Map.Entry<Integer, Double> entry : sourceByState.entrySet()) {
                if (entry.getKey() < source.length) {
                    source[entry.getKey()] = entry.getValue();
                }
            }

            return new FiniteProfileSystem(
                    List.copyOf(states),
                    List.copyOf(transitions),
                    List.copyOf(readoutProjections),
                    source,
                    overflow
            );
        }

        private int stateIdFor(ProfileStateKey state) {
            Integer existing = stateIds.get(state);

            if (existing != null) {
                return existing;
            }

            if (states.size() >= MAX_PROFILE_STATES) {
                overflow = true;
                return -1;
            }

            int stateId = states.size();
            stateIds.put(state, stateId);
            states.add(state);
            pendingStates.addLast(stateId);
            return stateId;
        }

        private BeamProfileTransfer transitionFor(PortGraphEdge edge, SpectralPowerLane lane) {
            ProfileTransitionSignature signature = profileTransitionSignature(level, graph, edge, lane);
            TransitionCacheKey key = new TransitionCacheKey(
                    signature,
                    lane.frequency(),
                    lane.coherence(),
                    lane.profile()
            );
            return transitionCache.computeIfAbsent(key, ignored -> profileTransfer(level, graph, edge, lane, signature));
        }

        private FoldedProfilePath foldedPathFor(ProfileStateKey state, PortGraphEdge firstEdge) {
            FoldedPathCacheKey cacheKey = new FoldedPathCacheKey(state, firstEdge.id());
            return foldedPathCache.computeIfAbsent(cacheKey, ignored -> buildFoldedPath(state, firstEdge));
        }

        private FoldedProfilePath buildFoldedPath(ProfileStateKey startState, PortGraphEdge firstEdge) {
            PortGraphNode startNode = startState.node();
            PortGraphNode previousNode = startNode;
            SpectralPowerLane lane = startState.lane();
            PortGraphEdge edge = firstEdge;
            double accumulatedGain = 1.0D;
            List<ProfileReadoutStep> readouts = new ArrayList<>();
            Set<FoldVisitKey> visited = new HashSet<>();

            while (true) {
                FoldVisitKey visitKey = new FoldVisitKey(edge.id(), lane);

                if (!visited.add(visitKey)) {
                    return new FoldedProfilePath(previousNode, lane, accumulatedGain, List.copyOf(readouts));
                }

                double baseGain = Math.max(0.0D, edge.sampleGainFor(lane.frequency()));

                if (baseGain <= 0.0D) {
                    return stopOrBlock(startState, previousNode, lane, accumulatedGain, readouts);
                }

                BeamProfileTransfer transfer = transitionFor(edge, lane);
                double stepGain = baseGain * transfer.gain();

                if (stepGain <= 0.0D) {
                    return stopOrBlock(startState, previousNode, lane, accumulatedGain, readouts);
                }

                accumulatedGain *= stepGain;

                if (!Double.isFinite(accumulatedGain) || accumulatedGain <= 0.0D) {
                    return FoldedProfilePath.blocked(startNode, startState.lane());
                }

                if (accumulatedGain < PROFILE_PATH_GAIN_CUTOFF) {
                    return FoldedProfilePath.blocked(startNode, startState.lane());
                }

                lane = lane.withProfile(transfer.outputProfile());
                PortGraphNode currentNode = edge.to();

                if (shouldStopFold(startNode, currentNode)) {
                    return new FoldedProfilePath(currentNode, lane, accumulatedGain, List.copyOf(readouts));
                }

                PortGraphEdge nextEdge = outgoingEdges.getOrDefault(currentNode, List.of()).getFirst();

                if (!edgeCanTransmit(nextEdge, lane)) {
                    return new FoldedProfilePath(currentNode, lane, accumulatedGain, List.copyOf(readouts));
                }

                readouts.add(new ProfileReadoutStep(currentNode, lane, accumulatedGain));
                previousNode = currentNode;
                edge = nextEdge;
            }
        }

        private FoldedProfilePath stopOrBlock(
                ProfileStateKey startState,
                PortGraphNode previousNode,
                SpectralPowerLane lane,
                double accumulatedGain,
                List<ProfileReadoutStep> readouts
        ) {
            if (previousNode.equals(startState.node())
                    && lane.equals(startState.lane())
                    && accumulatedGain == 1.0D
                    && readouts.isEmpty()) {
                return FoldedProfilePath.blocked(startState.node(), startState.lane());
            }

            return new FoldedProfilePath(previousNode, lane, accumulatedGain, List.copyOf(readouts));
        }

        private boolean shouldStopFold(PortGraphNode startNode, PortGraphNode currentNode) {
            if (currentNode.equals(startNode) || sourceBoundaryNodes.contains(currentNode)) {
                return true;
            }

            return outgoingEdges.getOrDefault(currentNode, List.of()).size() != 1;
        }

        private boolean edgeCanTransmit(PortGraphEdge edge, SpectralPowerLane lane) {
            if (Math.max(0.0D, edge.sampleGainFor(lane.frequency())) <= 0.0D) {
                return false;
            }

            return transitionFor(edge, lane).gain() > 0.0D;
        }
    }

    private record FiniteProfileSystem(
            List<ProfileStateKey> states,
            List<ProfileTransition> transitions,
            List<ProfileReadoutProjection> readoutProjections,
            double[] source,
            boolean overflow
    ) {
    }

    private record ProfileStateKey(PortGraphNode node, SpectralPowerLane lane) {
        private ProfileStateKey {
            Objects.requireNonNull(node, "node");
            Objects.requireNonNull(lane, "lane");
        }
    }

    private record ProfileTransition(int from, int to, double gain) {
        private ProfileTransition {
            if (from < 0 || to < 0) {
                throw new IllegalArgumentException("Profile transition state ids must be non-negative");
            }

            if (!Double.isFinite(gain) || gain <= 0.0D) {
                throw new IllegalArgumentException("Profile transition gain must be finite and positive");
            }
        }
    }

    private record ProfileReadoutProjection(int from, PortGraphNode node, SpectralPowerLane lane, double gain) {
        private ProfileReadoutProjection {
            if (from < 0) {
                throw new IllegalArgumentException("Profile readout source state id must be non-negative");
            }

            Objects.requireNonNull(node, "node");
            Objects.requireNonNull(lane, "lane");

            if (!Double.isFinite(gain) || gain <= 0.0D) {
                throw new IllegalArgumentException("Profile readout gain must be finite and positive");
            }
        }
    }

    private record ProfileReadoutStep(PortGraphNode node, SpectralPowerLane lane, double gain) {
        private ProfileReadoutStep {
            Objects.requireNonNull(node, "node");
            Objects.requireNonNull(lane, "lane");

            if (!Double.isFinite(gain) || gain <= 0.0D) {
                throw new IllegalArgumentException("Profile readout step gain must be finite and positive");
            }
        }
    }

    private record FoldedProfilePath(
            PortGraphNode outputNode,
            SpectralPowerLane outputLane,
            double gain,
            List<ProfileReadoutStep> readouts
    ) {
        private static FoldedProfilePath blocked(PortGraphNode outputNode, SpectralPowerLane outputLane) {
            return new FoldedProfilePath(outputNode, outputLane, 0.0D, List.of());
        }

        private FoldedProfilePath {
            Objects.requireNonNull(outputNode, "outputNode");
            Objects.requireNonNull(outputLane, "outputLane");
            Objects.requireNonNull(readouts, "readouts");

            if (!Double.isFinite(gain) || gain < 0.0D) {
                throw new IllegalArgumentException("Folded profile path gain must be finite and non-negative");
            }

            readouts = List.copyOf(readouts);
        }
    }

    private record FoldVisitKey(int edgeId, SpectralPowerLane lane) {
        private FoldVisitKey {
            if (edgeId < 0) {
                throw new IllegalArgumentException("Fold visit edge id must be non-negative");
            }

            Objects.requireNonNull(lane, "lane");
        }
    }

    private record FoldedPathCacheKey(ProfileStateKey state, int firstEdgeId) {
        private FoldedPathCacheKey {
            Objects.requireNonNull(state, "state");

            if (firstEdgeId < 0) {
                throw new IllegalArgumentException("Folded path edge id must be non-negative");
            }
        }
    }

    private enum ProfileTransitionKind {
        FREE_SPACE,
        FIBER_INTERFACE,
        SPATIAL_ELEMENT,
        IDENTITY
    }

    private record ProfileTransitionSignature(
            ProfileTransitionKind kind,
            PhaseSpaceMapSignature phaseSpaceMap,
            int distance,
            int topologyId,
            String elementSignature
    ) {
        private static ProfileTransitionSignature freeSpace(int distance) {
            return new ProfileTransitionSignature(
                    ProfileTransitionKind.FREE_SPACE,
                    PhaseSpaceMap.freeSpace(distance).signature(),
                    distance,
                    -1,
                    ""
            );
        }

        private static ProfileTransitionSignature identity() {
            return new ProfileTransitionSignature(
                    ProfileTransitionKind.IDENTITY,
                    PhaseSpaceMapSignature.IDENTITY,
                    0,
                    -1,
                    ""
            );
        }

        private ProfileTransitionSignature {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(phaseSpaceMap, "phaseSpaceMap");
            Objects.requireNonNull(elementSignature, "elementSignature");

            if (distance < 0) {
                throw new IllegalArgumentException("Profile transition distance must be non-negative");
            }
        }
    }

    private record TransitionCacheKey(
            ProfileTransitionSignature signature,
            FrequencyKey frequency,
            CoherenceKind coherence,
            BeamProfileKey profile
    ) {
        private TransitionCacheKey {
            Objects.requireNonNull(signature, "signature");
            Objects.requireNonNull(frequency, "frequency");
            Objects.requireNonNull(coherence, "coherence");
            Objects.requireNonNull(profile, "profile");
        }
    }

    private record StateSccGraph(
            List<List<Integer>> sccs,
            int[] sccIdByState,
            List<List<ProfileTransition>> internalTransitionsByScc,
            List<List<ProfileTransition>> outgoingTransitionsByScc,
            int[] inDegrees
    ) {
        private static StateSccGraph build(int stateCount, List<ProfileTransition> transitions) {
            List<List<Integer>> adjacency = new ArrayList<>();

            for (int index = 0; index < stateCount; index++) {
                adjacency.add(new ArrayList<>());
            }

            for (ProfileTransition transition : transitions) {
                adjacency.get(transition.from()).add(transition.to());
            }

            TarjanState tarjan = new TarjanState(stateCount);

            for (int stateId = 0; stateId < stateCount; stateId++) {
                if (tarjan.indices[stateId] == -1) {
                    strongConnect(stateId, adjacency, tarjan);
                }
            }

            List<List<Integer>> sccs = tarjan.components;
            int[] sccIdByState = new int[stateCount];

            for (int sccId = 0; sccId < sccs.size(); sccId++) {
                for (int stateId : sccs.get(sccId)) {
                    sccIdByState[stateId] = sccId;
                }
            }

            List<List<ProfileTransition>> internalTransitionsByScc = new ArrayList<>();
            List<List<ProfileTransition>> outgoingTransitionsByScc = new ArrayList<>();
            int[] inDegrees = new int[sccs.size()];

            for (int sccId = 0; sccId < sccs.size(); sccId++) {
                internalTransitionsByScc.add(new ArrayList<>());
                outgoingTransitionsByScc.add(new ArrayList<>());
            }

            for (ProfileTransition transition : transitions) {
                int fromScc = sccIdByState[transition.from()];
                int toScc = sccIdByState[transition.to()];

                if (fromScc == toScc) {
                    internalTransitionsByScc.get(fromScc).add(transition);
                } else {
                    outgoingTransitionsByScc.get(fromScc).add(transition);
                    inDegrees[toScc]++;
                }
            }

            return new StateSccGraph(sccs, sccIdByState, internalTransitionsByScc, outgoingTransitionsByScc, inDegrees);
        }

        private static void strongConnect(
                int stateId,
                List<List<Integer>> adjacency,
                TarjanState tarjan
        ) {
            tarjan.indices[stateId] = tarjan.nextIndex;
            tarjan.lowLinks[stateId] = tarjan.nextIndex;
            tarjan.nextIndex++;
            tarjan.stack.push(stateId);
            tarjan.onStack.add(stateId);

            for (int next : adjacency.get(stateId)) {
                if (tarjan.indices[next] == -1) {
                    strongConnect(next, adjacency, tarjan);
                    tarjan.lowLinks[stateId] = Math.min(tarjan.lowLinks[stateId], tarjan.lowLinks[next]);
                } else if (tarjan.onStack.contains(next)) {
                    tarjan.lowLinks[stateId] = Math.min(tarjan.lowLinks[stateId], tarjan.indices[next]);
                }
            }

            if (tarjan.lowLinks[stateId] == tarjan.indices[stateId]) {
                List<Integer> component = new ArrayList<>();
                int member;

                do {
                    member = tarjan.stack.pop();
                    tarjan.onStack.remove(member);
                    component.add(member);
                } while (member != stateId);

                component.sort(Integer::compareTo);
                tarjan.components.add(component);
            }
        }
    }

    private static final class TarjanState {
        private final int[] indices;
        private final int[] lowLinks;
        private final ArrayDeque<Integer> stack = new ArrayDeque<>();
        private final Set<Integer> onStack = new HashSet<>();
        private final List<List<Integer>> components = new ArrayList<>();
        private int nextIndex;

        private TarjanState(int size) {
            this.indices = new int[size];
            this.lowLinks = new int[size];

            for (int index = 0; index < size; index++) {
                indices[index] = -1;
                lowLinks[index] = -1;
            }
        }
    }

    private record ExactSolveResult(boolean solved, double[] powers) {
        private static ExactSolveResult solved(double[] powers) {
            return new ExactSolveResult(true, powers);
        }

        private static ExactSolveResult unsolved() {
            return new ExactSolveResult(false, new double[0]);
        }
    }

    private record LinearSolveResult(boolean solved, double[] solution) {
        private static LinearSolveResult solved(double[] solution) {
            return new LinearSolveResult(true, solution);
        }

        private static LinearSolveResult unsolved() {
            return new LinearSolveResult(false, new double[0]);
        }
    }

    private ProfileLanePowerSolver() {
    }
}
