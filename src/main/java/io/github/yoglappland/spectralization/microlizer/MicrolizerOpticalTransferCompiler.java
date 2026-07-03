package io.github.yoglappland.spectralization.microlizer;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.optics.BeamEnvelope;
import io.github.yoglappland.spectralization.optics.BeamPacket;
import io.github.yoglappland.spectralization.optics.CoherenceKind;
import io.github.yoglappland.spectralization.optics.FrequencyKey;
import io.github.yoglappland.spectralization.optics.IntrinsicOpticalSources;
import io.github.yoglappland.spectralization.optics.OpticalMaterialProfiles;
import io.github.yoglappland.spectralization.optics.OpticalNetworkCompiler;
import io.github.yoglappland.spectralization.optics.OpticalPropagationLoss;
import io.github.yoglappland.spectralization.optics.OpticalResult;
import io.github.yoglappland.spectralization.optics.OutputBeam;
import io.github.yoglappland.spectralization.optics.PlaneWaveComponent;
import io.github.yoglappland.spectralization.optics.fiber.FiberOpticalTransfer;
import io.github.yoglappland.spectralization.optics.geometry.BeamProfileKey;
import io.github.yoglappland.spectralization.optics.geometry.BeamProfileTransfer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

public final class MicrolizerOpticalTransferCompiler {
    private static final double MIN_POWER = 1.0E-4D;
    private static final int MAX_STATES = 4096;
    private static final BeamPacket SAMPLE_BEAM = BeamPacket.single(
            new PlaneWaveComponent(FrequencyKey.DEBUG_VISIBLE, 1.0D, Direction.NORTH, CoherenceKind.COHERENT),
            BeamEnvelope.DEFAULT_COLLIMATED
    );
    private static final Comparator<MicrolizedMachineItemData.Transfer> TRANSFER_ORDER = Comparator
            .comparingInt((MicrolizedMachineItemData.Transfer transfer) -> transfer.fromFace().ordinal())
            .thenComparingInt(transfer -> transfer.toFace().ordinal());
    private static final Comparator<OutputBeam> OUTPUT_ORDER = Comparator
            .comparingInt((OutputBeam output) -> output.outgoingDirection().ordinal());

    public static List<MicrolizedMachineItemData.Transfer> compile(
            ServerLevel level,
            BlockPos frameMin,
            BlockPos frameMax,
            List<MicrolizedMachineItemData.BoundaryPort> ports
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(frameMin, "frameMin");
        Objects.requireNonNull(frameMax, "frameMax");
        Objects.requireNonNull(ports, "ports");

        if (ports.isEmpty()) {
            return List.of();
        }

        Map<BlockPos, MicrolizedMachineItemData.BoundaryPort> portsByPos = indexPorts(ports);
        Map<TransferKey, Double> gainByTransfer = new LinkedHashMap<>();

        for (MicrolizedMachineItemData.BoundaryPort source : ports) {
            traceFrom(level, frameMin, frameMax, portsByPos, source, gainByTransfer);
        }

        List<MicrolizedMachineItemData.Transfer> transfers = new ArrayList<>();
        for (Map.Entry<TransferKey, Double> entry : gainByTransfer.entrySet()) {
            if (entry.getValue() > MIN_POWER) {
                transfers.add(new MicrolizedMachineItemData.Transfer(
                        entry.getKey().fromFace(),
                        entry.getKey().toFace(),
                        entry.getValue()
                ));
            }
        }

        transfers.sort(TRANSFER_ORDER);
        return List.copyOf(transfers);
    }

