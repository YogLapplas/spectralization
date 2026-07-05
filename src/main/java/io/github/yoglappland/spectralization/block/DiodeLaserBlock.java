package io.github.yoglappland.spectralization.block;

import io.github.yoglappland.spectralization.optics.BeamEnvelope;
import io.github.yoglappland.spectralization.optics.BeamPacket;
import io.github.yoglappland.spectralization.optics.CoherenceKind;
import io.github.yoglappland.spectralization.optics.OpticalSource;
import io.github.yoglappland.spectralization.optics.OutputBeam;
import io.github.yoglappland.spectralization.optics.PlaneWaveComponent;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class DiodeLaserBlock extends DirectionalFacingBlock implements OpticalSource {
    private static final double[][] MODEL_BOXES = {
            {5.0D, 0.0D, 5.0D, 11.0D, 1.0D, 11.0D},
            {6.0D, 1.0D, 6.0D, 10.0D, 2.0D, 10.0D}
    };
    private static final VoxelShape UP_SHAPE = buildLocalUpShape(Direction.UP, MODEL_BOXES);
    private static final VoxelShape DOWN_SHAPE = buildLocalUpShape(Direction.DOWN, MODEL_BOXES);
    private static final VoxelShape NORTH_SHAPE = buildLocalUpShape(Direction.NORTH, MODEL_BOXES);
    private static final VoxelShape EAST_SHAPE = buildLocalUpShape(Direction.EAST, MODEL_BOXES);
    private static final VoxelShape SOUTH_SHAPE = buildLocalUpShape(Direction.SOUTH, MODEL_BOXES);
    private static final VoxelShape WEST_SHAPE = buildLocalUpShape(Direction.WEST, MODEL_BOXES);

    private final double coherentPower;
    private final BeamEnvelope envelope;

    public DiodeLaserBlock(Properties properties, double coherentPower) {
        super(properties);
        this.coherentPower = coherentPower;
        this.envelope = BeamEnvelope.collimated(0.12D)
                .withBeamQuality(2.8D)
                .withScatter(0.015D);
    }

    @Override
    public List<OutputBeam> getOutputBeams(BlockState state, Level level, BlockPos pos) {
        Direction direction = state.getValue(FACING);
        PlaneWaveComponent component = new PlaneWaveComponent(
                RubyBlock.RUBY_LINE,
                coherentPower,
                direction,
                CoherenceKind.COHERENT
        );
        return List.of(new OutputBeam(direction, BeamPacket.single(component, envelope)));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeForFacing(state.getValue(FACING));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeForFacing(state.getValue(FACING));
    }

    private static VoxelShape shapeForFacing(Direction facing) {
        return switch (facing) {
            case DOWN -> DOWN_SHAPE;
            case NORTH -> NORTH_SHAPE;
            case EAST -> EAST_SHAPE;
            case SOUTH -> SOUTH_SHAPE;
            case WEST -> WEST_SHAPE;
            default -> UP_SHAPE;
        };
    }
}
