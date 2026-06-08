package io.github.yoglappland.spectralization.block;

import io.github.yoglappland.spectralization.blockentity.RubyBlockEntity;
import io.github.yoglappland.spectralization.optics.BeamEnvelope;
import io.github.yoglappland.spectralization.optics.BeamPacket;
import io.github.yoglappland.spectralization.optics.CoherenceKind;
import io.github.yoglappland.spectralization.optics.FrequencyKey;
import io.github.yoglappland.spectralization.optics.OpticalSource;
import io.github.yoglappland.spectralization.optics.OutputBeam;
import io.github.yoglappland.spectralization.optics.PlaneWaveComponent;
import io.github.yoglappland.spectralization.optics.pump.OpticalPumpSources;
import io.github.yoglappland.spectralization.registry.SpectralBlockEntities;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class RubyBlock extends Block implements EntityBlock, OpticalSource {
    public static final FrequencyKey RUBY_LINE = FrequencyKey.visible(4);
    private static final double SEED_POWER_PER_GLOWSTONE = 0.10;
    private static final double QUIET_SOURCE_POWER = 1.0E-9;

    public RubyBlock(Properties properties) {
        super(properties);
    }

    public static int lightLevel(BlockState state) {
        return 0;
    }

    public static List<OutputBeam> quietOutputBeams() {
        return outputBeams(QUIET_SOURCE_POWER, Direction.values());
    }

    @Override
    public List<OutputBeam> getOutputBeams(BlockState state, Level level, BlockPos pos) {
        double seedRate = OpticalPumpSources.adjacentSeedRate(level, pos);

        if (seedRate <= 0.0) {
            return List.of();
        }

        return outputBeams(seedRate * SEED_POWER_PER_GLOWSTONE, Direction.values());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RubyBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        if (level.isClientSide || blockEntityType != SpectralBlockEntities.RUBY_BLOCK.get()) {
            return null;
        }

        return (tickerLevel, pos, tickerState, blockEntity) ->
                RubyBlockEntity.tick(tickerLevel, pos, (RubyBlockEntity) blockEntity);
    }

    private static List<OutputBeam> outputBeams(double power, Direction[] directions) {
        return outputBeams(power, List.of(directions));
    }

    private static List<OutputBeam> outputBeams(double power, List<Direction> directions) {
        if (power <= 0.0) {
            return List.of();
        }

        List<OutputBeam> beams = new ArrayList<>();

        for (Direction direction : directions) {
            beams.add(outputBeam(power, direction));
        }

        return beams;
    }

    private static OutputBeam outputBeam(double power, Direction direction) {
        PlaneWaveComponent component = new PlaneWaveComponent(
                RUBY_LINE,
                power,
                direction,
                CoherenceKind.INCOHERENT
        );
        return new OutputBeam(
                direction,
                BeamPacket.single(component, BeamEnvelope.collimated(0.35))
        );
    }
}