    public static List<OutputBeam> compileSources(
            ServerLevel level,
            BlockPos frameMin,
            BlockPos frameMax,
            BlockPos workMin,
            BlockPos workMax,
            List<MicrolizedMachineItemData.BoundaryPort> ports
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(frameMin, "frameMin");
        Objects.requireNonNull(frameMax, "frameMax");
        Objects.requireNonNull(workMin, "workMin");
        Objects.requireNonNull(workMax, "workMax");
        Objects.requireNonNull(ports, "ports");

        if (ports.isEmpty()) {
            return List.of();
        }

        Map<BlockPos, MicrolizedMachineItemData.BoundaryPort> portsByPos = indexPorts(ports);
        Map<Direction, List<PlaneWaveComponent>> componentsByDirection = new EnumMap<>(Direction.class);
        Map<Direction, BeamEnvelope> envelopeByDirection = new EnumMap<>(Direction.class);

        for (BlockPos mutablePos : BlockPos.betweenClosed(workMin, workMax)) {
            BlockPos sourcePos = mutablePos.immutable();

            if (!level.isLoaded(sourcePos)) {
                continue;
            }

            BlockState sourceState = level.getBlockState(sourcePos);
            if (!IntrinsicOpticalSources.isSource(sourceState)) {
                continue;
            }

            for (OutputBeam output : IntrinsicOpticalSources.outputBeams(sourceState, level, sourcePos)) {
                if (output.beam().isEmpty()) {
                    continue;
                }

                Direction direction = output.outgoingDirection();
                traceBeam(
                        level,
                        frameMin,
                        frameMax,
                        portsByPos,
                        sourcePos.relative(direction),
                        direction,
                        output.beam().withDirection(direction),
                        (face, beam) -> collectBoundaryOutput(
                                componentsByDirection,
                                envelopeByDirection,
                                face,
                                beam
                        )
                );
            }
        }

        List<OutputBeam> outputs = new ArrayList<>();
        for (Map.Entry<Direction, List<PlaneWaveComponent>> entry : componentsByDirection.entrySet()) {
            List<PlaneWaveComponent> components = normalizeComponents(entry.getValue());

            if (components.isEmpty()) {
                continue;
            }

            Direction direction = entry.getKey();
            BeamEnvelope envelope = envelopeByDirection.getOrDefault(direction, BeamEnvelope.DEFAULT_COLLIMATED);
            outputs.add(new OutputBeam(direction, new BeamPacket(components, envelope).withDirection(direction)));
        }

        outputs.sort(OUTPUT_ORDER);
        return List.copyOf(outputs);
    }

    private static void traceFrom(
            ServerLevel level,
            BlockPos frameMin,
            BlockPos frameMax,
            Map<BlockPos, MicrolizedMachineItemData.BoundaryPort> portsByPos,
            MicrolizedMachineItemData.BoundaryPort source,
        Map<TransferKey, Double> gainByTransfer
    ) {
        Direction inward = source.face().getOpposite();
        traceBeam(
                level,
                frameMin,
                frameMax,
                portsByPos,
                source.pos().relative(inward),
                inward,
                SAMPLE_BEAM.withDirection(inward),
                (face, beam) -> gainByTransfer.merge(
                        new TransferKey(source.face(), face),
                        beam.totalPower(),
                        Double::sum
                )
        );
    }

