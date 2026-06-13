package io.github.yoglappland.spectralization;

import com.mojang.logging.LogUtils;
import io.github.yoglappland.spectralization.block.BeamProfilerBlock;
import io.github.yoglappland.spectralization.block.BeamSplitterBlock;
import io.github.yoglappland.spectralization.block.CmosSensorBlock;
import io.github.yoglappland.spectralization.block.CreativeLightSourceBlock;
import io.github.yoglappland.spectralization.block.DynamicMirrorBlock;
import io.github.yoglappland.spectralization.block.FiberOpticInterfaceBlock;
import io.github.yoglappland.spectralization.block.FiberRelayBlock;
import io.github.yoglappland.spectralization.block.HolographicStorageCrystalBlock;
import io.github.yoglappland.spectralization.block.LensHolderBlock;
import io.github.yoglappland.spectralization.block.MirrorBlock;
import io.github.yoglappland.spectralization.block.PassThroughSensorBlock;
import io.github.yoglappland.spectralization.block.PhotonicGradientGeneratorBlock;
import io.github.yoglappland.spectralization.block.PhotothermalGeneratorBlock;
import io.github.yoglappland.spectralization.block.RubyBlock;
import io.github.yoglappland.spectralization.block.SilverGlassBlock;
import io.github.yoglappland.spectralization.block.SpectrometerBlock;
import io.github.yoglappland.spectralization.block.StrayLightEmitterBlock;
import io.github.yoglappland.spectralization.block.ThermalSmelterBlock;
import io.github.yoglappland.spectralization.blockentity.RubyBlockEntity;
import io.github.yoglappland.spectralization.client.renderer.HolographicStorageCrystalRenderer;
import io.github.yoglappland.spectralization.client.renderer.LensHolderRenderer;
import io.github.yoglappland.spectralization.client.screen.CoatingBrushScreen;
import io.github.yoglappland.spectralization.client.screen.CreativeLightSourceScreen;
import io.github.yoglappland.spectralization.client.screen.PhotothermalGeneratorScreen;
import io.github.yoglappland.spectralization.client.screen.SpectrometerScreen;
import io.github.yoglappland.spectralization.client.screen.ThermalSmelterScreen;
import io.github.yoglappland.spectralization.command.SpectralCommands;
import io.github.yoglappland.spectralization.config.SpectralizationConfig;
import io.github.yoglappland.spectralization.item.CoatingBrushItem;
import io.github.yoglappland.spectralization.item.CreativeBrushItem;
import io.github.yoglappland.spectralization.item.LensItem;
import io.github.yoglappland.spectralization.item.OpticalFiberCoilItem;
import io.github.yoglappland.spectralization.item.PaintBucketItem;
import io.github.yoglappland.spectralization.item.PhosphorTubeItem;
import io.github.yoglappland.spectralization.item.SandpaperItem;
import io.github.yoglappland.spectralization.item.SurfaceCoatingInteraction;
import io.github.yoglappland.spectralization.network.SpectralNetwork;
import io.github.yoglappland.spectralization.optics.EnvironmentLightSpectra;
import io.github.yoglappland.spectralization.optics.OpticalSpotTracker;
import io.github.yoglappland.spectralization.optics.cache.OpticalDirtyKind;
import io.github.yoglappland.spectralization.optics.cache.OpticalTraceCache;
import io.github.yoglappland.spectralization.optics.field.OpticalFieldSources;
import io.github.yoglappland.spectralization.optics.fiber.FiberOverlayPublisher;
import io.github.yoglappland.spectralization.optics.fiber.FiberNetworkIndex;
import io.github.yoglappland.spectralization.optics.fiber.FiberShearsInteraction;
import io.github.yoglappland.spectralization.optics.surface.SurfaceCoatingData;
import io.github.yoglappland.spectralization.optics.surface.SurfaceKey;
import io.github.yoglappland.spectralization.optics.surface.SurfaceTreatmentKind;
import io.github.yoglappland.spectralization.optics.topology.OpticalNetworkIndex;
import io.github.yoglappland.spectralization.optics.world.OpticalWorldIndex;
import io.github.yoglappland.spectralization.recipe.AdvancedBrushLoadingRecipe;
import io.github.yoglappland.spectralization.registry.SpectralBlockEntities;
import io.github.yoglappland.spectralization.registry.SpectralFluids;
import io.github.yoglappland.spectralization.registry.SpectralMenus;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

@Mod(Spectralization.MODID)
public class Spectralization {
    public static final String MODID = "spectralization";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<AdvancedBrushLoadingRecipe>>
            ADVANCED_BRUSH_LOADING_SERIALIZER = RECIPE_SERIALIZERS.register(
                    "advanced_brush_loading",
                    () -> new SimpleCraftingRecipeSerializer<>(AdvancedBrushLoadingRecipe::new)
            );

