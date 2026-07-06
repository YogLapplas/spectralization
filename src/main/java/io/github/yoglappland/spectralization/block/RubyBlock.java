package io.github.yoglappland.spectralization.block;

import io.github.yoglappland.spectralization.blockentity.RubyBlockEntity;
import io.github.yoglappland.spectralization.optics.BeamEnvelope;
import io.github.yoglappland.spectralization.optics.BeamPacket;
import io.github.yoglappland.spectralization.optics.CoherenceKind;
import io.github.yoglappland.spectralization.optics.FrequencyKey;
import io.github.yoglappland.spectralization.optics.OpticalMaterialProfiles;
import io.github.yoglappland.spectralization.optics.OpticalSource;
import io.github.yoglappland.spectralization.optics.OutputBeam;
import io.github.yoglappland.spectralization.optics.PlaneWaveComponent;
import io.github.yoglappland.spectralization.optics.SpectralColorMap;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class RubyBlock extends Block implements EntityBlock, OpticalSource {
    public static final FrequencyKey RUBY_LINE = FrequencyKey.visible(SpectralColorMap.VISIBLE_RED_BIN);

    public RubyBlock(Properties properties) {
        super(properties);
    }

    public static int lightLevel(BlockState state) {
        return 0;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RubyBlockEntity(pos, state);
    }

    @Override
    public List<OutputBeam> getOutputBeams(BlockState state, Level level, BlockPos pos) {
        double powerPerDirection = OpticalMaterialProfiles.rubyExcitedCoherentSeedPowerPerDirection(level, pos, state);

        if (powerPerDirection <= 0.0) {
            return List.of();
        }

        List<OutputBeam> beams = new ArrayList<>(Direction.values().length);

        for (Direction direction : Direction.values()) {
            beams.add(new OutputBeam(
                    direction,
                    BeamPacket.single(
                            new PlaneWaveComponent(RUBY_LINE, powerPerDirection, direction, CoherenceKind.COHERENT),
                            BeamEnvelope.collimated(0.25)
                    )
            ));
        }

        return beams;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        refreshRuby(level, pos);
    }

    @Override
    protected void neighborChanged(
            BlockState state,
            Level level,
            BlockPos pos,
            Block neighborBlock,
            BlockPos neighborPos,
            boolean movedByPiston
    ) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        refreshRuby(level, pos);
    }

    private static void refreshRuby(Level level, BlockPos pos) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof RubyBlockEntity ruby) {
            ruby.refreshOutput();
        }
    }
}
