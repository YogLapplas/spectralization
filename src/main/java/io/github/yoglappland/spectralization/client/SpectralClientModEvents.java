package io.github.yoglappland.spectralization.client;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.client.model.CompactedMachineDynamicModel;
import io.github.yoglappland.spectralization.client.renderer.LensHolderRenderer;
import io.github.yoglappland.spectralization.client.renderer.MetamaterialDesignTableRenderer;
import io.github.yoglappland.spectralization.client.screen.CoatingBrushScreen;
import io.github.yoglappland.spectralization.client.screen.CompactMachineCoreScreen;
import io.github.yoglappland.spectralization.client.screen.CompactedMachineScreen;
import io.github.yoglappland.spectralization.client.screen.CreativeLightSourceScreen;
import io.github.yoglappland.spectralization.client.screen.HolographicStorageCoreScreen;
import io.github.yoglappland.spectralization.client.screen.HolographicStorageScreen;
import io.github.yoglappland.spectralization.client.screen.LensGrindingBenchScreen;
import io.github.yoglappland.spectralization.client.screen.MetamaterialDesignTableScreen;
import io.github.yoglappland.spectralization.client.screen.PhotothermalGeneratorScreen;
import io.github.yoglappland.spectralization.client.screen.SpectrometerScreen;
import io.github.yoglappland.spectralization.client.screen.ThermalSmelterScreen;
import io.github.yoglappland.spectralization.registry.SpectralBlockEntities;
import io.github.yoglappland.spectralization.registry.SpectralMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = Spectralization.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class SpectralClientModEvents {
    private SpectralClientModEvents() {
    }

    @SubscribeEvent
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(SpectralBlockEntities.LENS_HOLDER.get(), LensHolderRenderer::new);
        event.registerBlockEntityRenderer(
                SpectralBlockEntities.METAMATERIAL_DESIGN_TABLE.get(),
                MetamaterialDesignTableRenderer::new
        );
    }

    @SubscribeEvent
    static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
        CompactedMachineDynamicModel.registerAdditionalModels(event);
    }

    @SubscribeEvent
    static void modifyBakingResult(ModelEvent.ModifyBakingResult event) {
        CompactedMachineDynamicModel.modifyBakingResult(event);
    }

    @SubscribeEvent
    static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(SpectralMenus.CREATIVE_LIGHT_SOURCE.get(), CreativeLightSourceScreen::new);
        event.register(SpectralMenus.COATING_BRUSH.get(), CoatingBrushScreen::new);
        event.register(SpectralMenus.SPECTROMETER.get(), SpectrometerScreen::new);
        event.register(SpectralMenus.PHOTOTHERMAL_GENERATOR.get(), PhotothermalGeneratorScreen::new);
        event.register(SpectralMenus.THERMAL_SMELTER.get(), ThermalSmelterScreen::new);
        event.register(SpectralMenus.LENS_GRINDING_BENCH.get(), LensGrindingBenchScreen::new);
        event.register(SpectralMenus.METAMATERIAL_DESIGN_TABLE.get(), MetamaterialDesignTableScreen::new);
        event.register(SpectralMenus.HOLOGRAPHIC_STORAGE.get(), HolographicStorageScreen::new);
        event.register(SpectralMenus.HOLOGRAPHIC_STORAGE_CORE.get(), HolographicStorageCoreScreen::new);
        event.register(SpectralMenus.COMPACT_MACHINE_CORE.get(), CompactMachineCoreScreen::new);
        event.register(SpectralMenus.COMPACTED_MACHINE.get(), CompactedMachineScreen::new);
    }
}