    public static final DeferredItem<LensItem> LENS = ITEMS.register(
            "lens",
            () -> new LensItem(new Item.Properties())
    );
    public static final DeferredItem<OpticalFiberCoilItem> OPTICAL_FIBER_COIL = ITEMS.register(
            "optical_fiber_coil",
            () -> new OpticalFiberCoilItem(new Item.Properties().stacksTo(1))
    );
    public static final DeferredItem<Item> PHOSPHOR_DUST =
            ITEMS.registerSimpleItem("phosphor_dust", new Item.Properties());
    public static final DeferredItem<PhosphorTubeItem> PHOSPHOR_TUBE = ITEMS.register(
            "phosphor_tube",
            () -> new PhosphorTubeItem(new Item.Properties())
    );
    public static final DeferredItem<Item> EMPTY_PAINT_BUCKET =
            ITEMS.registerSimpleItem("empty_paint_bucket", new Item.Properties());
    public static final DeferredItem<PaintBucketItem> SILVER_PAINT_BUCKET = ITEMS.register(
            "silver_paint_bucket",
            () -> new PaintBucketItem(
                    SurfaceTreatmentKind.SILVERING,
                    EMPTY_PAINT_BUCKET::get,
                    new Item.Properties().durability(PaintBucketItem.MAX_USES)
            )
    );
    public static final DeferredItem<PaintBucketItem> GOLD_PAINT_BUCKET = ITEMS.register(
            "gold_paint_bucket",
            () -> new PaintBucketItem(
                    SurfaceTreatmentKind.GOLDING,
                    EMPTY_PAINT_BUCKET::get,
                    new Item.Properties().durability(PaintBucketItem.MAX_USES)
            )
    );
    public static final DeferredItem<SandpaperItem> SANDPAPER = ITEMS.register(
            "sandpaper",
            () -> new SandpaperItem(new Item.Properties().durability(64))
    );
    public static final DeferredItem<CoatingBrushItem> ADVANCED_BRUSH = ITEMS.register(
            "advanced_brush",
            () -> new CoatingBrushItem(new Item.Properties().stacksTo(1))
    );
    public static final DeferredItem<CreativeBrushItem> CREATIVE_BRUSH = ITEMS.register(
            "creative_brush",
            () -> new CreativeBrushItem(new Item.Properties().stacksTo(1))
    );
    public static final DeferredItem<ArmorItem> VERITY_HELM_OF_ALL_SEEING_INSIGHT = ITEMS.register(
            "verity_helm_of_all_seeing_insight",
            () -> new ArmorItem(
                    ArmorMaterials.NETHERITE,
                    ArmorItem.Type.HELMET,
                    new Item.Properties().stacksTo(1).fireResistant()
            )
    );
    public static final DeferredItem<ArmorItem> VERITY_CUIRASS_OF_COSMIC_INDIFFERENCE = ITEMS.register(
            "verity_cuirass_of_cosmic_indifference",
            () -> new ArmorItem(
                    ArmorMaterials.NETHERITE,
                    ArmorItem.Type.CHESTPLATE,
                    new Item.Properties().stacksTo(1).fireResistant()
            )
    );
    public static final DeferredItem<ArmorItem> VERITY_LEGGINGS_OF_ENTROPIC_RELEASE = ITEMS.register(
            "verity_leggings_of_entropic_release",
            () -> new ArmorItem(
                    ArmorMaterials.NETHERITE,
                    ArmorItem.Type.LEGGINGS,
                    new Item.Properties().stacksTo(1).fireResistant()
            )
    );
    public static final DeferredItem<ArmorItem> VERITY_BOOTS_OF_LIGHT_CONE_TRANSITION = ITEMS.register(
            "verity_boots_of_light_cone_transition",
            () -> new ArmorItem(
                    ArmorMaterials.NETHERITE,
                    ArmorItem.Type.BOOTS,
                    new Item.Properties().stacksTo(1).fireResistant()
            )
    );
    public static final DeferredItem<Item> RUTILE = ITEMS.registerSimpleItem("rutile", new Item.Properties());
    public static final DeferredItem<Item> TITANIUM_DIOXIDE_DUST =
            ITEMS.registerSimpleItem("titanium_dioxide_dust", new Item.Properties());
    public static final DeferredItem<Item> CORUNDUM = ITEMS.registerSimpleItem("corundum", new Item.Properties());
    public static final DeferredItem<Item> ALUMINA_DUST =
            ITEMS.registerSimpleItem("alumina_dust", new Item.Properties());
    public static final DeferredItem<Item> FLUORITE = ITEMS.registerSimpleItem("fluorite", new Item.Properties());
    public static final DeferredItem<Item> YTTRIUM_OXIDE =
            ITEMS.registerSimpleItem("yttrium_oxide", new Item.Properties());
    public static final DeferredItem<Item> YAG_CRYSTAL =
            ITEMS.registerSimpleItem("yag_crystal", new Item.Properties());
    public static final DeferredItem<Item> RUBY = ITEMS.registerSimpleItem("ruby", new Item.Properties());
    public static final DeferredItem<Item> RAW_SILVER =
            ITEMS.registerSimpleItem("raw_silver", new Item.Properties());
    public static final DeferredItem<Item> SILVER_INGOT =
            ITEMS.registerSimpleItem("silver_ingot", new Item.Properties());

