package io.github.yoglappland.spectralization;

import com.mojang.logging.LogUtils;
import io.github.yoglappland.spectralization.block.AutoCapacitorBlock;
import io.github.yoglappland.spectralization.block.BeamProfilerBlock;
import io.github.yoglappland.spectralization.block.BeamSplitterBlock;
import io.github.yoglappland.spectralization.block.BasicLithographyMachineBlock;
import io.github.yoglappland.spectralization.block.CmosSensorBlock;
import io.github.yoglappland.spectralization.block.MicrolizerAnchorBlock;
import io.github.yoglappland.spectralization.block.MicrolizerCoreBlock;
import io.github.yoglappland.spectralization.block.MicrolizerLightIoPortBlock;
import io.github.yoglappland.spectralization.block.MicrolizedMachineBlock;
import io.github.yoglappland.spectralization.block.CreativeLightSourceBlock;
import io.github.yoglappland.spectralization.block.DynamicBeamSplitterBlock;
import io.github.yoglappland.spectralization.block.DynamicMirrorBlock;
import io.github.yoglappland.spectralization.block.FiberDrawingMachineBlock;
import io.github.yoglappland.spectralization.block.FiberLaserBlock;
import io.github.yoglappland.spectralization.block.FiberOpticInterfaceBlock;
import io.github.yoglappland.spectralization.block.FiberRelayBlock;
import io.github.yoglappland.spectralization.block.HolographicStorageCrystalBlock;
import io.github.yoglappland.spectralization.block.HolographicStorageMainCoreBlock;
import io.github.yoglappland.spectralization.block.HolographicStorageScreenBlock;
import io.github.yoglappland.spectralization.block.HolographicStorageShellBlock;
import io.github.yoglappland.spectralization.block.LedBlock;
import io.github.yoglappland.spectralization.block.LensGrindingBenchBlock;
import io.github.yoglappland.spectralization.block.LensHolderBlock;
import io.github.yoglappland.spectralization.block.MetamaterialDesignTableBlock;
import io.github.yoglappland.spectralization.block.MirrorBlock;
import io.github.yoglappland.spectralization.block.PassThroughSensorBlock;
import io.github.yoglappland.spectralization.block.PhotonicGradientGeneratorBlock;
import io.github.yoglappland.spectralization.block.PhotothermalGeneratorBlock;
import io.github.yoglappland.spectralization.block.PumpMagmaBlock;
import io.github.yoglappland.spectralization.block.RecursiveGeneratorBlock;
import io.github.yoglappland.spectralization.block.RubyBlock;
import io.github.yoglappland.spectralization.block.SilverGlassBlock;
import io.github.yoglappland.spectralization.block.SolarDopingChamberBlock;
import io.github.yoglappland.spectralization.block.SpectrometerBlock;
import io.github.yoglappland.spectralization.block.StrayLightEmitterBlock;
import io.github.yoglappland.spectralization.block.ThermalSmelterBlock;
import io.github.yoglappland.spectralization.config.SpectralizationConfig;
import io.github.yoglappland.spectralization.event.SpectralCommonEvents;
import io.github.yoglappland.spectralization.item.CoatingBrushItem;
import io.github.yoglappland.spectralization.item.CreativeBrushItem;
import io.github.yoglappland.spectralization.item.EnchantableItem;
import io.github.yoglappland.spectralization.item.HolographicStorageShellItem;
import io.github.yoglappland.spectralization.item.LensItem;
import io.github.yoglappland.spectralization.item.MetamaterialTemplateItem;
import io.github.yoglappland.spectralization.item.OpticalFiberCoilItem;
import io.github.yoglappland.spectralization.item.PaintBucketItem;
import io.github.yoglappland.spectralization.item.PhosphorTubeItem;
import io.github.yoglappland.spectralization.item.RecursiveGeneratorBlockItem;
import io.github.yoglappland.spectralization.item.SandpaperItem;
import io.github.yoglappland.spectralization.item.SingularMaterialItem;
import io.github.yoglappland.spectralization.item.StrawberryRodItem;
import io.github.yoglappland.spectralization.network.SpectralNetwork;
import io.github.yoglappland.spectralization.optics.EnvironmentLightSpectra;
import io.github.yoglappland.spectralization.optics.surface.SurfaceTreatmentKind;
import io.github.yoglappland.spectralization.recipe.AdvancedBrushLoadingRecipe;
import io.github.yoglappland.spectralization.recipe.BasicLithographyMachineCraftingRecipe;
import io.github.yoglappland.spectralization.recipe.SingularMaterialInfusionRecipe;
import io.github.yoglappland.spectralization.registry.SpectralCapabilities;
import io.github.yoglappland.spectralization.registry.SpectralCreativeTabItems;
import io.github.yoglappland.spectralization.registry.SpectralRegistryBootstrap;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
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
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<SingularMaterialInfusionRecipe>>
            SINGULAR_MATERIAL_INFUSION_SERIALIZER = RECIPE_SERIALIZERS.register(
                    "singular_material_infusion",
                    () -> new SimpleCraftingRecipeSerializer<>(SingularMaterialInfusionRecipe::new)
            );
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<BasicLithographyMachineCraftingRecipe>>
            BASIC_LITHOGRAPHY_MACHINE_CRAFTING_SERIALIZER = RECIPE_SERIALIZERS.register(
                    "basic_lithography_machine_crafting",
                    () -> new SimpleCraftingRecipeSerializer<>(BasicLithographyMachineCraftingRecipe::new)
            );

    public static final DeferredItem<LensItem> LENS = ITEMS.register(
            "lens",
            () -> new LensItem(new Item.Properties(), 12)
    );
    public static final DeferredItem<MetamaterialTemplateItem> STANDARD_METAMATERIAL_TEMPLATE = ITEMS.register(
            "standard_metamaterial_template",
            () -> new MetamaterialTemplateItem(new Item.Properties())
    );
    public static final DeferredItem<MetamaterialTemplateItem> CUSTOM_METAMATERIAL_TEMPLATE = ITEMS.register(
            "custom_metamaterial_template",
            () -> new MetamaterialTemplateItem(new Item.Properties())
    );
    public static final DeferredItem<Item> BASIC_MASK =
            ITEMS.registerSimpleItem("basic_mask", new Item.Properties());
    public static final DeferredItem<Item> GRATING_MASK =
            ITEMS.registerSimpleItem("grating_mask", new Item.Properties());
    public static final DeferredItem<Item> WAVEGUIDE_MASK =
            ITEMS.registerSimpleItem("waveguide_mask", new Item.Properties());
    public static final DeferredItem<Item> CIRCUIT_MASK =
            ITEMS.registerSimpleItem("circuit_mask", new Item.Properties());
    public static final DeferredItem<Item> FIBER_MOLD =
            ITEMS.registerSimpleItem("fiber_mold", new Item.Properties());
    public static final DeferredItem<Item> SINGLE_MODE_FIBER_MOLD =
            ITEMS.registerSimpleItem("single_mode_fiber_mold", new Item.Properties());
    public static final DeferredItem<Item> PLATE_MOLD =
            ITEMS.registerSimpleItem("plate_mold", new Item.Properties());
    public static final DeferredItem<Item> WIRE_MOLD =
            ITEMS.registerSimpleItem("wire_mold", new Item.Properties());
    public static final DeferredItem<Item> CRYSTAL_MOLD =
            ITEMS.registerSimpleItem("crystal_mold", new Item.Properties());
    public static final DeferredItem<SingularMaterialItem> SINGULAR_MATERIAL = ITEMS.register(
            "singular_material",
            () -> new SingularMaterialItem(new Item.Properties())
    );
    public static final DeferredItem<Item> BLANK_WAFER =
            ITEMS.registerSimpleItem("blank_wafer", new Item.Properties());
    public static final DeferredItem<Item> HOLOGRAPHIC_CRYSTAL =
            ITEMS.registerSimpleItem("holographic_crystal", new Item.Properties());
    public static final DeferredItem<Item> BASIC_DIODE =
            ITEMS.registerSimpleItem("basic_diode", new Item.Properties());
    public static final DeferredItem<Item> BASIC_MACHINE_CORE =
            ITEMS.registerSimpleItem("basic_machine_core", new Item.Properties());
    public static final DeferredItem<Item> PRIMITIVE_CIRCUIT_BOARD =
            ITEMS.registerSimpleItem("primitive_circuit_board", new Item.Properties());
    public static final DeferredItem<Item> ADVANCED_CIRCUIT_BOARD =
            ITEMS.registerSimpleItem("advanced_circuit_board", new Item.Properties());
    public static final DeferredItem<Item> PRECISION_CIRCUIT_BOARD =
            ITEMS.registerSimpleItem("precision_circuit_board", new Item.Properties());
    public static final DeferredItem<OpticalFiberCoilItem> OPTICAL_FIBER_COIL = ITEMS.register(
            "optical_fiber_coil",
            () -> new OpticalFiberCoilItem(new Item.Properties().stacksTo(1))
    );
    public static final DeferredItem<OpticalFiberCoilItem> SINGLE_MODE_FIBER_COIL = ITEMS.register(
            "single_mode_fiber_coil",
            () -> new OpticalFiberCoilItem(new Item.Properties().stacksTo(1), true)
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
    public static final DeferredItem<Item> GRINDING_KNIFE =
            ITEMS.register("grinding_knife", () -> new EnchantableItem(new Item.Properties().stacksTo(1), 8));
    public static final DeferredItem<Item> DIAMOND_GRINDING_KNIFE =
            ITEMS.register("diamond_grinding_knife", () -> new EnchantableItem(new Item.Properties().stacksTo(1), 12));
    public static final DeferredItem<Item> OBSIDIAN_GRINDING_KNIFE =
            ITEMS.register("obsidian_grinding_knife", () -> new EnchantableItem(new Item.Properties().stacksTo(1), 10));
    public static final DeferredItem<Item> CERAMIC_GRINDING_KNIFE =
            ITEMS.register("ceramic_grinding_knife", () -> new EnchantableItem(new Item.Properties().stacksTo(1), 16));
    public static final DeferredItem<Item> CLEAR_GRINDING_KNIFE =
            ITEMS.register("clear_grinding_knife", () -> new EnchantableItem(new Item.Properties().stacksTo(1), 20));
    public static final DeferredItem<Item> GRINDING_TOOL =
            ITEMS.registerSimpleItem("grinding_tool", new Item.Properties().stacksTo(1));
    public static final DeferredItem<CoatingBrushItem> ADVANCED_BRUSH = ITEMS.register(
            "advanced_brush",
            () -> new CoatingBrushItem(new Item.Properties().stacksTo(1))
    );
    public static final DeferredItem<CreativeBrushItem> CREATIVE_BRUSH = ITEMS.register(
            "creative_brush",
            () -> new CreativeBrushItem(new Item.Properties().stacksTo(1))
    );
    public static final DeferredItem<StrawberryRodItem> ROD_OF_STRAWBERRY = ITEMS.register(
            "rod_of_strawberry",
            () -> new StrawberryRodItem(new Item.Properties().stacksTo(1))
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
    public static final DeferredItem<Item> ALUMINA_CERAMIC_PLATE =
            ITEMS.registerSimpleItem("alumina_ceramic_plate", new Item.Properties());
    public static final DeferredItem<Item> FLUORITE = ITEMS.registerSimpleItem("fluorite", new Item.Properties());
    public static final DeferredItem<Item> ND_FLUORITE_CRYSTAL =
            ITEMS.registerSimpleItem("nd_fluorite_crystal", new Item.Properties());
    public static final DeferredItem<Item> YB_FLUORITE_CRYSTAL =
            ITEMS.registerSimpleItem("yb_fluorite_crystal", new Item.Properties());
    public static final DeferredItem<Item> ER_FLUORITE_CRYSTAL =
            ITEMS.registerSimpleItem("er_fluorite_crystal", new Item.Properties());
    public static final DeferredItem<Item> CE_FLUORITE_CRYSTAL =
            ITEMS.registerSimpleItem("ce_fluorite_crystal", new Item.Properties());
    public static final DeferredItem<Item> YTTRIUM_OXIDE =
            ITEMS.registerSimpleItem("yttrium_oxide", new Item.Properties());
    public static final DeferredItem<Item> YAG_CRYSTAL =
            ITEMS.registerSimpleItem("yag_crystal", new Item.Properties());
    public static final DeferredItem<Item> ND_YAG_CRYSTAL =
            ITEMS.registerSimpleItem("nd_yag_crystal", new Item.Properties());
    public static final DeferredItem<Item> YB_YAG_CRYSTAL =
            ITEMS.registerSimpleItem("yb_yag_crystal", new Item.Properties());
    public static final DeferredItem<Item> ER_YAG_CRYSTAL =
            ITEMS.registerSimpleItem("er_yag_crystal", new Item.Properties());
    public static final DeferredItem<Item> CE_YAG_CRYSTAL =
            ITEMS.registerSimpleItem("ce_yag_crystal", new Item.Properties());
    public static final DeferredItem<Item> RUBY = ITEMS.registerSimpleItem("ruby", new Item.Properties());
    public static final DeferredItem<Item> ND_DOPED_SILICA =
            ITEMS.registerSimpleItem("nd_doped_silica", new Item.Properties());
    public static final DeferredItem<Item> YB_DOPED_SILICA =
            ITEMS.registerSimpleItem("yb_doped_silica", new Item.Properties());
    public static final DeferredItem<Item> ER_DOPED_SILICA =
            ITEMS.registerSimpleItem("er_doped_silica", new Item.Properties());
    public static final DeferredItem<Item> CE_DOPED_SILICA =
            ITEMS.registerSimpleItem("ce_doped_silica", new Item.Properties());
    public static final DeferredItem<Item> SILICON =
            ITEMS.registerSimpleItem("silicon", new Item.Properties());
    public static final DeferredItem<Item> BORON_DOPED_SILICON =
            ITEMS.registerSimpleItem("boron_doped_silicon", new Item.Properties());
    public static final DeferredItem<Item> PHOSPHORUS_DOPED_SILICON =
            ITEMS.registerSimpleItem("phosphorus_doped_silicon", new Item.Properties());
    public static final DeferredItem<Item> ARSENIC_DOPED_SILICON =
            ITEMS.registerSimpleItem("arsenic_doped_silicon", new Item.Properties());
    public static final DeferredItem<Item> ERBIUM_DOPED_SILICON =
            ITEMS.registerSimpleItem("erbium_doped_silicon", new Item.Properties());
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

    public static final DeferredBlock<LensGrindingBenchBlock> LENS_GRINDING_BENCH = BLOCKS.register(
            "lens_grinding_bench",
            () -> new LensGrindingBenchBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0F, 6.0F)
                    .sound(SoundType.STONE)
                    .noOcclusion())
    );

    public static final DeferredItem<BlockItem> LENS_GRINDING_BENCH_ITEM =
            ITEMS.registerSimpleBlockItem("lens_grinding_bench", LENS_GRINDING_BENCH);

    public static final DeferredBlock<MetamaterialDesignTableBlock> METAMATERIAL_DESIGN_TABLE = BLOCKS.register(
            "metamaterial_design_table",
            () -> new MetamaterialDesignTableBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .isRedstoneConductor((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)
                    .isViewBlocking((state, level, pos) -> false))
    );

    public static final DeferredItem<BlockItem> METAMATERIAL_DESIGN_TABLE_ITEM =
            ITEMS.registerSimpleBlockItem("metamaterial_design_table", METAMATERIAL_DESIGN_TABLE);

    public static final DeferredBlock<BasicLithographyMachineBlock> BASIC_LITHOGRAPHY_MACHINE = BLOCKS.register(
            "basic_lithography_machine",
            () -> new BasicLithographyMachineBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .isRedstoneConductor((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)
                    .isViewBlocking((state, level, pos) -> false))
    );

    public static final DeferredItem<BlockItem> BASIC_LITHOGRAPHY_MACHINE_ITEM =
            ITEMS.registerSimpleBlockItem("basic_lithography_machine", BASIC_LITHOGRAPHY_MACHINE);

    public static final DeferredBlock<SolarDopingChamberBlock> SOLAR_DOPING_CHAMBER = BLOCKS.register(
            "solar_doping_chamber",
            () -> new SolarDopingChamberBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .isRedstoneConductor((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)
                    .isViewBlocking((state, level, pos) -> false))
    );

    public static final DeferredItem<BlockItem> SOLAR_DOPING_CHAMBER_ITEM =
            ITEMS.registerSimpleBlockItem("solar_doping_chamber", SOLAR_DOPING_CHAMBER);

    public static final DeferredBlock<SolarDopingChamberBlock> DIMENSION_DOPING_CHAMBER = BLOCKS.register(
            "dimension_doping_chamber",
            () -> new SolarDopingChamberBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(2.0F, 6.0F)
                            .sound(SoundType.METAL)
                            .noOcclusion()
                            .isRedstoneConductor((state, level, pos) -> false)
                            .isSuffocating((state, level, pos) -> false)
                            .isViewBlocking((state, level, pos) -> false),
                    "dimension_doping",
                    "container.spectralization.dimension_doping_chamber"
            )
    );

    public static final DeferredItem<BlockItem> DIMENSION_DOPING_CHAMBER_ITEM =
            ITEMS.registerSimpleBlockItem("dimension_doping_chamber", DIMENSION_DOPING_CHAMBER);

    public static final DeferredBlock<FiberDrawingMachineBlock> FIBER_DRAWING_MACHINE = BLOCKS.register(
            "fiber_drawing_machine",
            () -> new FiberDrawingMachineBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .isRedstoneConductor((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)
                    .isViewBlocking((state, level, pos) -> false))
    );

    public static final DeferredItem<BlockItem> FIBER_DRAWING_MACHINE_ITEM =
            ITEMS.registerSimpleBlockItem("fiber_drawing_machine", FIBER_DRAWING_MACHINE);

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

    public static final DeferredBlock<FiberLaserBlock> FIBER_LASER = BLOCKS.register(
            "fiber_laser",
            () -> new FiberLaserBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .strength(2.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .isRedstoneConductor((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)
                    .isViewBlocking((state, level, pos) -> false))
    );

    public static final DeferredItem<BlockItem> FIBER_LASER_ITEM =
            ITEMS.registerSimpleBlockItem("fiber_laser", FIBER_LASER);

    public static final DeferredBlock<HolographicStorageShellBlock> HOLOGRAPHIC_STORAGE_SHELL = BLOCKS.register(
            "holographic_storage_shell",
            () -> new HolographicStorageShellBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .strength(1.5F, 6.0F)
                    .sound(SoundType.AMETHYST)
                    .noOcclusion())
    );

    public static final DeferredItem<BlockItem> HOLOGRAPHIC_STORAGE_SHELL_ITEM =
            ITEMS.register("holographic_storage_shell", () ->
                    new HolographicStorageShellItem(HOLOGRAPHIC_STORAGE_SHELL.get(), new Item.Properties()));

    public static final DeferredBlock<HolographicStorageShellBlock> STABLE_HOLOGRAPHIC_STORAGE_SHELL = BLOCKS.register(
            "stable_holographic_storage_shell",
            () -> new HolographicStorageShellBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .strength(1.5F, 6.0F)
                    .sound(SoundType.AMETHYST)
                    .lightLevel(state -> 6)
                    .noOcclusion(), true)
    );

    public static final DeferredItem<BlockItem> STABLE_HOLOGRAPHIC_STORAGE_SHELL_ITEM =
            ITEMS.register("stable_holographic_storage_shell", () ->
                    new HolographicStorageShellItem(STABLE_HOLOGRAPHIC_STORAGE_SHELL.get(), new Item.Properties()));

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

    public static final DeferredBlock<HolographicStorageMainCoreBlock> HOLOGRAPHIC_STORAGE_MAIN_CORE = BLOCKS.register(
            "holographic_storage_main_core",
            () -> new HolographicStorageMainCoreBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .strength(1.5F, 6.0F)
                    .sound(SoundType.AMETHYST)
                    .lightLevel(state -> 10)
                    .noOcclusion())
    );

    public static final DeferredItem<BlockItem> HOLOGRAPHIC_STORAGE_MAIN_CORE_ITEM =
            ITEMS.registerSimpleBlockItem("holographic_storage_main_core", HOLOGRAPHIC_STORAGE_MAIN_CORE);

    public static final DeferredBlock<HolographicStorageScreenBlock> HOLOGRAPHIC_STORAGE_SCREEN = BLOCKS.register(
            "holographic_storage_screen",
            () -> new HolographicStorageScreenBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .strength(1.5F, 6.0F)
                    .sound(SoundType.AMETHYST)
                    .noOcclusion())
    );

    public static final DeferredItem<BlockItem> HOLOGRAPHIC_STORAGE_SCREEN_ITEM =
            ITEMS.registerSimpleBlockItem("holographic_storage_screen", HOLOGRAPHIC_STORAGE_SCREEN);

    public static final DeferredBlock<MicrolizerAnchorBlock> MICROLIZER_ANCHOR = BLOCKS.register(
            "microlizer_anchor",
            () -> new MicrolizerAnchorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .strength(3.0F, 9.0F)
                    .sound(SoundType.METAL)
                    .lightLevel(state -> 15))
    );
    public static final DeferredItem<BlockItem> MICROLIZER_ANCHOR_ITEM =
            ITEMS.registerSimpleBlockItem("microlizer_anchor", MICROLIZER_ANCHOR);

    public static final DeferredBlock<MicrolizerCoreBlock> MICROLIZER_CORE = BLOCKS.register(
            "microlizer_core",
            () -> new MicrolizerCoreBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .strength(4.0F, 12.0F)
                    .sound(SoundType.METAL)
                    .lightLevel(state -> 15))
    );
    public static final DeferredItem<BlockItem> MICROLIZER_CORE_ITEM =
            ITEMS.registerSimpleBlockItem("microlizer_core", MICROLIZER_CORE);

    public static final DeferredBlock<MicrolizerLightIoPortBlock> MICROLIZER_LIGHT_IO_PORT = BLOCKS.register(
            "microlizer_light_io_port",
            () -> new MicrolizerLightIoPortBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .strength(3.0F, 9.0F)
                    .sound(SoundType.METAL))
    );
    public static final DeferredItem<BlockItem> MICROLIZER_LIGHT_IO_PORT_ITEM =
            ITEMS.registerSimpleBlockItem("microlizer_light_io_port", MICROLIZER_LIGHT_IO_PORT);

    public static final DeferredBlock<Block> MICROLIZER_BLOCK = BLOCKS.registerSimpleBlock(
            "microlizer_block",
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0F, 9.0F)
                    .sound(SoundType.METAL)
    );
    public static final DeferredItem<BlockItem> MICROLIZER_BLOCK_ITEM =
            ITEMS.registerSimpleBlockItem("microlizer_block", MICROLIZER_BLOCK);

    public static final DeferredBlock<TransparentBlock> MICROLIZER_GLASS = glassBlock(
            "microlizer_glass",
            MapColor.COLOR_LIGHT_BLUE
    );
    public static final DeferredItem<BlockItem> MICROLIZER_GLASS_ITEM =
            ITEMS.registerSimpleBlockItem("microlizer_glass", MICROLIZER_GLASS);

    public static final DeferredBlock<MicrolizedMachineBlock> MICROLIZED_MACHINE = BLOCKS.register(
            "microlized_machine",
            () -> new MicrolizedMachineBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .strength(4.0F, 12.0F)
                    .sound(SoundType.METAL))
    );
    public static final DeferredItem<BlockItem> MICROLIZED_MACHINE_ITEM =
            ITEMS.registerSimpleBlockItem("microlized_machine", MICROLIZED_MACHINE);

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

    public static final DeferredBlock<DynamicBeamSplitterBlock> DYNAMIC_BEAM_SPLITTER = BLOCKS.register(
            "dynamic_beam_splitter",
            () -> new DynamicBeamSplitterBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(0.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion())
    );

    public static final DeferredItem<BlockItem> DYNAMIC_BEAM_SPLITTER_ITEM =
            ITEMS.registerSimpleBlockItem("dynamic_beam_splitter", DYNAMIC_BEAM_SPLITTER);

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

    public static final DeferredBlock<PhotonicGradientGeneratorBlock> LIGHT_SOURCE_GENERATOR = BLOCKS.register(
            "light_source_generator",
            () -> new PhotonicGradientGeneratorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .lightLevel(state -> state.getValue(PhotonicGradientGeneratorBlock.ACTIVE) ? 10 : 0)
                    .noOcclusion())
    );

    public static final DeferredItem<BlockItem> LIGHT_SOURCE_GENERATOR_ITEM =
            ITEMS.registerSimpleBlockItem("light_source_generator", LIGHT_SOURCE_GENERATOR);

    public static final DeferredBlock<PhotothermalGeneratorBlock> PHOTOTHERMAL_GENERATOR = BLOCKS.register(
            "photothermal_generator",
            () -> new PhotothermalGeneratorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .lightLevel(state -> state.getValue(PhotothermalGeneratorBlock.ACTIVE) ? 10 : 0)
                    .noOcclusion())
    );

    public static final DeferredItem<BlockItem> PHOTOTHERMAL_GENERATOR_ITEM =
            ITEMS.registerSimpleBlockItem("photothermal_generator", PHOTOTHERMAL_GENERATOR);

    public static final DeferredBlock<RecursiveGeneratorBlock> RECURSIVE_GENERATOR = BLOCKS.register(
            "recursive_generator",
            () -> new RecursiveGeneratorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .lightLevel(state -> state.getValue(RecursiveGeneratorBlock.ACTIVE) ? 10 : 0)
                    .noOcclusion())
    );

    public static final DeferredItem<RecursiveGeneratorBlockItem> RECURSIVE_GENERATOR_ITEM = ITEMS.register(
            "recursive_generator",
            () -> new RecursiveGeneratorBlockItem(RECURSIVE_GENERATOR.get(), new Item.Properties())
    );

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

    public static final DeferredBlock<LedBlock> BASIC_LED = BLOCKS.register(
            "basic_led",
            () -> new LedBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_LIGHT_GRAY)
                            .strength(0.4F, 0.4F)
                            .sound(SoundType.GLASS)
                            .lightLevel(state -> 10)
                            .noOcclusion()
            )
    );

    public static final DeferredItem<BlockItem> BASIC_LED_ITEM =
            ITEMS.registerSimpleBlockItem("basic_led", BASIC_LED);

    public static final DeferredBlock<LedBlock> ADVANCED_LED = BLOCKS.register(
            "advanced_led",
            () -> new LedBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_LIGHT_BLUE)
                            .strength(0.5F, 0.5F)
                            .sound(SoundType.GLASS)
                            .lightLevel(state -> 15)
                            .noOcclusion()
            )
    );

    public static final DeferredItem<BlockItem> ADVANCED_LED_ITEM =
            ITEMS.registerSimpleBlockItem("advanced_led", ADVANCED_LED);

    public static final DeferredBlock<AutoCapacitorBlock> AUTO_CAPACITOR = BLOCKS.register(
            "auto_capacitor",
            () -> new AutoCapacitorBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(2.0F, 6.0F)
                            .sound(SoundType.METAL)
                            .noOcclusion()
                            .isRedstoneConductor((state, level, pos) -> false)
                            .isSuffocating((state, level, pos) -> false)
                            .isViewBlocking((state, level, pos) -> false)
            )
    );

    public static final DeferredItem<BlockItem> AUTO_CAPACITOR_ITEM =
            ITEMS.registerSimpleBlockItem("auto_capacitor", AUTO_CAPACITOR);

    public static final DeferredBlock<PumpMagmaBlock> PUMP_MAGMA_BLOCK = BLOCKS.register(
            "pump_magma_block",
            () -> new PumpMagmaBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_ORANGE)
                            .strength(1.5F, 6.0F)
                            .sound(SoundType.STONE)
                            .lightLevel(state -> state.getValue(PumpMagmaBlock.ACTIVE) ? 10 : 0)
            )
    );

    public static final DeferredItem<BlockItem> PUMP_MAGMA_BLOCK_ITEM =
            ITEMS.registerSimpleBlockItem("pump_magma_block", PUMP_MAGMA_BLOCK);

    public static final DeferredBlock<Block> BASIC_MACHINE_BLOCK = BLOCKS.registerSimpleBlock(
            "basic_machine_block",
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0F, 6.0F)
                    .sound(SoundType.METAL)
    );

    public static final DeferredItem<BlockItem> BASIC_MACHINE_BLOCK_ITEM =
            ITEMS.registerSimpleBlockItem("basic_machine_block", BASIC_MACHINE_BLOCK);

    public static final DeferredBlock<Block> ADVANCED_MACHINE_BLOCK = BLOCKS.registerSimpleBlock(
            "advanced_machine_block",
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.5F, 6.0F)
                    .sound(SoundType.METAL)
    );

    public static final DeferredItem<BlockItem> ADVANCED_MACHINE_BLOCK_ITEM =
            ITEMS.registerSimpleBlockItem("advanced_machine_block", ADVANCED_MACHINE_BLOCK);

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

    public static final DeferredBlock<Block> BORAX = BLOCKS.registerSimpleBlock(
            "borax",
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.SAND)
                    .strength(0.6F)
                    .sound(SoundType.SAND)
    );
    public static final DeferredItem<BlockItem> BORAX_ITEM =
            ITEMS.registerSimpleBlockItem("borax", BORAX);

    public static final DeferredBlock<TransparentBlock> QUARTZ_GLASS =
            glassBlock("quartz_glass", MapColor.COLOR_LIGHT_BLUE);
    public static final DeferredItem<BlockItem> QUARTZ_GLASS_ITEM =
            ITEMS.registerSimpleBlockItem("quartz_glass", QUARTZ_GLASS);
    public static final DeferredBlock<TransparentBlock> BOROSILICATE_GLASS =
            glassBlock("borosilicate_glass", MapColor.COLOR_LIGHT_BLUE);
    public static final DeferredItem<BlockItem> BOROSILICATE_GLASS_ITEM =
            ITEMS.registerSimpleBlockItem("borosilicate_glass", BOROSILICATE_GLASS);
    public static final DeferredBlock<TransparentBlock> CROWN_GLASS =
            glassBlock("crown_glass", MapColor.COLOR_LIGHT_GRAY);
    public static final DeferredItem<BlockItem> CROWN_GLASS_ITEM =
            ITEMS.registerSimpleBlockItem("crown_glass", CROWN_GLASS);
    public static final DeferredBlock<TransparentBlock> FLINT_GLASS =
            glassBlock("flint_glass", MapColor.COLOR_BROWN);
    public static final DeferredItem<BlockItem> FLINT_GLASS_ITEM =
            ITEMS.registerSimpleBlockItem("flint_glass", FLINT_GLASS);
    public static final DeferredBlock<TransparentBlock> HEAVY_GLASS =
            glassBlock("heavy_glass", MapColor.COLOR_LIGHT_GRAY);
    public static final DeferredItem<BlockItem> HEAVY_GLASS_ITEM =
            ITEMS.registerSimpleBlockItem("heavy_glass", HEAVY_GLASS);

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
                    .displayItems((parameters, output) -> SpectralCreativeTabItems.accept(output))
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

    private static DeferredBlock<TransparentBlock> glassBlock(String name, MapColor mapColor) {
        return BLOCKS.register(
                name,
                () -> new TransparentBlock(
                        BlockBehaviour.Properties.of()
                                .mapColor(mapColor)
                                .strength(0.3F)
                                .sound(SoundType.GLASS)
                                .noOcclusion()
                                .isRedstoneConductor((state, level, pos) -> false)
                                .isSuffocating((state, level, pos) -> false)
                                .isViewBlocking((state, level, pos) -> false)
                )
        );
    }

    public Spectralization(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, SpectralizationConfig.SPEC);

        SpectralRegistryBootstrap.register(modEventBus);
        modEventBus.addListener(SpectralNetwork::register);
        modEventBus.addListener(SpectralCapabilities::register);

        NeoForge.EVENT_BUS.register(new SpectralCommonEvents());
        LOGGER.info("Spectralization initialized");
    }
}
