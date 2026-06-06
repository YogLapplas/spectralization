package io.github.yoglappland.spectralization.block;

import io.github.yoglappland.spectralization.optics.BeamPacket;
import io.github.yoglappland.spectralization.optics.OpticalResult;
import io.github.yoglappland.spectralization.optics.OutputBeam;
import java.util.ArrayList;
import java.util.List;
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
    public OpticalResult interact(
            BeamPacket input,
            Direction incomingDirection,
            BlockState state,
            Level level,
            BlockPos pos
    ) {
        if (input.isEmpty()) {
            return OpticalResult.empty();
        }

        Direction transmissionDirection = incomingDirection.getOpposite();

        if (!getConnectedDirections(state).contains(incomingDirection)) {
            return OpticalResult.single(
                    new OutputBeam(transmissionDirection, input.withDirection(transmissionDirection)),
                    0.0,
                    0.0
            );
        }

        double transmittance = transmittance(state, input, incomingDirection);
        double reflectance = reflectance(state, input, incomingDirection);
        BeamPacket transmittedBeam = input.withDirection(transmissionDirection).scalePower(transmittance);
        Direction reflectionDirection = getReflectedDirection(state, incomingDirection);
        BeamPacket reflectedBeam = input.withDirection(reflectionDirection).scalePower(reflectance);
        List<OutputBeam> outputs = new ArrayList<>(2);

        if (!transmittedBeam.isEmpty()) {
            outputs.add(new OutputBeam(transmissionDirection, transmittedBeam));
        }

        if (!reflectedBeam.isEmpty()) {
            outputs.add(new OutputBeam(reflectionDirection, reflectedBeam));
        }

        double absorbedPower = Math.max(0.0, input.totalPower() - transmittedBeam.totalPower() - reflectedBeam.totalPower());

        return new OpticalResult(outputs, absorbedPower, absorbedPower);
    }

    protected double transmittance(BlockState state, BeamPacket input, Direction incomingDirection) {
        return DEFAULT_TRANSMITTANCE;
    }

    protected double reflectance(BlockState state, BeamPacket input, Direction incomingDirection) {
        return DEFAULT_REFLECTANCE;
    }
}
