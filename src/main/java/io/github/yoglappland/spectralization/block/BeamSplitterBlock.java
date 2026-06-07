package io.github.yoglappland.spectralization.block;

import io.github.yoglappland.spectralization.optics.BeamPacket;
import io.github.yoglappland.spectralization.optics.CompiledOpticalNetwork;
import io.github.yoglappland.spectralization.optics.OpticalResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class BeamSplitterBlock extends MirrorBlock {
    private static final double DEFAULT_TRANSMITTANCE = 0.5;
    private static final double DEFAULT_REFLECTANCE = 0.5;

    public BeamSplitterBlock(Properties properties) {
        super(properties);
    }

    @Override
    public CompiledOpticalNetwork compileOpticalNetwork(BlockState state, Level level, BlockPos pos) {
        CompiledOpticalNetwork.Builder builder = CompiledOpticalNetwork.builder();

        for (Direction incomingDirection : Direction.values()) {
            Direction transmissionDirection = incomingDirection.getOpposite();

            if (!getConnectedDirections(state).contains(incomingDirection)) {
                builder.addRule(incomingDirection, transmissionDirection, CompiledOpticalNetwork.passThrough());
                continue;
            }

            builder.addRule(
                    incomingDirection,
                    transmissionDirection,
                    (input, ignoredIncoming, outgoingDirection) ->
                            input.withDirection(outgoingDirection).scalePower(transmittance(state, input, incomingDirection))
            );
            builder.addRule(
                    incomingDirection,
                    getReflectedDirection(state, incomingDirection),
                    (input, ignoredIncoming, outgoingDirection) ->
                            input.withDirection(outgoingDirection).scalePower(reflectance(state, input, incomingDirection))
            );
        }

        return builder.build();
    }

    @Override
    public OpticalResult interact(
            BeamPacket input,
            Direction incomingDirection,
            BlockState state,
            Level level,
            BlockPos pos
    ) {
        return compileOpticalNetwork(state, level, pos).interact(input, incomingDirection);
    }

    protected double transmittance(BlockState state, BeamPacket input, Direction incomingDirection) {
        return DEFAULT_TRANSMITTANCE;
    }

    protected double reflectance(BlockState state, BeamPacket input, Direction incomingDirection) {
        return DEFAULT_REFLECTANCE;
    }
}
