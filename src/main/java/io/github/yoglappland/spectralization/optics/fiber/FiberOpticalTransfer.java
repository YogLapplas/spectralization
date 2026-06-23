package io.github.yoglappland.spectralization.optics.fiber;

import io.github.yoglappland.spectralization.block.FiberOpticInterfaceBlock;
import io.github.yoglappland.spectralization.optics.BeamEnvelope;
import io.github.yoglappland.spectralization.optics.geometry.BeamGeometryOps;
import io.github.yoglappland.spectralization.optics.geometry.BeamProfileKey;
import io.github.yoglappland.spectralization.optics.geometry.BeamProfileShape;
import io.github.yoglappland.spectralization.optics.geometry.BeamProfileTransfer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class FiberOpticalTransfer {
    private static final double MIN_GUIDED_GAIN = 1.0E-6;
    private static final Comparator<BlockPos> POS_ORDER = Comparator
            .comparingInt((BlockPos pos) -> pos.getX())
            .thenComparingInt(pos -> pos.getY())
            .thenComparingInt(pos -> pos.getZ());

    public static List<OutputPort> remoteOutputPorts(
            ServerLevel level,
            BlockPos inputPos,
            Direction incomingDirection
    ) {
        return remoteOutputPorts(level, inputPos, incomingDirection, BeamEnvelope.DEFAULT_COLLIMATED);
    }

    public static List<OutputPort> remoteOutputPorts(
            ServerLevel level,
            BlockPos inputPos,
            Direction incomingDirection,
            BeamEnvelope inputEnvelope
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(inputPos, "inputPos");
        Objects.requireNonNull(incomingDirection, "incomingDirection");
        Objects.requireNonNull(inputEnvelope, "inputEnvelope");

        if (!level.isLoaded(inputPos)) {
            return List.of();
        }

        BlockState inputState = level.getBlockState(inputPos);

        if (!(inputState.getBlock() instanceof FiberOpticInterfaceBlock)
                || inputState.getValue(FiberOpticInterfaceBlock.FACING) != incomingDirection) {
            return List.of();
        }

        FiberNetworkSnapshot snapshot = FiberNetworkIndex.snapshot(level);
        FiberNode inputNode = snapshot.nodeAt(inputPos).orElse(null);

        if (inputNode == null || inputNode.kind() != FiberNodeKind.INTERFACE) {
            return List.of();
        }

        List<RouteOutput> routeOutputs = directEndpointRouteOutputs(snapshot, inputPos);

        if (routeOutputs.isEmpty()) {
            return List.of();
        }

        Map<BlockPos, OutputAccumulator> outputAccumulators = new HashMap<>();

        for (RouteOutput routeOutput : routeOutputs) {
            FiberNode outputNode = snapshot.nodeAt(routeOutput.outputPos()).orElse(null);

            if (outputNode == null || outputNode.kind() != FiberNodeKind.INTERFACE || !level.isLoaded(routeOutput.outputPos())) {
                continue;
            }

            BlockState outputState = level.getBlockState(routeOutput.outputPos());

            if (!(outputState.getBlock() instanceof FiberOpticInterfaceBlock)) {
                continue;
            }

            double guidedGain = routeGain(snapshot, routeOutput.route());

            if (guidedGain <= MIN_GUIDED_GAIN) {
                continue;
            }

            outputAccumulators.computeIfAbsent(
                    routeOutput.outputPos(),
                    ignored -> new OutputAccumulator(
                            routeOutput.outputPos(),
                            outputState.getValue(FiberOpticInterfaceBlock.FACING)
                    )
            ).accept(routeOutput.route(), guidedGain);
        }

        if (outputAccumulators.isEmpty()) {
            return List.of();
        }

        double splitGain = 1.0D / outputAccumulators.size();
        return outputAccumulators.values().stream()
                .sorted(Comparator.comparing(OutputAccumulator::pos, POS_ORDER))
                .map(output -> output.toOutputPort(splitGain))
                .toList();
    }

    public static List<FiberRoute> directEndpointRoutes(FiberNetworkSnapshot snapshot, BlockPos start, BlockPos end) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");

        if (start.equals(end)) {
            return List.of();
        }

        List<FiberRoute> routes = new ArrayList<>();

        for (FiberCompiledConnection compiledConnection : snapshot.connections()) {
            if (!compiledConnection.valid()) {
                continue;
            }

            FiberConnection connection = compiledConnection.connection();

            if (connection.connects(start, end)) {
                routes.add(connection.route());
            }
        }

        return routes.isEmpty() ? List.of() : List.copyOf(routes);
    }

    public static Optional<FiberRoute> routeBetween(FiberNetworkSnapshot snapshot, BlockPos start, BlockPos end) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");

        if (start.equals(end) || !snapshot.hasNode(start) || !snapshot.hasNode(end)) {
            return Optional.empty();
        }

        Map<BlockPos, List<RouteEdge>> adjacency = weightedAdjacency(snapshot);
        Map<BlockPos, Double> bestDistance = new HashMap<>();
        Map<BlockPos, BlockPos> previous = new HashMap<>();
        java.util.PriorityQueue<RouteState> pending = new java.util.PriorityQueue<>(
                Comparator.comparingDouble(RouteState::distance)
        );

        bestDistance.put(start, 0.0D);
        pending.add(new RouteState(start, 0.0D));

        while (!pending.isEmpty()) {
            RouteState state = pending.remove();
            double best = bestDistance.getOrDefault(state.pos(), Double.POSITIVE_INFINITY);

            if (state.distance() > best + 1.0E-9D) {
                continue;
            }

            if (state.pos().equals(end)) {
                return Optional.of(FiberRoute.fromNodes(reconstructPath(previous, start, end)));
            }

            for (RouteEdge edge : adjacency.getOrDefault(state.pos(), List.of())) {
                BlockPos next = edge.other(state.pos());
                double distance = state.distance() + edge.length();
                double previousBest = bestDistance.getOrDefault(next, Double.POSITIVE_INFINITY);

                if (distance + 1.0E-9D >= previousBest) {
                    continue;
                }

                bestDistance.put(next, distance);
                previous.put(next, state.pos());
                pending.add(new RouteState(next, distance));
            }
        }

        return Optional.empty();
    }

    private static List<RouteOutput> directEndpointRouteOutputs(FiberNetworkSnapshot snapshot, BlockPos inputPos) {
        List<RouteOutput> outputs = new ArrayList<>();

        for (FiberCompiledConnection compiledConnection : snapshot.connections()) {
            if (!compiledConnection.valid()) {
                continue;
            }

            FiberConnection connection = compiledConnection.connection();
            BlockPos outputPos = null;

            if (connection.endpointA().equals(inputPos)) {
                outputPos = connection.endpointB();
            } else if (connection.endpointB().equals(inputPos)) {
                outputPos = connection.endpointA();
            }

            if (outputPos != null) {
                outputs.add(new RouteOutput(outputPos, connection.route()));
            }
        }

        if (outputs.isEmpty()) {
            return List.of();
        }

        outputs.sort(Comparator.comparing(RouteOutput::outputPos, POS_ORDER));
        return List.copyOf(outputs);
    }

    private static double guidedGain(FiberNetworkSnapshot snapshot, FiberRoute route, BeamEnvelope inputEnvelope) {
        RouteProfile profile = limitingRouteProfile(snapshot, route);
        double spotGain = spotCoupling(inputEnvelope, profile.coreRadius());
        double angularGain = angularCoupling(inputEnvelope, profile.numericalAperture());
        return BeamGeometryOps.clamp01(spotGain * angularGain * routeGain(profile, route));
    }

    private static double routeGain(FiberNetworkSnapshot snapshot, FiberRoute route) {
        return routeGain(limitingRouteProfile(snapshot, route), route);
    }

    private static double routeGain(RouteProfile profile, FiberRoute route) {
        double lengthGain = Math.pow(profile.transmissionPerBlock(), route.totalLength());
        double bendGain = bendTransmission(route, profile.bendTransmissionPerRightAngle());
        return BeamGeometryOps.clamp01(lengthGain * bendGain);
    }

    private static RouteProfile limitingRouteProfile(FiberNetworkSnapshot snapshot, FiberRoute route) {
        double coreRadius = Double.POSITIVE_INFINITY;
        double numericalAperture = Double.POSITIVE_INFINITY;
        double transmissionPerBlock = 1.0D;
        double bendTransmissionPerRightAngle = 1.0D;

        for (BlockPos nodePos : route.nodes()) {
            FiberNodeProfile profile = snapshot.nodeAt(nodePos)
                    .map(FiberNode::profile)
                    .orElse(FiberNodeProfile.BASIC_RELAY);
            coreRadius = Math.min(coreRadius, profile.coreRadius());
            numericalAperture = Math.min(numericalAperture, profile.numericalAperture());
            transmissionPerBlock = Math.min(transmissionPerBlock, profile.transmissionPerBlock());
            bendTransmissionPerRightAngle = Math.min(
                    bendTransmissionPerRightAngle,
                    profile.bendTransmissionPerRightAngle()
            );
        }

        if (!Double.isFinite(coreRadius) || !Double.isFinite(numericalAperture)) {
            return RouteProfile.BASIC;
        }

        return new RouteProfile(coreRadius, numericalAperture, transmissionPerBlock, bendTransmissionPerRightAngle);
    }

    private static double spotCoupling(BeamEnvelope envelope, double coreRadius) {
        double radius = Math.max(BeamEnvelope.DEFAULT_MIN_WAIST_RADIUS, envelope.radius());

        if (radius <= coreRadius) {
            return 1.0D;
        }

        double ratio = coreRadius / radius;
        return BeamGeometryOps.clamp01(ratio * ratio);
    }

    private static double angularCoupling(BeamEnvelope envelope, double numericalAperture) {
        double angularSpread = envelope.divergence() * Math.sqrt(Math.max(1.0D, envelope.beamQuality()))
                + envelope.scatter() * numericalAperture * 2.0D;

        if (angularSpread <= numericalAperture) {
            return 1.0D;
        }

        double ratio = numericalAperture / angularSpread;
        return BeamGeometryOps.clamp01(ratio * ratio);
    }

    public static BeamProfileTransfer profileTransferForEdge(
            ServerLevel level,
            BlockPos inputPos,
            Direction incomingDirection,
            BlockPos outputPos,
            Direction outputDirection,
            BeamProfileKey inputProfile
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(inputPos, "inputPos");
        Objects.requireNonNull(incomingDirection, "incomingDirection");
        Objects.requireNonNull(outputPos, "outputPos");
        Objects.requireNonNull(outputDirection, "outputDirection");
        Objects.requireNonNull(inputProfile, "inputProfile");

        if (!level.isLoaded(inputPos) || !level.isLoaded(outputPos)) {
            return BeamProfileTransfer.of(inputProfile, 0.0D);
        }

        BlockState inputState = level.getBlockState(inputPos);
        BlockState outputState = level.getBlockState(outputPos);

        if (!(inputState.getBlock() instanceof FiberOpticInterfaceBlock)
                || inputState.getValue(FiberOpticInterfaceBlock.FACING) != incomingDirection
                || !(outputState.getBlock() instanceof FiberOpticInterfaceBlock)
                || outputState.getValue(FiberOpticInterfaceBlock.FACING) != outputDirection) {
            return BeamProfileTransfer.of(inputProfile, 0.0D);
        }

        FiberNetworkSnapshot snapshot = FiberNetworkIndex.snapshot(level);
        List<FiberRoute> routes = directEndpointRoutes(snapshot, inputPos, outputPos);

        if (routes.isEmpty()) {
            return BeamProfileTransfer.of(inputProfile, 0.0D);
        }

        BeamProfileShape shape = inputProfile.toShape();
        double routeGainSum = 0.0D;
        double acceptedGainSum = 0.0D;
        RouteProfile outputProfile = null;

        for (FiberRoute route : routes) {
            RouteProfile routeProfile = limitingRouteProfile(snapshot, route);
            double routeGain = routeGain(routeProfile, route);

            if (routeGain <= 0.0D) {
                continue;
            }

            double acceptance = profileAcceptance(shape, routeProfile);
            routeGainSum += routeGain;
            acceptedGainSum += routeGain * acceptance;
            outputProfile = outputProfile == null ? routeProfile : outputProfile.limiting(routeProfile);
        }

        if (routeGainSum <= 0.0D || outputProfile == null) {
            return BeamProfileTransfer.of(inputProfile, 0.0D);
        }

        double acceptanceGain = BeamGeometryOps.clamp01(acceptedGainSum / routeGainSum);
        BeamProfileKey outputKey = shape
                .guidedOutput(outputProfile.coreRadius(), outputProfile.numericalAperture())
                .toKey();
        return BeamProfileTransfer.of(outputKey, acceptanceGain);
    }

    private static double profileAcceptance(BeamProfileShape shape, RouteProfile profile) {
        double radius = Math.max(BeamEnvelope.DEFAULT_MIN_WAIST_RADIUS, Math.sqrt(Math.max(0.0D, shape.r2())));
        double spotGain = radius <= profile.coreRadius()
                ? 1.0D
                : BeamGeometryOps.clamp01((profile.coreRadius() / radius) * (profile.coreRadius() / radius));
        double angularSpread = Math.sqrt(Math.max(0.0D, shape.theta2())) * Math.sqrt(Math.max(1.0D, shape.quality()))
                + shape.scatter() * profile.numericalAperture() * 2.0D;
        double angularGain;

        if (angularSpread <= profile.numericalAperture()) {
            angularGain = 1.0D;
        } else {
            double ratio = profile.numericalAperture() / angularSpread;
            angularGain = BeamGeometryOps.clamp01(ratio * ratio);
        }

        return BeamGeometryOps.clamp01(spotGain * angularGain);
    }

    private static double bendTransmission(FiberRoute route, double bendTransmissionPerRightAngle) {
        if (route.nodes().size() < 3) {
            return 1.0D;
        }

        double rightAngleUnits = 0.0D;

        for (int index = 1; index < route.nodes().size() - 1; index++) {
            Vec3 incoming = vectorBetween(route.nodes().get(index - 1), route.nodes().get(index));
            Vec3 outgoing = vectorBetween(route.nodes().get(index), route.nodes().get(index + 1));

            if (incoming.lengthSqr() <= 1.0E-9D || outgoing.lengthSqr() <= 1.0E-9D) {
                continue;
            }

            double dot = incoming.normalize().dot(outgoing.normalize());
            double angle = Math.acos(Math.max(-1.0D, Math.min(1.0D, dot)));
            rightAngleUnits += angle / (Math.PI * 0.5D);
        }

        if (rightAngleUnits <= 0.0D) {
            return 1.0D;
        }

        return Math.pow(bendTransmissionPerRightAngle, rightAngleUnits);
    }

    private static Vec3 vectorBetween(BlockPos from, BlockPos to) {
        return new Vec3(to.getX() - from.getX(), to.getY() - from.getY(), to.getZ() - from.getZ());
    }

    private static Map<BlockPos, List<RouteEdge>> weightedAdjacency(FiberNetworkSnapshot snapshot) {
        Map<BlockPos, List<RouteEdge>> adjacency = new HashMap<>();

        for (FiberCompiledConnection compiledConnection : snapshot.connections()) {
            if (!compiledConnection.valid()) {
                continue;
            }

            for (FiberSegment segment : compiledConnection.connection().route().segments()) {
                RouteEdge edge = new RouteEdge(segment.from(), segment.to(), segment.length());
                adjacency.computeIfAbsent(segment.from(), ignored -> new ArrayList<>()).add(edge);
                adjacency.computeIfAbsent(segment.to(), ignored -> new ArrayList<>()).add(edge);
            }
        }

        return adjacency;
    }

    private static List<BlockPos> reconstructPath(Map<BlockPos, BlockPos> previous, BlockPos start, BlockPos end) {
        List<BlockPos> reversed = new ArrayList<>();
        BlockPos cursor = end;
        reversed.add(cursor);

        while (!cursor.equals(start)) {
            cursor = previous.get(cursor);

            if (cursor == null) {
                return List.of();
            }

            reversed.add(cursor);
        }

        List<BlockPos> path = new ArrayList<>(reversed.size());

        for (int index = reversed.size() - 1; index >= 0; index--) {
            path.add(reversed.get(index));
        }

        return path;
    }

    public record OutputPort(BlockPos pos, Direction direction, double gain, double guidedGain, double routeLength) {
        public OutputPort(BlockPos pos, Direction direction) {
            this(pos, direction, 1.0D, 1.0D, 0.0D);
        }

        public OutputPort(BlockPos pos, Direction direction, double gain) {
            this(pos, direction, gain, gain, 0.0D);
        }

        public OutputPort {
            Objects.requireNonNull(pos, "pos");
            Objects.requireNonNull(direction, "direction");
            pos = pos.immutable();

            if (!Double.isFinite(gain) || gain <= 0.0D || gain > 1.0D) {
                throw new IllegalArgumentException("Fiber output gain must be finite and between 0 and 1");
            }

            if (!Double.isFinite(guidedGain) || guidedGain <= 0.0D || guidedGain > 1.0D) {
                throw new IllegalArgumentException("Fiber guided gain must be finite and between 0 and 1");
            }

            if (!Double.isFinite(routeLength) || routeLength < 0.0D) {
                throw new IllegalArgumentException("Fiber route length must be finite and non-negative");
            }
        }
    }

    private record RouteOutput(BlockPos outputPos, FiberRoute route) {
        private RouteOutput {
            Objects.requireNonNull(outputPos, "outputPos");
            Objects.requireNonNull(route, "route");
            outputPos = outputPos.immutable();
        }
    }

    private record RouteProfile(
            double coreRadius,
            double numericalAperture,
            double transmissionPerBlock,
            double bendTransmissionPerRightAngle
    ) {
        private static final RouteProfile BASIC = new RouteProfile(
                FiberNodeProfile.BASIC_INTERFACE.coreRadius(),
                FiberNodeProfile.BASIC_INTERFACE.numericalAperture(),
                FiberNodeProfile.BASIC_INTERFACE.transmissionPerBlock(),
                FiberNodeProfile.BASIC_INTERFACE.bendTransmissionPerRightAngle()
        );

        private RouteProfile limiting(RouteProfile other) {
            return new RouteProfile(
                    Math.min(coreRadius, other.coreRadius),
                    Math.min(numericalAperture, other.numericalAperture),
                    Math.min(transmissionPerBlock, other.transmissionPerBlock),
                    Math.min(bendTransmissionPerRightAngle, other.bendTransmissionPerRightAngle)
            );
        }
    }

    private static final class OutputAccumulator {
        private final BlockPos pos;
        private final Direction direction;
        private double guidedGainSum;
        private double longestRoute;

        private OutputAccumulator(BlockPos pos, Direction direction) {
            this.pos = pos.immutable();
            this.direction = direction;
        }

        private BlockPos pos() {
            return pos;
        }

        private void accept(FiberRoute route, double guidedGain) {
            guidedGainSum += guidedGain;
            longestRoute = Math.max(longestRoute, route.totalLength());
        }

        private OutputPort toOutputPort(double splitGain) {
            double cappedGuidedGain = BeamGeometryOps.clamp01(guidedGainSum);
            return new OutputPort(pos, direction, splitGain * cappedGuidedGain, cappedGuidedGain, longestRoute);
        }
    }

    private record RouteEdge(BlockPos from, BlockPos to, double length) {
        private RouteEdge {
            from = from.immutable();
            to = to.immutable();
        }

        private BlockPos other(BlockPos pos) {
            if (from.equals(pos)) {
                return to;
            }

            if (to.equals(pos)) {
                return from;
            }

            throw new IllegalArgumentException("Position is not on this fiber route edge");
        }
    }

    private record RouteState(BlockPos pos, double distance) {
    }

    private FiberOpticalTransfer() {
    }
}
