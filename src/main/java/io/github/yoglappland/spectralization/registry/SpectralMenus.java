package io.github.yoglappland.spectralization.registry;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.menu.CoatingBrushMenu;
import io.github.yoglappland.spectralization.menu.CreativeLightSourceMenu;
import io.github.yoglappland.spectralization.menu.SpectrometerMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class SpectralMenus {
    private static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, Spectralization.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<CreativeLightSourceMenu>> CREATIVE_LIGHT_SOURCE =
            MENU_TYPES.register("creative_light_source", () ->
                    new MenuType<>(CreativeLightSourceMenu::new, FeatureFlags.DEFAULT_FLAGS));

    public static final DeferredHolder<MenuType<?>, MenuType<CoatingBrushMenu>> COATING_BRUSH =
            MENU_TYPES.register("coating_brush", () ->
                    new MenuType<>(CoatingBrushMenu::new, FeatureFlags.DEFAULT_FLAGS));

    public static final DeferredHolder<MenuType<?>, MenuType<SpectrometerMenu>> SPECTROMETER =
            MENU_TYPES.register("spectrometer", () ->
                    new MenuType<>(SpectrometerMenu::new, FeatureFlags.DEFAULT_FLAGS));

    public static void register(IEventBus modEventBus) {
        MENU_TYPES.register(modEventBus);
    }

    private SpectralMenus() {
    }
}
