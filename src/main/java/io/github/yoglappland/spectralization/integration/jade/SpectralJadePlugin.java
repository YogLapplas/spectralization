package io.github.yoglappland.spectralization.integration.jade;

import io.github.yoglappland.spectralization.Spectralization;
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

    @Override
    public void register(IWailaCommonRegistration registration) {
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(PassThroughSensorComponentProvider.INSTANCE, PassThroughSensorBlock.class);
    }
}
