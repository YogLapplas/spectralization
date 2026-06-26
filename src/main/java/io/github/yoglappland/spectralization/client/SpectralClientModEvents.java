package io.github.yoglappland.spectralization.client;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.client.model.CompactedMachineDynamicModel;
import io.github.yoglappland.spectralization.client.model.MetamaterialTemplateDynamicModel;
import io.github.yoglappland.spectralization.client.model.SingularMaterialDynamicModel;
import io.github.yoglappland.spectralization.client.renderer.LensHolderRenderer;
import io.github.yoglappland.spectralization.client.renderer.MetamaterialDesignTableRenderer;
import io.github.yoglappland.spectralization.client.renderer.MetamaterialTemplateItemRenderer;
import io.github.yoglappland.spectralization.client.renderer.SolarDopingChamberRenderer;
import io.github.yoglappland.spectralization.client.renderer.SingularMaterialItemRenderer;
import io.github.yoglappland.spectralization.client.screen.BasicLithographyMachineScreen;
import io.github.yoglappland.spectralization.client.renderer.BasicLithographyMachineRenderer;
import io.github.yoglappland.spectralization.client.renderer.HolographicStorageShellRenderer;
import io.github.yoglappland.spectralization.client.screen.CoatingBrushScreen;
import io.github.yoglappland.spectralization.client.screen.CompactMachineCoreScreen;
import io.github.yoglappland.spectralization.client.screen.CompactedMachineScreen;
import io.github.yoglappland.spectralization.client.screen.CreativeLightSourceScreen;
import io.github.yoglappland.spectralization.client.screen.FiberDrawingMachineScreen;
import io.github.yoglappland.spectralization.client.screen.FiberLaserScreen;
import io.github.yoglappland.spectralization.client.screen.HolographicStorageCoreScreen;
import io.github.yoglappland.spectralization.client.screen.HolographicStorageScreen;
import io.github.yoglappland.spectralization.client.screen.LensGrindingBenchScreen;
import io.github.yoglappland.spectralization.client.screen.MetamaterialDesignTableScreen;
import io.github.yoglappland.spectralization.client.screen.PhotothermalGeneratorScreen;
import io.github.yoglappland.spectralization.client.screen.SolarDopingChamberScreen;
import io.github.yoglappland.spectralization.client.screen.SpectrometerScreen;
import io.github.yoglappland.spectralization.client.screen.ThermalSmelterScreen;
import io.github.yoglappland.spectralization.registry.SpectralBlockEntities;
import io.github.yoglappland.spectralization.registry.SpectralMenus;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
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
        event.registerBlockEntityRenderer(
                SpectralBlockEntities.BASIC_LITHOGRAPHY_MACHINE.get(),
                BasicLithographyMachineRenderer::new
        );
        event.registerBlockEntityRenderer(
                SpectralBlockEntities.SOLAR_DOPING_CHAMBER.get(),
                SolarDopingChamberRenderer::new
        );
        event.registerBlockEntityRenderer(
                SpectralBlockEntities.HOLOGRAPHIC_STORAGE_SHELL.get(),
                HolographicStorageShellRenderer::new
        );
    }

    @SubscribeEvent
    static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
        CompactedMachineDynamicModel.registerAdditionalModels(event);
    }

    @SubscribeEvent
    static void modifyBakingResult(ModelEvent.ModifyBakingResult event) {
        CompactedMachineDynamicModel.modifyBakingResult(event);
        MetamaterialTemplateDynamicModel.modifyBakingResult(event);
        SingularMaterialDynamicModel.modifyBakingResult(event);
    }

    @SubscribeEvent
    static void registerClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(
                new IClientItemExtensions() {
                    @Override
                    public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                        return MetamaterialTemplateItemRenderer.instance();
                    }
                },
                Spectralization.STANDARD_METAMATERIAL_TEMPLATE.get(),
                Spectralization.CUSTOM_METAMATERIAL_TEMPLATE.get()
        );
        event.registerItem(
                new IClientItemExtensions() {
                    @Override
                    public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                        return SingularMaterialItemRenderer.instance();
                    }
                },
                Spectralization.SINGULAR_MATERIAL.get()
        );
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
        event.register(SpectralMenus.BASIC_LITHOGRAPHY_MACHINE.get(), BasicLithographyMachineScreen::new);
        event.register(SpectralMenus.SOLAR_DOPING_CHAMBER.get(), SolarDopingChamberScreen::new);
        event.register(SpectralMenus.FIBER_DRAWING_MACHINE.get(), FiberDrawingMachineScreen::new);
        event.register(SpectralMenus.FIBER_LASER.get(), FiberLaserScreen::new);
        event.register(SpectralMenus.HOLOGRAPHIC_STORAGE.get(), HolographicStorageScreen::new);
        event.register(SpectralMenus.HOLOGRAPHIC_STORAGE_CORE.get(), HolographicStorageCoreScreen::new);
        event.register(SpectralMenus.COMPACT_MACHINE_CORE.get(), CompactMachineCoreScreen::new);
        event.register(SpectralMenus.COMPACTED_MACHINE.get(), CompactedMachineScreen::new);
    }
}
