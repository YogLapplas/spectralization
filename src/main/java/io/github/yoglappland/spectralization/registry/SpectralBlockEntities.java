package io.github.yoglappland.spectralization.registry;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.blockentity.BasicLithographyMachineBlockEntity;
import io.github.yoglappland.spectralization.blockentity.BeamProfilerBlockEntity;
import io.github.yoglappland.spectralization.blockentity.CmosSensorBlockEntity;
import io.github.yoglappland.spectralization.blockentity.CompactMachineCoreBlockEntity;
import io.github.yoglappland.spectralization.blockentity.CompactedMachineBlockEntity;
import io.github.yoglappland.spectralization.blockentity.CreativeLightSourceBlockEntity;
import io.github.yoglappland.spectralization.blockentity.FiberOpticInterfaceBlockEntity;
import io.github.yoglappland.spectralization.blockentity.FiberRelayBlockEntity;
import io.github.yoglappland.spectralization.blockentity.HolographicStorageCrystalBlockEntity;
import io.github.yoglappland.spectralization.blockentity.HolographicStorageMainCoreBlockEntity;
import io.github.yoglappland.spectralization.blockentity.LensGrindingBenchBlockEntity;
import io.github.yoglappland.spectralization.blockentity.LensHolderBlockEntity;
import io.github.yoglappland.spectralization.blockentity.MetamaterialDesignTableBlockEntity;
import io.github.yoglappland.spectralization.blockentity.PassThroughSensorBlockEntity;
import io.github.yoglappland.spectralization.blockentity.PhotonicGradientGeneratorBlockEntity;
import io.github.yoglappland.spectralization.blockentity.PhotothermalGeneratorBlockEntity;
import io.github.yoglappland.spectralization.blockentity.RubyBlockEntity;
import io.github.yoglappland.spectralization.blockentity.SpectrometerBlockEntity;
import io.github.yoglappland.spectralization.blockentity.StrayLightEmitterBlockEntity;
import io.github.yoglappland.spectralization.blockentity.ThermalSmelterBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class SpectralBlockEntities {
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Spectralization.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LensHolderBlockEntity>> LENS_HOLDER =
            BLOCK_ENTITY_TYPES.register("lens_holder", () ->
                    BlockEntityType.Builder.of(LensHolderBlockEntity::new, Spectralization.LENS_HOLDER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LensGrindingBenchBlockEntity>> LENS_GRINDING_BENCH =
            BLOCK_ENTITY_TYPES.register("lens_grinding_bench", () ->
                    BlockEntityType.Builder.of(
                            LensGrindingBenchBlockEntity::new,
                            Spectralization.LENS_GRINDING_BENCH.get()
                    ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MetamaterialDesignTableBlockEntity>> METAMATERIAL_DESIGN_TABLE =
            BLOCK_ENTITY_TYPES.register("metamaterial_design_table", () ->
                    BlockEntityType.Builder.of(
                            MetamaterialDesignTableBlockEntity::new,
                            Spectralization.METAMATERIAL_DESIGN_TABLE.get()
                    ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BasicLithographyMachineBlockEntity>> BASIC_LITHOGRAPHY_MACHINE =
            BLOCK_ENTITY_TYPES.register("basic_lithography_machine", () ->
                    BlockEntityType.Builder.of(
                            BasicLithographyMachineBlockEntity::new,
                            Spectralization.BASIC_LITHOGRAPHY_MACHINE.get()
                    ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FiberOpticInterfaceBlockEntity>> FIBER_OPTIC_INTERFACE =
            BLOCK_ENTITY_TYPES.register("fiber_optic_interface", () ->
                    BlockEntityType.Builder.of(FiberOpticInterfaceBlockEntity::new, Spectralization.FIBER_OPTIC_INTERFACE.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FiberRelayBlockEntity>> FIBER_RELAY =
            BLOCK_ENTITY_TYPES.register("fiber_relay", () ->
                    BlockEntityType.Builder.of(FiberRelayBlockEntity::new, Spectralization.FIBER_RELAY.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HolographicStorageCrystalBlockEntity>> HOLOGRAPHIC_STORAGE_CRYSTAL =
            BLOCK_ENTITY_TYPES.register("holographic_storage_crystal", () ->
                    BlockEntityType.Builder.of(
                            HolographicStorageCrystalBlockEntity::new,
                            Spectralization.HOLOGRAPHIC_STORAGE_CRYSTAL.get()
                    ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HolographicStorageMainCoreBlockEntity>> HOLOGRAPHIC_STORAGE_MAIN_CORE =
            BLOCK_ENTITY_TYPES.register("holographic_storage_main_core", () ->
                    BlockEntityType.Builder.of(
                            HolographicStorageMainCoreBlockEntity::new,
                            Spectralization.HOLOGRAPHIC_STORAGE_MAIN_CORE.get()
                    ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CompactedMachineBlockEntity>> COMPACTED_MACHINE =
            BLOCK_ENTITY_TYPES.register("compacted_machine", () ->
                    BlockEntityType.Builder.of(
                            CompactedMachineBlockEntity::new,
                            Spectralization.COMPACTED_MACHINE.get()
                    ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CompactMachineCoreBlockEntity>> COMPACT_MACHINE_CORE =
            BLOCK_ENTITY_TYPES.register("compact_machine_core", () ->
                    BlockEntityType.Builder.of(
                            CompactMachineCoreBlockEntity::new,
                            Spectralization.COMPACT_MACHINE_CORE.get()
                    ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CreativeLightSourceBlockEntity>> CREATIVE_LIGHT_SOURCE =
            BLOCK_ENTITY_TYPES.register("creative_light_source", () ->
                    BlockEntityType.Builder.of(CreativeLightSourceBlockEntity::new, Spectralization.CREATIVE_LIGHT_SOURCE.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CmosSensorBlockEntity>> CMOS_SENSOR =
            BLOCK_ENTITY_TYPES.register("cmos_sensor", () ->
                    BlockEntityType.Builder.of(CmosSensorBlockEntity::new, Spectralization.CMOS_SENSOR.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PassThroughSensorBlockEntity>> PASS_THROUGH_SENSOR =
            BLOCK_ENTITY_TYPES.register("pass_through_sensor", () ->
                    BlockEntityType.Builder.of(PassThroughSensorBlockEntity::new, Spectralization.PASS_THROUGH_SENSOR.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BeamProfilerBlockEntity>> BEAM_PROFILER =
            BLOCK_ENTITY_TYPES.register("beam_profiler", () ->
                    BlockEntityType.Builder.of(BeamProfilerBlockEntity::new, Spectralization.BEAM_PROFILER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SpectrometerBlockEntity>> SPECTROMETER =
            BLOCK_ENTITY_TYPES.register("spectrometer", () ->
                    BlockEntityType.Builder.of(SpectrometerBlockEntity::new, Spectralization.SPECTROMETER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RubyBlockEntity>> RUBY_BLOCK =
            BLOCK_ENTITY_TYPES.register("ruby_block", () ->
                    BlockEntityType.Builder.of(RubyBlockEntity::new, Spectralization.RUBY_BLOCK.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PhotonicGradientGeneratorBlockEntity>> PHOTONIC_GRADIENT_GENERATOR =
            BLOCK_ENTITY_TYPES.register("photonic_gradient_generator", () ->
                    BlockEntityType.Builder.of(PhotonicGradientGeneratorBlockEntity::new, Spectralization.PHOTONIC_GRADIENT_GENERATOR.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PhotothermalGeneratorBlockEntity>> PHOTOTHERMAL_GENERATOR =
            BLOCK_ENTITY_TYPES.register("photothermal_generator", () ->
                    BlockEntityType.Builder.of(PhotothermalGeneratorBlockEntity::new, Spectralization.PHOTOTHERMAL_GENERATOR.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ThermalSmelterBlockEntity>> THERMAL_SMELTER =
            BLOCK_ENTITY_TYPES.register("thermal_smelter", () ->
                    BlockEntityType.Builder.of(ThermalSmelterBlockEntity::new, Spectralization.THERMAL_SMELTER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StrayLightEmitterBlockEntity>> STRAY_LIGHT_EMITTER =
            BLOCK_ENTITY_TYPES.register("stray_light_emitter", () ->
                    BlockEntityType.Builder.of(
                            StrayLightEmitterBlockEntity::new,
                            Spectralization.STRAY_LIGHT_EMITTER.get(),
                            Spectralization.ADVANCED_STRAY_LIGHT_EMITTER.get()
                    ).build(null));

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITY_TYPES.register(modEventBus);
    }

    private SpectralBlockEntities() {
    }
}
