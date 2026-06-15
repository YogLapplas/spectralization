package io.github.yoglappland.spectralization.registry;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.menu.CoatingBrushMenu;
import io.github.yoglappland.spectralization.menu.CompactMachineCoreMenu;
import io.github.yoglappland.spectralization.menu.CompactedMachineMenu;
import io.github.yoglappland.spectralization.menu.CreativeLightSourceMenu;
import io.github.yoglappland.spectralization.menu.HolographicStorageCoreMenu;
import io.github.yoglappland.spectralization.menu.HolographicStorageMenu;
import io.github.yoglappland.spectralization.menu.PhotothermalGeneratorMenu;
import io.github.yoglappland.spectralization.menu.SpectrometerMenu;
import io.github.yoglappland.spectralization.menu.ThermalSmelterMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
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

    public static final DeferredHolder<MenuType<?>, MenuType<PhotothermalGeneratorMenu>> PHOTOTHERMAL_GENERATOR =
            MENU_TYPES.register("photothermal_generator", () ->
                    new MenuType<>(PhotothermalGeneratorMenu::new, FeatureFlags.DEFAULT_FLAGS));

    public static final DeferredHolder<MenuType<?>, MenuType<ThermalSmelterMenu>> THERMAL_SMELTER =
            MENU_TYPES.register("thermal_smelter", () ->
                    new MenuType<>(ThermalSmelterMenu::new, FeatureFlags.DEFAULT_FLAGS));

    public static final DeferredHolder<MenuType<?>, MenuType<HolographicStorageMenu>> HOLOGRAPHIC_STORAGE =
            MENU_TYPES.register("holographic_storage", () ->
                    new MenuType<>(HolographicStorageMenu::new, FeatureFlags.DEFAULT_FLAGS));

    public static final DeferredHolder<MenuType<?>, MenuType<HolographicStorageCoreMenu>> HOLOGRAPHIC_STORAGE_CORE =
            MENU_TYPES.register("holographic_storage_core", () ->
                    new MenuType<>(HolographicStorageCoreMenu::new, FeatureFlags.DEFAULT_FLAGS));

    public static final DeferredHolder<MenuType<?>, MenuType<CompactMachineCoreMenu>> COMPACT_MACHINE_CORE =
            MENU_TYPES.register("compact_machine_core", () ->
                    new MenuType<>(CompactMachineCoreMenu::new, FeatureFlags.DEFAULT_FLAGS));

    public static final DeferredHolder<MenuType<?>, MenuType<CompactedMachineMenu>> COMPACTED_MACHINE =
            MENU_TYPES.register("compacted_machine", () ->
                    IMenuTypeExtension.create(CompactedMachineMenu::new));

    public static void register(IEventBus modEventBus) {
        MENU_TYPES.register(modEventBus);
    }

    private SpectralMenus() {
    }
}
