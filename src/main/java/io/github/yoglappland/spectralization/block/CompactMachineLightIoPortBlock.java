package io.github.yoglappland.spectralization.block;

import io.github.yoglappland.spectralization.compact.CompactMachinePartKind;
import io.github.yoglappland.spectralization.optics.BeamPacket;
import io.github.yoglappland.spectralization.optics.CompiledOpticalNetwork;
import io.github.yoglappland.spectralization.optics.OpticalElement;
import io.github.yoglappland.spectralization.optics.OpticalResult;
import io.github.yoglappland.spectralization.optics.topology.OpticalTopologyProvider;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public class CompactMachineLightIoPortBlock extends CompactMachinePartBlock implements OpticalElement, OpticalTopologyProvider {
    public CompactMachineLightIoPortBlock(BlockBehaviour.Properties properties) {
        super(properties, CompactMachinePartKind.IO_PORT);
    }

    @Override
    public CompiledOpticalNetwork compileOpticalNetwork(BlockState state, Level level, BlockPos pos) {
        CompiledOpticalNetwork.Builder builder = CompiledOpticalNetwork.builder();

        for (Direction incomingDirection : Direction.values()) {
            builder.addRule(
                    incomingDirection,
                    incomingDirection.getOpposite(),
                    CompiledOpticalNetwork.passThrough()
            );
        }

        return builder.build();
    }

    @Override
    public Set<Direction> potentialOutgoingDirections(
            BlockState state,
            Level level,
            BlockPos pos,
            Direction incomingDirection
    ) {
        return Set.of(incomingDirection.getOpposite());
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
}
