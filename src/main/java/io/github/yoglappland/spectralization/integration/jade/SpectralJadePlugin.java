package io.github.yoglappland.spectralization.integration.jade;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.block.BeamProfilerBlock;
import io.github.yoglappland.spectralization.block.CmosSensorBlock;
import io.github.yoglappland.spectralization.block.CompactMachineCoreBlock;
import io.github.yoglappland.spectralization.block.FiberOpticInterfaceBlock;
import io.github.yoglappland.spectralization.block.FiberRelayBlock;
import io.github.yoglappland.spectralization.block.PassThroughSensorBlock;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

@WailaPlugin
public class SpectralJadePlugin implements IWailaPlugin {
    public static final ResourceLocation PASS_THROUGH_SENSOR =
            ResourceLocation.fromNamespaceAndPath(Spectralization.MODID, "pass_through_sensor");
    public static final ResourceLocation CMOS_SENSOR =
            ResourceLocation.fromNamespaceAndPath(Spectralization.MODID, "cmos_sensor");
    public static final ResourceLocation BEAM_PROFILER =
            ResourceLocation.fromNamespaceAndPath(Spectralization.MODID, "beam_profiler");
    public static final ResourceLocation FIBER_NODE =
            ResourceLocation.fromNamespaceAndPath(Spectralization.MODID, "fiber_node");
    public static final ResourceLocation COMPACT_MACHINE_CORE =
            ResourceLocation.fromNamespaceAndPath(Spectralization.MODID, "compact_machine_core");

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(FiberNodeComponentProvider.INSTANCE, FiberOpticInterfaceBlock.class);
        registration.registerBlockDataProvider(FiberNodeComponentProvider.INSTANCE, FiberRelayBlock.class);
        registration.registerBlockDataProvider(CompactMachineCoreComponentProvider.INSTANCE, CompactMachineCoreBlock.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(CmosSensorComponentProvider.INSTANCE, CmosSensorBlock.class);
        registration.registerBlockComponent(PassThroughSensorComponentProvider.INSTANCE, PassThroughSensorBlock.class);
        registration.registerBlockComponent(BeamProfilerComponentProvider.INSTANCE, BeamProfilerBlock.class);
        registration.registerBlockComponent(FiberNodeComponentProvider.INSTANCE, FiberOpticInterfaceBlock.class);
        registration.registerBlockComponent(FiberNodeComponentProvider.INSTANCE, FiberRelayBlock.class);
        registration.registerBlockComponent(CompactMachineCoreComponentProvider.INSTANCE, CompactMachineCoreBlock.class);
    }
}