    private static void traceBeam(
            ServerLevel level,
            BlockPos frameMin,
            BlockPos frameMax,
            Map<BlockPos, MicrolizedMachineItemData.BoundaryPort> portsByPos,
            BlockPos startPos,
            Direction startDirection,
            BeamPacket startBeam,
            BoundaryHitConsumer boundaryHitConsumer
    ) {
        ArrayDeque<TravelState> pending = new ArrayDeque<>();
        Map<StateKey, Double> bestPowerByState = new HashMap<>();
        enqueue(pending, startPos, startDirection, startBeam.withDirection(startDirection));
        int processedStates = 0;

        while (!pending.isEmpty() && processedStates < MAX_STATES) {
            TravelState state = pending.removeFirst();
            processedStates++;

            if (!inside(frameMin, frameMax, state.pos()) || !level.isLoaded(state.pos())) {
                continue;
            }

            BeamPacket beam = state.beam().withDirection(state.direction());
            beam = beam.scalePower(OpticalPropagationLoss.factor(level, state.pos(), beam));

            if (beam.totalPower() < MIN_POWER) {
                continue;
            }

            MicrolizedMachineItemData.BoundaryPort target = portsByPos.get(state.pos());
            if (target != null) {
                if (state.direction() == target.face()) {
                    boundaryHitConsumer.accept(target.face(), beam.withDirection(target.face()));
                }
                continue;
            }

            if (onFrameShell(frameMin, frameMax, state.pos())) {
                continue;
            }

            StateKey stateKey = new StateKey(state.pos(), state.direction());
            double previousBestPower = bestPowerByState.getOrDefault(stateKey, 0.0D);
            if (beam.totalPower() <= previousBestPower + 1.0E-9D) {
                continue;
            }
            bestPowerByState.put(stateKey, beam.totalPower());

            BlockState blockState = level.getBlockState(state.pos());
            if (OpticalMaterialProfiles.isAirLike(blockState)) {
                enqueue(pending, state.pos().relative(state.direction()), state.direction(), beam);
                continue;
            }

            Direction incomingDirection = state.direction().getOpposite();
            OpticalResult result = OpticalNetworkCompiler.compile(level, state.pos(), blockState)
                    .scatterWithoutEffects(beam, incomingDirection);

            for (OutputBeam output : result.outputs()) {
                Direction outputDirection = output.outgoingDirection();
                BeamPacket outputBeam = output.beam().withDirection(outputDirection);
                outputBeam = applyRubyCompactGain(
                        level,
                        state.pos(),
                        blockState,
                        incomingDirection,
                        outputDirection,
                        outputBeam
                );
                enqueue(
                        pending,
                        state.pos().relative(outputDirection),
                        outputDirection,
                        outputBeam
                );
            }

            enqueueFiberRemoteOutputs(level, frameMin, frameMax, pending, state.pos(), incomingDirection, beam);
        }

        if (!pending.isEmpty()) {
            Spectralization.LOGGER.warn(
                    "Microlizer optical trace reached state limit in {}: frame {} -> {}, start {} toward {}, pending state(s) {}",
                    level.dimension().location(),
                    frameMin,
                    frameMax,
                    startPos,
                    startDirection,
                    pending.size()
            );
        }
    }

    private static void enqueueFiberRemoteOutputs(
            ServerLevel level,
            BlockPos frameMin,
            BlockPos frameMax,
            ArrayDeque<TravelState> pending,
            BlockPos inputPos,
            Direction incomingDirection,
            BeamPacket inputBeam
    ) {
        for (FiberOpticalTransfer.OutputPort outputPort :
                FiberOpticalTransfer.remoteOutputPorts(level, inputPos, incomingDirection)) {
            if (!inside(frameMin, frameMax, outputPort.pos())) {
                continue;
            }

            Direction outputDirection = outputPort.direction();
            BeamProfileTransfer transfer = FiberOpticalTransfer.profileTransferForEdge(
                    level,
                    inputPos,
                    incomingDirection,
                    outputPort.pos(),
                    outputDirection,
                    BeamProfileKey.fromEnvelope(inputBeam.envelope())
            );
            BeamPacket outputBeam = inputBeam
                    .withDirection(outputDirection)
                    .withEnvelope(transfer.outputProfile().toEnvelope())
                    .scalePower(outputPort.gain() * transfer.gain());
            enqueue(pending, outputPort.pos().relative(outputDirection), outputDirection, outputBeam);
        }
    }

    private static BeamPacket applyRubyCompactGain(
            ServerLevel level,
            BlockPos pos,
            BlockState blockState,
            Direction incomingDirection,
            Direction outputDirection,
            BeamPacket outputBeam
    ) {
        if (!blockState.is(Spectralization.RUBY_BLOCK.get()) || outputDirection != incomingDirection.getOpposite()) {
            return outputBeam;
        }

        List<PlaneWaveComponent> components = new ArrayList<>();
        boolean changed = false;

        for (PlaneWaveComponent component : outputBeam.components()) {
            double power = component.power();

            if (component.coherence().supportsResonance()) {
                double gain = OpticalMaterialProfiles.scheduledCoherentBaseGainFor(
                        level,
                        pos,
                        blockState,
                        component.frequency()
                );

                if (gain != 1.0D) {
                    double passivePower = power;
                    double extraOutput = OpticalMaterialProfiles.saturatedCoherentExtraOutputFor(
                            level,
                            pos,
                            blockState,
                            component.frequency()
                    );
                    double unsaturatedPower = passivePower * gain;
                    power = extraOutput > 0.0D
                            ? Math.min(unsaturatedPower, passivePower + extraOutput)
                            : unsaturatedPower;
                    changed = true;
                }
            }

            if (power > 0.0D) {
                components.add(component.withPower(power).withDirection(outputDirection));
            }
        }

        if (!changed) {
            return outputBeam;
        }

        return new BeamPacket(normalizeComponents(components), outputBeam.envelope());
    }

