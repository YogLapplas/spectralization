package io.github.yoglappland.spectralization.optics;

import io.github.yoglappland.spectralization.block.RubyBlock;
import io.github.yoglappland.spectralization.tag.SpectralBlockTags;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class IntrinsicOpticalSources {
    private static final double GLOWSTONE_STRAY_POWER_PER_DIRECTION = 1.0;
    private static final BeamEnvelope GLOWSTONE_STRAY_ENVELOPE =
            BeamEnvelope.collimated(0.45).withBeamQuality(8.0).withScatter(1.0);

    public static boolean isSource(BlockState state) {
        return state.getBlock() instanceof OpticalSource || isBuiltInSource(state);
    }

    public static boolean isBuiltInSource(BlockState state) {
        return state.is(SpectralBlockTags.STRAY_LIGHT_SOURCE);
    }

    public static List<OutputBeam> outputBeams(BlockState state, Level level, BlockPos pos) {
        if (state.getBlock() instanceof OpticalSource source) {
            return source.getOutputBeams(state, level, pos);
        }

        return builtInOutputBeams(state);
    }

    public static List<OutputBeam> builtInOutputBeams(BlockState state) {
        if (!isBuiltInSource(state)) {
            return List.of();
        }

        List<OutputBeam> beams = new ArrayList<>(Direction.values().length);

        for (Direction direction : Direction.values()) {
            PlaneWaveComponent component = new PlaneWaveComponent(
                    RubyBlock.RUBY_LINE,
                    GLOWSTONE_STRAY_POWER_PER_DIRECTION,
                    direction,
                    CoherenceKind.INCOHERENT
            );
            beams.add(new OutputBeam(direction, BeamPacket.single(component, GLOWSTONE_STRAY_ENVELOPE)));
        }

        return beams;
    }

    private IntrinsicOpticalSources() {
    }
}
