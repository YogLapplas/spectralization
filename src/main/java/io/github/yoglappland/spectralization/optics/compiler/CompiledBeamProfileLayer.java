package io.github.yoglappland.spectralization.optics.compiler;

import io.github.yoglappland.spectralization.optics.BeamEnvelope;
import io.github.yoglappland.spectralization.optics.BeamModel;
import io.github.yoglappland.spectralization.optics.CoherenceKind;
import io.github.yoglappland.spectralization.optics.OpticalPort;
import io.github.yoglappland.spectralization.optics.PlaneWaveComponent;
import io.github.yoglappland.spectralization.optics.geometry.BeamGeometryOps;
import io.github.yoglappland.spectralization.optics.geometry.SpatialModeCoupling;
import io.github.yoglappland.spectralization.optics.geometry.SpatialProfileElement;
import io.github.yoglappland.spectralization.optics.geometry.SpatialTransformContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public record CompiledBeamProfileLayer(
        Map<SpectralPowerLane, Map<PortGraphNode, BeamEnvelope>> envelopesByLane
) {
    public static final CompiledBeamProfileLayer EMPTY = new CompiledBeamProfileLayer(Map.of());
    private static final double DISTANCE_EPSILON = 1.0E-9;

    public CompiledBeamProfileLayer {
        Objects.requireNonNull(envelopesByLane, "envelopesByLane");

        Map<SpectralPowerLane, Map<PortGraphNode, BeamEnvelope>> copied = new HashMap<>();
        for (Map.Entry<SpectralPowerLane, Map<PortGraphNode, BeamEnvelope>> entry : envelopesByLane.entrySet()) {
            copied.put(entry.getKey(), Map.copyOf(entry.getValue()));
        }
        envelopesByLane = Map.copyOf(copied);
    }

    public static CompiledBeamProfileLayer compile(
            Level level,
            CompiledPortGraph graph,
            Collection<BeamProfileSource> sources
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(sources, "sources");

        if (sources.isEmpty() || graph.nodes().isEmpty()) {
            return EMPTY;
        }

        Map<SpectralPowerLane, List<ProfileSeed>> seedsByLane = collectSeeds(graph, sources);
        if (seedsByLane.isEmpty()) {
            return EMPTY;
        }

        Map<PortGraphNode, List<PortGraphEdge>> edgesByFrom = edgesByFrom(graph);
        Set<Integer> feedbackEdgeIds = feedbackEdgeIds(graph);
        Map<SpectralPowerLane, Map<PortGraphNode, BeamEnvelope>> envelopesByLane = new HashMap<>();

        for (Map.Entry<SpectralPowerLane, List<ProfileSeed>> entry : seedsByLane.entrySet()) {
            envelopesByLane.put(
                    entry.getKey(),
                    propagateLane(level, graph, edgesByFrom, feedbackEdgeIds, entry.getKey(), entry.getValue())
            );
        }

        return new CompiledBeamProfileLayer(envelopesByLane);
    }

    public BeamEnvelope envelopeAt(PortGraphNode node, ScalarPowerSolution solution) {
        if (node == null) {
            return BeamEnvelope.DEFAULT_COLLIMATED;
        }

        Objects.requireNonNull(solution, "solution");
        WeightedEnvelope weightedEnvelope = new WeightedEnvelope();

        for (Map.Entry<SpectralPowerLane, Map<PortGraphNode, Double>> entry : solution.powerByLane().entrySet()) {
            double power = entry.getValue().getOrDefault(node, 0.0);

            if (power <= 0.0) {
                continue;
            }

            BeamEnvelope envelope = envelopeAt(entry.getKey(), node);

            if (envelope != null) {
                weightedEnvelope.accept(envelope, power);
            }
        }

        return weightedEnvelope.resultOr(defaultEnvelopeAt(node));
    }

    public BeamEnvelope envelopeAtOrNull(PortGraphNode node, ScalarPowerSolution solution, CoherenceKind coherence) {
        if (node == null || solution == null || coherence == null) {
            return null;
        }

        WeightedEnvelope weightedEnvelope = new WeightedEnvelope();

        for (Map.Entry<SpectralPowerLane, Map<PortGraphNode, Double>> entry : solution.powerByLane().entrySet()) {
            SpectralPowerLane lane = entry.getKey();

            if (lane.coherence() != coherence) {
                continue;
            }

            double power = entry.getValue().getOrDefault(node, 0.0);

            if (power <= 0.0) {
                continue;
            }

            BeamEnvelope envelope = envelopeAt(lane, node);

            if (envelope != null) {
                weightedEnvelope.accept(envelope, power);
            }
        }

        return weightedEnvelope.resultOr(defaultEnvelopeAt(node, coherence));
    }

    private BeamEnvelope envelopeAt(SpectralPowerLane lane, PortGraphNode node) {
        Map<PortGraphNode, BeamEnvelope> envelopesByNode = envelopesByLane.get(lane);

        if (envelopesByNode == null) {
            return null;
        }

        return envelopesByNode.get(node);
    }

    private BeamEnvelope defaultEnvelopeAt(PortGraphNode node) {
        for (Map<PortGraphNode, BeamEnvelope> envelopesByNode : envelopesByLane.values()) {
            BeamEnvelope envelope = envelopesByNode.get(node);

            if (envelope != null) {
                return envelope;
            }
        }

        return BeamEnvelope.DEFAULT_COLLIMATED;
    }

    private BeamEnvelope defaultEnvelopeAt(PortGraphNode node, CoherenceKind coherence) {
        for (Map.Entry<SpectralPowerLane, Map<PortGraphNode, BeamEnvelope>> entry : envelopesByLane.entrySet()) {
            if (entry.getKey().coherence() != coherence) {
                continue;
            }

            BeamEnvelope envelope = entry.getValue().get(node);

            if (envelope != null) {
                return envelope;
            }
        }

        return null;
    }

    private static Map<SpectralPowerLane, List<ProfileSeed>> collectSeeds(
            CompiledPortGraph graph,
            Collection<BeamProfileSource> sources
    ) {
        Set<PortGraphNode> graphNodes = new HashSet<>(graph.nodes());
        Map<SpectralPowerLane, List<ProfileSeed>> seedsByLane = new HashMap<>();
        int order = 0;

        for (BeamProfileSource source : sources) {
            PortGraphNode sourceNode = PortGraphNode.outgoing(new OpticalPort(
                    source.pos(),
                    source.outputBeam().outgoingDirection()
            ));

            if (!graphNodes.contains(sourceNode)) {
                continue;
            }

            for (PlaneWaveComponent component : source.outputBeam().beam().components()) {
                if (component.power() <= 0.0) {
                    continue;
                }

                SpectralPowerLane lane = new SpectralPowerLane(component.frequency(), component.coherence());
                seedsByLane.computeIfAbsent(lane, ignored -> new ArrayList<>())
                        .add(new ProfileSeed(sourceNode, source.outputBeam().beam().envelope(), order++));
            }
        }

        return seedsByLane;
    }

    private static Map<PortGraphNode, BeamEnvelope> propagateLane(
            Level level,
            CompiledPortGraph graph,
            Map<PortGraphNode, List<PortGraphEdge>> edgesByFrom,
            Set<Integer> feedbackEdgeIds,
            SpectralPowerLane lane,
            List<ProfileSeed> seeds
    ) {
        Map<PortGraphNode, BeamEnvelope> envelopesByNode = new HashMap<>();
        Map<PortGraphNode, Double> bestDistanceByNode = new HashMap<>();
        PriorityQueue<ProfileState> pending = new PriorityQueue<>(
                Comparator.comparingDouble(ProfileState::distance)
                        .thenComparingInt(ProfileState::order)
        );

        for (ProfileSeed seed : seeds) {
            double previousDistance = bestDistanceByNode.getOrDefault(seed.node(), Double.POSITIVE_INFINITY);

            if (previousDistance <= 0.0) {
                continue;
            }

            bestDistanceByNode.put(seed.node(), 0.0);
            envelopesByNode.put(seed.node(), seed.envelope());
            pending.add(new ProfileState(seed.node(), seed.envelope(), 0.0, seed.order()));
        }

        while (!pending.isEmpty()) {
            ProfileState state = pending.remove();
            double bestDistance = bestDistanceByNode.getOrDefault(state.node(), Double.POSITIVE_INFINITY);

            if (state.distance() > bestDistance + DISTANCE_EPSILON) {
                continue;
            }

            for (PortGraphEdge edge : edgesByFrom.getOrDefault(state.node(), List.of())) {
                double distance = state.distance() + Math.max(0, edge.distance());
                double previousDistance = bestDistanceByNode.getOrDefault(edge.to(), Double.POSITIVE_INFINITY);

                if (distance + DISTANCE_EPSILON >= previousDistance) {
                    continue;
                }

                BeamEnvelope envelope = transformEnvelope(level, graph, edge, feedbackEdgeIds, lane, state.envelope());
                bestDistanceByNode.put(edge.to(), distance);
                envelopesByNode.put(edge.to(), envelope);
                pending.add(new ProfileState(edge.to(), envelope, distance, state.order()));
            }
        }

        return envelopesByNode;
    }

    private static BeamEnvelope transformEnvelope(
            Level level,
            CompiledPortGraph graph,
            PortGraphEdge edge,
            Set<Integer> feedbackEdgeIds,
            SpectralPowerLane lane,
            BeamEnvelope envelope
    ) {
        if (edge.kind() == PortGraphEdgeKind.PROPAGATION) {
            return BeamGeometryOps.propagate(envelope, edge.distance());
        }

        if (edge.kind() != PortGraphEdgeKind.LOCAL_SCATTERING || !level.isLoaded(edge.from().pos())) {
            return envelope;
        }

        BlockState state = level.getBlockState(edge.from().pos());
        Block block = state.getBlock();

        if (!(block instanceof SpatialProfileElement profileElement)) {
            return envelope;
        }

        SpatialModeCoupling coupling = profileElement.transformSpatialProfile(
                envelope,
                new SpatialTransformContext(
                        level,
                        edge.from().pos(),
                        state,
                        lane.frequency(),
                        lane.coherence(),
                        edge.from().side(),
                        edge.to().side(),
                        edge.distance(),
                        feedbackEdgeIds.contains(edge.id()) || feedbackSccContains(graph, edge)
                )
        );
        double scatter = BeamGeometryOps.clamp01(coupling.orderedEnvelope().scatter() + coupling.strayFraction());
        return coupling.orderedEnvelope().withScatter(scatter);
    }

    private static Map<PortGraphNode, List<PortGraphEdge>> edgesByFrom(CompiledPortGraph graph) {
        Map<PortGraphNode, List<PortGraphEdge>> edgesByFrom = new HashMap<>();

        for (PortGraphEdge edge : graph.edges()) {
            edgesByFrom.computeIfAbsent(edge.from(), ignored -> new ArrayList<>()).add(edge);
        }

        for (List<PortGraphEdge> edges : edgesByFrom.values()) {
            edges.sort(Comparator.comparingInt(PortGraphEdge::id));
        }

        return edgesByFrom;
    }

    private static Set<Integer> feedbackEdgeIds(CompiledPortGraph graph) {
        Set<Integer> edgeIds = new HashSet<>();

        for (PortGraphScc scc : graph.sccs()) {
            if (scc.feedback()) {
                edgeIds.addAll(scc.edgeIds());
            }
        }

        return edgeIds;
    }

    private static boolean feedbackSccContains(CompiledPortGraph graph, PortGraphEdge edge) {
        for (PortGraphScc scc : graph.sccs()) {
            if (scc.feedback() && scc.nodes().contains(edge.from()) && scc.nodes().contains(edge.to())) {
                return true;
            }
        }

        return false;
    }

    private record ProfileSeed(PortGraphNode node, BeamEnvelope envelope, int order) {
        private ProfileSeed {
            Objects.requireNonNull(node, "node");
            Objects.requireNonNull(envelope, "envelope");
        }
    }

    private record ProfileState(PortGraphNode node, BeamEnvelope envelope, double distance, int order) {
        private ProfileState {
            Objects.requireNonNull(node, "node");
            Objects.requireNonNull(envelope, "envelope");

            if (!Double.isFinite(distance) || distance < 0.0) {
                throw new IllegalArgumentException("Beam profile distance must be finite and non-negative");
            }
        }
    }

    private static final class WeightedEnvelope {
        private double totalWeight;
        private double radiusMoment;
        private double waistRadiusMoment;
        private double divergenceMoment;
        private double focusDistance;
        private double beamQuality;
        private double apertureFill;
        private double scatter;
        private double dominantWeight = -1.0;
        private BeamModel dominantModel = BeamModel.COLLIMATED;
        private int dominantModeM;
        private int dominantModeN;

        private void accept(BeamEnvelope envelope, double weight) {
            if (weight <= 0.0) {
                return;
            }

            totalWeight += weight;
            radiusMoment += weight * envelope.radius() * envelope.radius();
            waistRadiusMoment += weight * envelope.waistRadius() * envelope.waistRadius();
            divergenceMoment += weight * envelope.divergence() * envelope.divergence();
            focusDistance += weight * envelope.focusDistance();
            beamQuality += weight * envelope.beamQuality();
            apertureFill += weight * envelope.apertureFill();
            scatter += weight * envelope.scatter();

            if (weight > dominantWeight) {
                dominantWeight = weight;
                dominantModel = envelope.model();
                dominantModeM = envelope.modeM();
                dominantModeN = envelope.modeN();
            }
        }

        private BeamEnvelope resultOr(BeamEnvelope fallback) {
            if (totalWeight <= 0.0) {
                return fallback;
            }

            return new BeamEnvelope(
                    dominantModel,
                    Math.sqrt(radiusMoment / totalWeight),
                    Math.sqrt(waistRadiusMoment / totalWeight),
                    Math.sqrt(divergenceMoment / totalWeight),
                    focusDistance / totalWeight,
                    Math.max(1.0, beamQuality / totalWeight),
                    BeamGeometryOps.clamp01(apertureFill / totalWeight),
                    BeamGeometryOps.clamp01(scatter / totalWeight),
                    dominantModeM,
                    dominantModeN
            );
        }
    }
}
