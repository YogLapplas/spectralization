package io.github.yoglappland.spectralization.integration.jade;

import io.github.yoglappland.spectralization.blockentity.BeamProfilerBlockEntity;
import io.github.yoglappland.spectralization.optics.geometry.BeamProfileReadoutSample;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public class BeamProfilerComponentProvider implements IBlockComponentProvider {
    public static final BeamProfilerComponentProvider INSTANCE = new BeamProfilerComponentProvider();

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (!(accessor.getBlockEntity() instanceof BeamProfilerBlockEntity profiler)) {
            return;
        }

        BeamProfileReadoutSample sample = profiler.sample();
        Component reliability = Component.translatable(profiler.isOutputReliable()
                ? "jade.spectralization.sensor.reliability.reliable"
                : "jade.spectralization.sensor.reliability.unstable");

        tooltip.add(Component.translatable(
                "jade.spectralization.beam_profiler.power",
                format(sample.power()),
                reliability
        ));
        tooltip.add(Component.translatable(
                "jade.spectralization.beam_profiler.coherence",
                format(sample.coherentPower()),
                format(sample.strayPower())
        ));
        tooltip.add(Component.translatable("jade.spectralization.beam_profiler.radius", format(sample.radius())));
        tooltip.add(Component.translatable("jade.spectralization.beam_profiler.divergence", format(sample.divergence())));
        tooltip.add(Component.translatable("jade.spectralization.beam_profiler.irradiance", format(sample.irradiance())));
        tooltip.add(Component.translatable(
                "jade.spectralization.beam_profiler.quality",
                format(sample.beamQuality()),
                format(sample.scatter())
        ));
    }

    @Override
    public ResourceLocation getUid() {
        return SpectralJadePlugin.BEAM_PROFILER;
    }

    private static String format(double value) {
        double absolute = Math.abs(value);

        if ((absolute > 0.0 && absolute < 0.01) || absolute >= 10000.0) {
            return String.format("%.3e", value);
        }

        return String.format("%.3f", value);
    }
}
