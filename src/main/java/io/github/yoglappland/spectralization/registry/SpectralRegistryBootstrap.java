package io.github.yoglappland.spectralization.registry;

import io.github.yoglappland.spectralization.Spectralization;
import net.neoforged.bus.api.IEventBus;

public final class SpectralRegistryBootstrap {
    private SpectralRegistryBootstrap() {
    }

    public static void register(IEventBus modEventBus) {
        Spectralization.BLOCKS.register(modEventBus);
        Spectralization.ITEMS.register(modEventBus);
        Spectralization.RECIPE_SERIALIZERS.register(modEventBus);
        Spectralization.CREATIVE_MODE_TABS.register(modEventBus);
        SpectralBlockEntities.register(modEventBus);
        SpectralMenus.register(modEventBus);
    }
}
