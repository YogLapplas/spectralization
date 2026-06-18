package io.github.yoglappland.spectralization.optics.source;

import io.github.yoglappland.spectralization.block.RubyBlock;
import io.github.yoglappland.spectralization.optics.BeamEnvelope;
import io.github.yoglappland.spectralization.optics.BeamPacket;
import io.github.yoglappland.spectralization.optics.CoherenceKind;
import io.github.yoglappland.spectralization.optics.FrequencyKey;
import io.github.yoglappland.spectralization.optics.OutputBeam;
import io.github.yoglappland.spectralization.optics.PlaneWaveComponent;
import io.github.yoglappland.spectralization.tag.SpectralBlockTags;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class BuiltInOpticalSourceProfiles {
    private static final double TAGGED_STRAY_POWER_PER_DIRECTION = 1.0D;
    private static final BeamEnvelope TAGGED_STRAY_ENVELOPE =
            BeamEnvelope.collimated(0.45D).withBeamQuality(8.0D).withScatter(1.0D);
    private static final List<BuiltInOpticalSourceProfile> PROFILES = List.of(
            new OmnidirectionalTagProfile(
                    SpectralBlockTags.STRAY_LIGHT_SOURCE,
                    RubyBlock.RUBY_LINE,
                    TAGGED_STRAY_POWER_PER_DIRECTION,
                    CoherenceKind.INCOHERENT,
                    TAGGED_STRAY_ENVELOPE
            )
    );

    public static boolean hasProfile(BlockState state) {
        return profileFor(state).isPresent();
    }

    public static List<OutputBeam> outputBeams(BlockState state, Level level, BlockPos pos) {
        Optional<BuiltInOpticalSourceProfile> profile = profileFor(state);
        return profile.map(sourceProfile -> sourceProfile.outputBeams(state, level, pos)).orElse(List.of());
    }

    private static Optional<BuiltInOpticalSourceProfile> profileFor(BlockState state) {
        for (BuiltInOpticalSourceProfile profile : PROFILES) {
            if (profile.matches(state)) {
                return Optional.of(profile);
            }
        }

        return Optional.empty();
    }

    private record OmnidirectionalTagProfile(
            TagKey<Block> tag,
            FrequencyKey frequency,
            double powerPerDirection,
            CoherenceKind coherence,
            BeamEnvelope envelope
    ) implements BuiltInOpticalSourceProfile {
        @Override
        public boolean matches(BlockState state) {
            return state.is(tag);
        }

        @Override
        public List<OutputBeam> outputBeams(BlockState state, Level level, BlockPos pos) {
            List<OutputBeam> beams = new ArrayList<>(Direction.values().length);

            for (Direction direction : Direction.values()) {
                PlaneWaveComponent component = new PlaneWaveComponent(
                        frequency,
                        powerPerDirection,
                        direction,
                        coherence
                );
                beams.add(new OutputBeam(direction, BeamPacket.single(component, envelope)));
            }

            return beams;
        }
    }

    private BuiltInOpticalSourceProfiles() {
    }
}