    public static final DeferredBlock<LensHolderBlock> LENS_HOLDER = BLOCKS.register(
            "lens_holder",
            () -> new LensHolderBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(0.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion())
    );

    public static final DeferredItem<BlockItem> LENS_HOLDER_ITEM =
            ITEMS.registerSimpleBlockItem("lens_holder", LENS_HOLDER);

    public static final DeferredBlock<FiberOpticInterfaceBlock> FIBER_OPTIC_INTERFACE = BLOCKS.register(
            "fiber_optic_interface",
            () -> new FiberOpticInterfaceBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .strength(1.5F, 3.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion())
    );

    public static final DeferredItem<BlockItem> FIBER_OPTIC_INTERFACE_ITEM =
            ITEMS.registerSimpleBlockItem("fiber_optic_interface", FIBER_OPTIC_INTERFACE);

    public static final DeferredBlock<FiberRelayBlock> FIBER_RELAY = BLOCKS.register(
            "fiber_relay",
            () -> new FiberRelayBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .strength(1.5F, 3.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion())
    );

    public static final DeferredItem<BlockItem> FIBER_RELAY_ITEM =
            ITEMS.registerSimpleBlockItem("fiber_relay", FIBER_RELAY);

    public static final DeferredBlock<HolographicStorageCrystalBlock> HOLOGRAPHIC_STORAGE_CRYSTAL = BLOCKS.register(
            "holographic_storage_crystal",
            () -> new HolographicStorageCrystalBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .strength(1.5F, 6.0F)
                    .sound(SoundType.AMETHYST)
                    .lightLevel(state -> 10)
                    .noOcclusion())
    );

    public static final DeferredItem<BlockItem> HOLOGRAPHIC_STORAGE_CRYSTAL_ITEM =
            ITEMS.registerSimpleBlockItem("holographic_storage_crystal", HOLOGRAPHIC_STORAGE_CRYSTAL);

    public static final DeferredBlock<MirrorBlock> MIRROR = BLOCKS.register(
            "mirror",
            () -> new MirrorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(0.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion())
    );

    public static final DeferredItem<BlockItem> MIRROR_ITEM =
            ITEMS.registerSimpleBlockItem("mirror", MIRROR);

    public static final DeferredBlock<DynamicMirrorBlock> DYNAMIC_MIRROR = BLOCKS.register(
            "dynamic_mirror",
            () -> new DynamicMirrorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(0.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion())
    );

    public static final DeferredItem<BlockItem> DYNAMIC_MIRROR_ITEM =
            ITEMS.registerSimpleBlockItem("dynamic_mirror", DYNAMIC_MIRROR);

    public static final DeferredBlock<BeamSplitterBlock> BEAM_SPLITTER = BLOCKS.register(
            "beam_splitter",
            () -> new BeamSplitterBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(0.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion())
    );

    public static final DeferredItem<BlockItem> BEAM_SPLITTER_ITEM =
            ITEMS.registerSimpleBlockItem("beam_splitter", BEAM_SPLITTER);

    public static final DeferredBlock<CreativeLightSourceBlock> CREATIVE_LIGHT_SOURCE = BLOCKS.register(
            "creative_light_source",
            () -> new CreativeLightSourceBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(0.0F)
                    .sound(SoundType.GLASS)
                    .lightLevel(state -> 15)
                    .noOcclusion())
    );

    public static final DeferredItem<BlockItem> CREATIVE_LIGHT_SOURCE_ITEM =
            ITEMS.registerSimpleBlockItem("creative_light_source", CREATIVE_LIGHT_SOURCE);

    public static final DeferredBlock<CmosSensorBlock> CMOS_SENSOR = BLOCKS.register(
            "cmos_sensor",
            () -> new CmosSensorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(0.0F)
                    .sound(SoundType.METAL)
                    .pushReaction(PushReaction.BLOCK)
                    .noOcclusion())
    );

    public static final DeferredItem<BlockItem> CMOS_SENSOR_ITEM =
            ITEMS.registerSimpleBlockItem("cmos_sensor", CMOS_SENSOR);

    public static final DeferredBlock<PassThroughSensorBlock> PASS_THROUGH_SENSOR = BLOCKS.register(
            "pass_through_sensor",
            () -> new PassThroughSensorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(0.0F)
                    .sound(SoundType.METAL)
                    .pushReaction(PushReaction.BLOCK)
                    .noOcclusion())
    );

    public static final DeferredItem<BlockItem> PASS_THROUGH_SENSOR_ITEM =
            ITEMS.registerSimpleBlockItem("pass_through_sensor", PASS_THROUGH_SENSOR);

    public static final DeferredBlock<BeamProfilerBlock> BEAM_PROFILER = BLOCKS.register(
            "beam_profiler",
            () -> new BeamProfilerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(0.0F)
                    .sound(SoundType.METAL)
                    .pushReaction(PushReaction.BLOCK)
                    .noOcclusion())
    );

    public static final DeferredItem<BlockItem> BEAM_PROFILER_ITEM =
            ITEMS.registerSimpleBlockItem("beam_profiler", BEAM_PROFILER);

    public static final DeferredBlock<SpectrometerBlock> SPECTROMETER = BLOCKS.register(
            "spectrometer",
            () -> new SpectrometerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(0.0F)
                    .sound(SoundType.METAL)
                    .pushReaction(PushReaction.BLOCK)
                    .noOcclusion())
    );

    public static final DeferredItem<BlockItem> SPECTROMETER_ITEM =
            ITEMS.registerSimpleBlockItem("spectrometer", SPECTROMETER);

    public static final DeferredBlock<PhotonicGradientGeneratorBlock> PHOTONIC_GRADIENT_GENERATOR = BLOCKS.register(
            "photonic_gradient_generator",
            () -> new PhotonicGradientGeneratorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion())
    );

    public static final DeferredItem<BlockItem> PHOTONIC_GRADIENT_GENERATOR_ITEM =
            ITEMS.registerSimpleBlockItem("photonic_gradient_generator", PHOTONIC_GRADIENT_GENERATOR);

    public static final DeferredBlock<PhotothermalGeneratorBlock> PHOTOTHERMAL_GENERATOR = BLOCKS.register(
            "photothermal_generator",
            () -> new PhotothermalGeneratorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion())
    );

    public static final DeferredItem<BlockItem> PHOTOTHERMAL_GENERATOR_ITEM =
            ITEMS.registerSimpleBlockItem("photothermal_generator", PHOTOTHERMAL_GENERATOR);

    public static final DeferredBlock<ThermalSmelterBlock> THERMAL_SMELTER = BLOCKS.register(
            "thermal_smelter",
            () -> new ThermalSmelterBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.5F, 6.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion())
    );

    public static final DeferredItem<BlockItem> THERMAL_SMELTER_ITEM =
            ITEMS.registerSimpleBlockItem("thermal_smelter", THERMAL_SMELTER);

    public static final DeferredBlock<StrayLightEmitterBlock> STRAY_LIGHT_EMITTER = BLOCKS.register(
            "stray_light_emitter",
            () -> new StrayLightEmitterBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(1.5F, 3.0F)
                            .sound(SoundType.METAL)
                            .noOcclusion(),
                    0,
                    EnvironmentLightSpectra.BASIC_EFFICIENCY)
    );

    public static final DeferredItem<BlockItem> STRAY_LIGHT_EMITTER_ITEM =
            ITEMS.registerSimpleBlockItem("stray_light_emitter", STRAY_LIGHT_EMITTER);

    public static final DeferredBlock<StrayLightEmitterBlock> ADVANCED_STRAY_LIGHT_EMITTER = BLOCKS.register(
            "advanced_stray_light_emitter",
            () -> new StrayLightEmitterBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(2.0F, 4.0F)
                            .sound(SoundType.METAL)
                            .noOcclusion(),
                    1,
                    EnvironmentLightSpectra.ADVANCED_EFFICIENCY)
    );

    public static final DeferredItem<BlockItem> ADVANCED_STRAY_LIGHT_EMITTER_ITEM =
            ITEMS.registerSimpleBlockItem("advanced_stray_light_emitter", ADVANCED_STRAY_LIGHT_EMITTER);

    public static final DeferredBlock<RubyBlock> RUBY_BLOCK = BLOCKS.register(
            "ruby_block",
            () -> new RubyBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_RED)
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.AMETHYST)
                    .lightLevel(RubyBlock::lightLevel)
                    .noOcclusion())
    );

    public static final DeferredItem<BlockItem> RUBY_BLOCK_ITEM =
            ITEMS.registerSimpleBlockItem("ruby_block", RUBY_BLOCK);

    public static final DeferredBlock<net.minecraft.world.level.block.Block> SILVER_BLOCK = BLOCKS.registerSimpleBlock(
            "silver_block",
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_GRAY)
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.METAL)
    );

    public static final DeferredItem<BlockItem> SILVER_BLOCK_ITEM =
            ITEMS.registerSimpleBlockItem("silver_block", SILVER_BLOCK);

    public static final DeferredBlock<SilverGlassBlock> SILVER_GLASS = BLOCKS.register(
            "silver_glass",
            () -> new SilverGlassBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_GRAY)
                    .strength(0.3F)
                    .sound(SoundType.GLASS)
                    .noOcclusion()
                    .isRedstoneConductor((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)
                    .isViewBlocking((state, level, pos) -> false))
    );

    public static final DeferredItem<BlockItem> SILVER_GLASS_ITEM =
            ITEMS.registerSimpleBlockItem("silver_glass", SILVER_GLASS);

    public static final DeferredBlock<Block> SILVER_ORE = oreBlock("silver_ore", MapColor.STONE, 3.0F);
    public static final DeferredItem<BlockItem> SILVER_ORE_ITEM =
            ITEMS.registerSimpleBlockItem("silver_ore", SILVER_ORE);
    public static final DeferredBlock<Block> DEEPSLATE_SILVER_ORE =
            oreBlock("deepslate_silver_ore", MapColor.DEEPSLATE, 4.5F);
    public static final DeferredItem<BlockItem> DEEPSLATE_SILVER_ORE_ITEM =
            ITEMS.registerSimpleBlockItem("deepslate_silver_ore", DEEPSLATE_SILVER_ORE);

    public static final DeferredBlock<Block> RUBY_ORE = oreBlock("ruby_ore", MapColor.STONE, 3.0F);
    public static final DeferredItem<BlockItem> RUBY_ORE_ITEM =
            ITEMS.registerSimpleBlockItem("ruby_ore", RUBY_ORE);
    public static final DeferredBlock<Block> DEEPSLATE_RUBY_ORE =
            oreBlock("deepslate_ruby_ore", MapColor.DEEPSLATE, 4.5F);
    public static final DeferredItem<BlockItem> DEEPSLATE_RUBY_ORE_ITEM =
            ITEMS.registerSimpleBlockItem("deepslate_ruby_ore", DEEPSLATE_RUBY_ORE);

    public static final DeferredBlock<Block> RAW_SILVER_BLOCK =
            storageBlock("raw_silver_block", MapColor.COLOR_LIGHT_GRAY, SoundType.METAL);
    public static final DeferredItem<BlockItem> RAW_SILVER_BLOCK_ITEM =
            ITEMS.registerSimpleBlockItem("raw_silver_block", RAW_SILVER_BLOCK);

    public static final DeferredBlock<Block> RUTILE_ORE = oreBlock("rutile_ore", MapColor.STONE, 3.0F);
    public static final DeferredItem<BlockItem> RUTILE_ORE_ITEM =
            ITEMS.registerSimpleBlockItem("rutile_ore", RUTILE_ORE);
    public static final DeferredBlock<Block> DEEPSLATE_RUTILE_ORE =
            oreBlock("deepslate_rutile_ore", MapColor.DEEPSLATE, 4.5F);
    public static final DeferredItem<BlockItem> DEEPSLATE_RUTILE_ORE_ITEM =
            ITEMS.registerSimpleBlockItem("deepslate_rutile_ore", DEEPSLATE_RUTILE_ORE);

    public static final DeferredBlock<Block> CORUNDUM_ORE = oreBlock("corundum_ore", MapColor.STONE, 3.0F);
    public static final DeferredItem<BlockItem> CORUNDUM_ORE_ITEM =
            ITEMS.registerSimpleBlockItem("corundum_ore", CORUNDUM_ORE);
    public static final DeferredBlock<Block> DEEPSLATE_CORUNDUM_ORE =
            oreBlock("deepslate_corundum_ore", MapColor.DEEPSLATE, 4.5F);
    public static final DeferredItem<BlockItem> DEEPSLATE_CORUNDUM_ORE_ITEM =
            ITEMS.registerSimpleBlockItem("deepslate_corundum_ore", DEEPSLATE_CORUNDUM_ORE);

    public static final DeferredBlock<Block> FLUORITE_ORE = oreBlock("fluorite_ore", MapColor.STONE, 3.0F);
    public static final DeferredItem<BlockItem> FLUORITE_ORE_ITEM =
            ITEMS.registerSimpleBlockItem("fluorite_ore", FLUORITE_ORE);
    public static final DeferredBlock<Block> DEEPSLATE_FLUORITE_ORE =
            oreBlock("deepslate_fluorite_ore", MapColor.DEEPSLATE, 4.5F);
    public static final DeferredItem<BlockItem> DEEPSLATE_FLUORITE_ORE_ITEM =
            ITEMS.registerSimpleBlockItem("deepslate_fluorite_ore", DEEPSLATE_FLUORITE_ORE);

    public static final DeferredBlock<Block> XENOTIME_ORE = oreBlock("xenotime_ore", MapColor.STONE, 3.0F);
    public static final DeferredItem<BlockItem> XENOTIME_ORE_ITEM =
            ITEMS.registerSimpleBlockItem("xenotime_ore", XENOTIME_ORE);
    public static final DeferredBlock<Block> DEEPSLATE_XENOTIME_ORE =
            oreBlock("deepslate_xenotime_ore", MapColor.DEEPSLATE, 4.5F);
    public static final DeferredItem<BlockItem> DEEPSLATE_XENOTIME_ORE_ITEM =
            ITEMS.registerSimpleBlockItem("deepslate_xenotime_ore", DEEPSLATE_XENOTIME_ORE);

    public static final DeferredBlock<Block> RUBY_BEARING_CORUNDUM_ORE =
            oreBlock("ruby_bearing_corundum_ore", MapColor.STONE, 3.0F);
    public static final DeferredItem<BlockItem> RUBY_BEARING_CORUNDUM_ORE_ITEM =
            ITEMS.registerSimpleBlockItem("ruby_bearing_corundum_ore", RUBY_BEARING_CORUNDUM_ORE);

    public static final DeferredBlock<Block> RUTILE_BLOCK =
            storageBlock("rutile_block", MapColor.COLOR_BROWN, SoundType.STONE);
    public static final DeferredItem<BlockItem> RUTILE_BLOCK_ITEM =
            ITEMS.registerSimpleBlockItem("rutile_block", RUTILE_BLOCK);
    public static final DeferredBlock<Block> CORUNDUM_BLOCK =
            storageBlock("corundum_block", MapColor.COLOR_LIGHT_BLUE, SoundType.AMETHYST);
    public static final DeferredItem<BlockItem> CORUNDUM_BLOCK_ITEM =
            ITEMS.registerSimpleBlockItem("corundum_block", CORUNDUM_BLOCK);
    public static final DeferredBlock<Block> FLUORITE_BLOCK =
            storageBlock("fluorite_block", MapColor.COLOR_PURPLE, SoundType.AMETHYST);
    public static final DeferredItem<BlockItem> FLUORITE_BLOCK_ITEM =
            ITEMS.registerSimpleBlockItem("fluorite_block", FLUORITE_BLOCK);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> SPECTRALIZATION_TAB =
            CREATIVE_MODE_TABS.register("spectralization", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.spectralization"))
                    .withTabsBefore(CreativeModeTabs.FUNCTIONAL_BLOCKS)
                    .icon(() -> LENS.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(LENS.get());
                        output.accept(OPTICAL_FIBER_COIL.get());
                        output.accept(PHOSPHOR_DUST.get());
                        output.accept(PHOSPHOR_TUBE.get());
                        output.accept(EMPTY_PAINT_BUCKET.get());
                        output.accept(SILVER_PAINT_BUCKET.get());
                        output.accept(GOLD_PAINT_BUCKET.get());
                        output.accept(SANDPAPER.get());
                        output.accept(ADVANCED_BRUSH.get());
                        output.accept(CREATIVE_BRUSH.get());
                        output.accept(VERITY_HELM_OF_ALL_SEEING_INSIGHT.get());
                        output.accept(VERITY_CUIRASS_OF_COSMIC_INDIFFERENCE.get());
                        output.accept(VERITY_LEGGINGS_OF_ENTROPIC_RELEASE.get());
                        output.accept(VERITY_BOOTS_OF_LIGHT_CONE_TRANSITION.get());
                        output.accept(RUTILE.get());
                        output.accept(TITANIUM_DIOXIDE_DUST.get());
                        output.accept(CORUNDUM.get());
                        output.accept(ALUMINA_DUST.get());
                        output.accept(FLUORITE.get());
                        output.accept(YTTRIUM_OXIDE.get());
                        output.accept(YAG_CRYSTAL.get());
                        output.accept(RUBY.get());
                        output.accept(RAW_SILVER.get());
                        output.accept(SILVER_INGOT.get());
                        output.accept(LENS_HOLDER_ITEM.get());
                        output.accept(FIBER_OPTIC_INTERFACE_ITEM.get());
                        output.accept(FIBER_RELAY_ITEM.get());
                        output.accept(HOLOGRAPHIC_STORAGE_CRYSTAL_ITEM.get());
                        output.accept(MIRROR_ITEM.get());
                        output.accept(DYNAMIC_MIRROR_ITEM.get());
                        output.accept(BEAM_SPLITTER_ITEM.get());
                        output.accept(CREATIVE_LIGHT_SOURCE_ITEM.get());
                        output.accept(CMOS_SENSOR_ITEM.get());
                        output.accept(PASS_THROUGH_SENSOR_ITEM.get());
                        output.accept(BEAM_PROFILER_ITEM.get());
                        output.accept(SPECTROMETER_ITEM.get());
                        output.accept(PHOTONIC_GRADIENT_GENERATOR_ITEM.get());
                        output.accept(PHOTOTHERMAL_GENERATOR_ITEM.get());
                        output.accept(THERMAL_SMELTER_ITEM.get());
                        output.accept(STRAY_LIGHT_EMITTER_ITEM.get());
                        output.accept(ADVANCED_STRAY_LIGHT_EMITTER_ITEM.get());
                        output.accept(RUBY_BLOCK_ITEM.get());
                        output.accept(SILVER_BLOCK_ITEM.get());
                        output.accept(SILVER_GLASS_ITEM.get());
                        output.accept(SILVER_ORE_ITEM.get());
                        output.accept(DEEPSLATE_SILVER_ORE_ITEM.get());
                        output.accept(RUBY_ORE_ITEM.get());
                        output.accept(DEEPSLATE_RUBY_ORE_ITEM.get());
                        output.accept(RAW_SILVER_BLOCK_ITEM.get());
                        output.accept(RUTILE_ORE_ITEM.get());
                        output.accept(DEEPSLATE_RUTILE_ORE_ITEM.get());
                        output.accept(CORUNDUM_ORE_ITEM.get());
                        output.accept(DEEPSLATE_CORUNDUM_ORE_ITEM.get());
                        output.accept(FLUORITE_ORE_ITEM.get());
                        output.accept(DEEPSLATE_FLUORITE_ORE_ITEM.get());
                        output.accept(XENOTIME_ORE_ITEM.get());
                        output.accept(DEEPSLATE_XENOTIME_ORE_ITEM.get());
                        output.accept(RUBY_BEARING_CORUNDUM_ORE_ITEM.get());
                        output.accept(RUTILE_BLOCK_ITEM.get());
                        output.accept(CORUNDUM_BLOCK_ITEM.get());
                        output.accept(FLUORITE_BLOCK_ITEM.get());
                        output.accept(SpectralFluids.MOLTEN_SILVER.bucket().get());
                        output.accept(SpectralFluids.MOLTEN_GOLD.bucket().get());
                        output.accept(SpectralFluids.MOLTEN_COPPER.bucket().get());
                        output.accept(SpectralFluids.MOLTEN_SILICA.bucket().get());
                        output.accept(SpectralFluids.MOLTEN_ALUMINA.bucket().get());
                        output.accept(SpectralFluids.MOLTEN_TITANIUM_DIOXIDE.bucket().get());
                        output.accept(SpectralFluids.MOLTEN_FLUORITE.bucket().get());
                        output.accept(SpectralFluids.MOLTEN_YTTRIUM_OXIDE.bucket().get());
                        output.accept(SpectralFluids.MOLTEN_YAG.bucket().get());
                    })
                    .build());

    private static DeferredBlock<Block> oreBlock(String name, MapColor mapColor, float strength) {
        return BLOCKS.registerSimpleBlock(
                name,
                BlockBehaviour.Properties.of()
                        .mapColor(mapColor)
                        .strength(strength, 3.0F)
                        .requiresCorrectToolForDrops()
                        .sound(mapColor == MapColor.DEEPSLATE ? SoundType.DEEPSLATE : SoundType.STONE)
        );
    }

    private static DeferredBlock<Block> storageBlock(String name, MapColor mapColor, SoundType soundType) {
        return BLOCKS.registerSimpleBlock(
                name,
                BlockBehaviour.Properties.of()
                        .mapColor(mapColor)
                        .strength(3.0F, 6.0F)
                        .requiresCorrectToolForDrops()
                        .sound(soundType)
        );
    }

    public Spectralization(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, SpectralizationConfig.SPEC);

        SpectralFluids.register(modEventBus);
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        RECIPE_SERIALIZERS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        SpectralBlockEntities.register(modEventBus);
        SpectralMenus.register(modEventBus);
        modEventBus.addListener(SpectralNetwork::register);
        modEventBus.addListener(this::registerCapabilities);

        NeoForge.EVENT_BUS.register(this);
        LOGGER.info("Spectralization initialized");
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        SpectralCommands.register(event.getDispatcher());
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                SpectralBlockEntities.PHOTONIC_GRADIENT_GENERATOR.get(),
                (generator, side) -> generator.getEnergyStorage(side)
        );
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                SpectralBlockEntities.PHOTOTHERMAL_GENERATOR.get(),
                (generator, side) -> generator.getEnergyStorage(side)
        );
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                SpectralBlockEntities.PHOTOTHERMAL_GENERATOR.get(),
                (generator, side) -> generator.getFuelItems(side)
        );
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                SpectralBlockEntities.THERMAL_SMELTER.get(),
                (smelter, side) -> smelter.getItems(side)
        );
        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                SpectralBlockEntities.THERMAL_SMELTER.get(),
                (smelter, side) -> smelter.getFluidHandler(side)
        );
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        resetOpticalRuntimeCaches();
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        resetOpticalRuntimeCaches();
    }

    @SubscribeEvent
    public void onLevelUnload(LevelEvent.Unload event) {
        resetOpticalRuntimeCaches(event.getLevel());
    }

    @SubscribeEvent
    public void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof Level level) {
            SurfaceCoatingData.removeAll(level, event.getPos());
        }

        if (!isFiberRelayOnly(event.getLevel().getBlockState(event.getPos()))) {
            OpticalFieldSources.invalidate(event.getLevel());
            OpticalWorldIndex.onBlockPlaced(event.getLevel(), event.getPos());
            OpticalTraceCache.rememberSourceState(event.getLevel(), event.getPos());
            OpticalTraceCache.markChanged(event.getLevel(), event.getPos(), OpticalDirtyKind.STRUCTURE);
            RubyBlockEntity.refreshNear(event.getLevel(), event.getPos());
            OpticalTraceCache.requestIntrinsicSourcesNear(event.getLevel(), event.getPos());
            OpticalNetworkIndex.markDirty(event.getLevel());
        }

        FiberNetworkIndex.onBlockPlaced(event.getLevel(), event.getPos());
    }

    @SubscribeEvent
    public void onBlockBroken(BlockEvent.BreakEvent event) {
        SurfaceCoatingData.removeAll(event.getPlayer().level(), event.getPos());

        if (!isFiberRelayOnly(event.getState())) {
            OpticalFieldSources.invalidate(event.getLevel());
            OpticalWorldIndex.onBlockBroken(event.getLevel(), event.getPos());
            OpticalTraceCache.forgetDormantSource(event.getLevel(), event.getPos());
            OpticalTraceCache.markChanged(event.getLevel(), event.getPos(), OpticalDirtyKind.STRUCTURE);
            OpticalTraceCache.requestIntrinsicSourcesNear(event.getLevel(), event.getPos());
            OpticalNetworkIndex.markDirty(event.getLevel());
        }

        FiberNetworkIndex.onBlockBroken(event.getLevel(), event.getPos());
    }

    private static boolean isFiberRelayOnly(BlockState state) {
        return state.getBlock() instanceof FiberRelayBlock;
    }

    @SubscribeEvent
    public void onNeighborNotified(BlockEvent.NeighborNotifyEvent event) {
        boolean opticalDataChanged = OpticalTraceCache.markChanged(event.getLevel(), event.getPos(), OpticalDirtyKind.PARAMETER);
        opticalDataChanged |= RubyBlockEntity.refreshNear(event.getLevel(), event.getPos());

        if (opticalDataChanged) {
            OpticalTraceCache.requestIntrinsicSourcesNear(event.getLevel(), event.getPos());
        }
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND
                && event.getItemStack().is(Items.SHEARS)
                && FiberShearsInteraction.isFiberNodeTarget(event.getLevel(), event.getPos())) {
            net.minecraft.world.InteractionResult result = event.getLevel() instanceof net.minecraft.server.level.ServerLevel level
                    ? FiberShearsInteraction.useOn(level, event.getEntity(), event.getItemStack(), event.getPos().immutable())
                    : net.minecraft.world.InteractionResult.SUCCESS;
            event.setCancellationResult(result);
            event.setCanceled(true);
            return;
        }

        if (event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND
                || !event.getItemStack().is(Items.BRUSH)
                || !(event.getEntity().getOffhandItem().getItem() instanceof PaintBucketItem)) {
            return;
        }

        SurfaceKey key = new SurfaceKey(event.getPos(), event.getFace());
        var result = SurfaceCoatingInteraction.applyPaint(
                event.getLevel(),
                event.getEntity(),
                key,
                event.getEntity().getOffhandItem()
        );
        event.setCancellationResult(result.consumesAction() ? result : net.minecraft.world.InteractionResult.FAIL);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public void onServerTickPost(ServerTickEvent.Post event) {
        OpticalTraceCache.processQueues(event.getServer());
        FiberOverlayPublisher.publishToInterestedPlayers(event.getServer());
    }

    private static void resetOpticalRuntimeCaches() {
        OpticalTraceCache.clearAll();
        OpticalWorldIndex.clearAll();
        OpticalNetworkIndex.clearAll();
        FiberNetworkIndex.clearAll();
        OpticalFieldSources.clearAll();
        OpticalSpotTracker.clearAll();
    }

    private static void resetOpticalRuntimeCaches(net.minecraft.world.level.LevelAccessor level) {
        OpticalTraceCache.clear(level);
        OpticalWorldIndex.clear(level);
        OpticalNetworkIndex.clear(level);
        FiberNetworkIndex.clear(level);
        OpticalFieldSources.invalidate(level);
        OpticalSpotTracker.clear(level);
    }

    @EventBusSubscriber(modid = Spectralization.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    static class ClientModEvents {
        @SubscribeEvent
        static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerBlockEntityRenderer(SpectralBlockEntities.LENS_HOLDER.get(), LensHolderRenderer::new);
            event.registerBlockEntityRenderer(SpectralBlockEntities.HOLOGRAPHIC_STORAGE_CRYSTAL.get(), HolographicStorageCrystalRenderer::new);
        }

        @SubscribeEvent
        static void registerScreens(RegisterMenuScreensEvent event) {
            event.register(SpectralMenus.CREATIVE_LIGHT_SOURCE.get(), CreativeLightSourceScreen::new);
            event.register(SpectralMenus.COATING_BRUSH.get(), CoatingBrushScreen::new);
            event.register(SpectralMenus.SPECTROMETER.get(), SpectrometerScreen::new);
            event.register(SpectralMenus.PHOTOTHERMAL_GENERATOR.get(), PhotothermalGeneratorScreen::new);
            event.register(SpectralMenus.THERMAL_SMELTER.get(), ThermalSmelterScreen::new);
        }
    }
}