    private static void collectBoundaryOutput(
            Map<Direction, List<PlaneWaveComponent>> componentsByDirection,
            Map<Direction, BeamEnvelope> envelopeByDirection,
            Direction direction,
            BeamPacket beam
    ) {
        if (beam.isEmpty()) {
            return;
        }

        envelopeByDirection.putIfAbsent(direction, beam.envelope());
        List<PlaneWaveComponent> components = componentsByDirection.computeIfAbsent(direction, ignored -> new ArrayList<>());

        for (PlaneWaveComponent component : beam.components()) {
            if (component.power() > 0.0D) {
                components.add(component.withDirection(direction));
            }
        }
    }

    private static List<PlaneWaveComponent> normalizeComponents(Collection<PlaneWaveComponent> rawComponents) {
        Map<ComponentKey, Double> powerByKey = new LinkedHashMap<>();

        for (PlaneWaveComponent component : rawComponents) {
            if (component.power() <= 0.0D) {
                continue;
            }

            ComponentKey key = new ComponentKey(component.frequency(), component.direction(), component.coherence());
            powerByKey.merge(key, component.power(), Double::sum);
        }

        return powerByKey.entrySet().stream()
                .map(entry -> entry.getKey().component(entry.getValue()))
                .sorted(Comparator.comparingDouble(PlaneWaveComponent::power).reversed())
                .limit(BeamPacket.MAX_COMPONENTS)
                .toList();
    }

    private static Map<BlockPos, MicrolizedMachineItemData.BoundaryPort> indexPorts(
            List<MicrolizedMachineItemData.BoundaryPort> ports
    ) {
        Map<BlockPos, MicrolizedMachineItemData.BoundaryPort> portsByPos = new HashMap<>();

        for (MicrolizedMachineItemData.BoundaryPort port : ports) {
            portsByPos.putIfAbsent(port.pos(), port);
        }

        return portsByPos;
    }

    private static void enqueue(
            ArrayDeque<TravelState> pending,
            BlockPos pos,
            Direction direction,
            BeamPacket beam
    ) {
        if (!beam.isEmpty() && beam.totalPower() >= MIN_POWER) {
            pending.addLast(new TravelState(pos.immutable(), direction, beam));
        }
    }

    private static boolean inside(BlockPos frameMin, BlockPos frameMax, BlockPos pos) {
        return pos.getX() >= frameMin.getX()
                && pos.getX() <= frameMax.getX()
                && pos.getY() >= frameMin.getY()
                && pos.getY() <= frameMax.getY()
                && pos.getZ() >= frameMin.getZ()
                && pos.getZ() <= frameMax.getZ();
    }

    private static boolean onFrameShell(BlockPos frameMin, BlockPos frameMax, BlockPos pos) {
        return pos.getX() == frameMin.getX()
                || pos.getX() == frameMax.getX()
                || pos.getY() == frameMin.getY()
                || pos.getY() == frameMax.getY()
                || pos.getZ() == frameMin.getZ()
                || pos.getZ() == frameMax.getZ();
    }

    private record TransferKey(Direction fromFace, Direction toFace) {
    }

    private record StateKey(BlockPos pos, Direction direction) {
        private StateKey {
            pos = pos.immutable();
        }
    }

    private record ComponentKey(FrequencyKey frequency, Direction direction, CoherenceKind coherence) {
        private PlaneWaveComponent component(double power) {
            return new PlaneWaveComponent(frequency, power, direction, coherence);
        }
    }

    private record TravelState(BlockPos pos, Direction direction, BeamPacket beam) {
        private TravelState {
            pos = pos.immutable();
        }
    }

    @FunctionalInterface
    private interface BoundaryHitConsumer {
        void accept(Direction face, BeamPacket beam);
    }

    private MicrolizerOpticalTransferCompiler() {
    }
}
