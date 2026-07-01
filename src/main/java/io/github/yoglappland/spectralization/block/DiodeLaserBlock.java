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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class DiodeLaserBlock extends HorizontalFacingBlock implements OpticalSource {
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(5.0D, 0.0D, 5.0D, 11.0D, 1.0D, 11.0D),
            Block.box(6.0D, 1.0D, 6.0D, 10.0D, 2.0D, 10.0D)
    ).optimize();

    private final double coherentPower;
    private final BeamEnvelope envelope;

    public DiodeLaserBlock(Properties properties, double coherentPower) {
        super(properties);
        this.coherentPower = coherentPower;
        this.envelope = BeamEnvelope.collimated(0.12D)
                .withBeamQuality(2.8D)
                .withScatter(0.015D);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.SOUTH));
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
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
