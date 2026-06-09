package io.github.yoglappland.spectralization;

import com.mojang.logging.LogUtils;
import io.github.yoglappland.spectralization.block.BeamSplitterBlock;
import io.github.yoglappland.spectralization.block.CmosSensorBlock;
import io.github.yoglappland.spectralization.block.CreativeLightSourceBlock;
import io.github.yoglappland.spectralization.block.DynamicMirrorBlock;
import io.github.yoglappland.spectralization.block.LensHolderBlock;
import io.github.yoglappland.spectralization.block.MirrorBlock;
import io.github.yoglappland.spectralization.block.PassThroughSensorBlock;
import io.github.yoglappland.spectralization.block.RubyBlock;
import io.github.yoglappland.spectralization.block.SilverGlassBlock;
import io.github.yoglappland.spectralization.client.renderer.LensHolderRenderer;
import io.github.yoglappland.spectralization.client.screen.CoatingBrushScreen;
import io.github.yoglappland.spectralization.client.screen.CreativeLightSourceScreen;
import io.github.yoglappland.spectralization.command.SpectralCommands;
import io.github.yoglappland.spectralization.config.SpectralizationConfig;
import io.github.yoglappland.spectralization.item.CoatingBrushItem;
import io.github.yoglappland.spectralization.item.CreativeBrushItem;
import io.github.yoglappland.spectralization.item.PaintBucketItem;
import io.github.yoglappland.spectralization.item.PhosphorTubeItem;
import io.github.yoglappland.spectralization.item.SandpaperItem;
import io.github.yoglappland.spectralization.item.SurfaceCoatingInteraction;
import io.github.yoglappland.spectralization.network.SpectralNetwork;
import io.github.yoglappland.spectralization.optics.cache.OpticalDirtyKind;
import io.github.yoglappland.spectralization.optics.cache.OpticalTraceCache;
import io.github.yoglappland.spectralization.optics.field.OpticalFieldSources;
import io.github.yoglappland.spectralization.optics.surface.SurfaceCoatingData;
import io.github.yoglappland.spectralization.optics.surface.SurfaceKey;
import io.github.yoglappland.spectralization.optics.surface.SurfaceTreatmentKind;
import io.github.yoglappland.spectralization.optics.topology.OpticalNetworkIndex;
import io.github.yoglappland.spectralization.optics.world.OpticalWorldIndex;
import io.github.yoglappland.spectralization.recipe.AdvancedBrushLoadingRecipe;
import io.github.yoglappland.spectralization.registry.SpectralBlockEntities;
import io.github.yoglappland.spectralization.registry.SpectralMenus;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
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
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
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

    public static final DeferredItem<Item> LENS = ITEMS.registerSimpleItem("lens", new Item.Properties());
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

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> SPECTRALIZATION_TAB =
            CREATIVE_MODE_TABS.register("spectralization", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.spectralization"))
                    .withTabsBefore(CreativeModeTabs.FUNCTIONAL_BLOCKS)
                    .icon(() -> LENS.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(LENS.get());
                        output.accept(PHOSPHOR_DUST.get());
                        output.accept(PHOSPHOR_TUBE.get());
                        output.accept(EMPTY_PAINT_BUCKET.get());
                        output.accept(SILVER_PAINT_BUCKET.get());
                        output.accept(GOLD_PAINT_BUCKET.get());
                        output.accept(SANDPAPER.get());
                        output.accept(ADVANCED_BRUSH.get());
                        output.accept(CREATIVE_BRUSH.get());
                        output.accept(LENS_HOLDER_ITEM.get());
                        output.accept(MIRROR_ITEM.get());
                        output.accept(DYNAMIC_MIRROR_ITEM.get());
                        output.accept(BEAM_SPLITTER_ITEM.get());
                        output.accept(CREATIVE_LIGHT_SOURCE_ITEM.get());
                        output.accept(CMOS_SENSOR_ITEM.get());
                        output.accept(PASS_THROUGH_SENSOR_ITEM.get());
                        output.accept(RUBY_BLOCK_ITEM.get());
                        output.accept(SILVER_BLOCK_ITEM.get());
                        output.accept(SILVER_GLASS_ITEM.get());
                    })
                    .build());

    public Spectralization(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, SpectralizationConfig.SPEC);

        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        RECIPE_SERIALIZERS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        SpectralBlockEntities.register(modEventBus);
        SpectralMenus.register(modEventBus);
        modEventBus.addListener(SpectralNetwork::register);

        NeoForge.EVENT_BUS.register(this);
        LOGGER.info("Spectralization initialized");
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        SpectralCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof Level level) {
            SurfaceCoatingData.removeAll(level, event.getPos());
        }
        OpticalFieldSources.invalidate(event.getLevel());
        OpticalWorldIndex.onBlockPlaced(event.getLevel(), event.getPos());
        OpticalTraceCache.markChanged(event.getLevel(), event.getPos(), OpticalDirtyKind.STRUCTURE);
        OpticalNetworkIndex.markDirty(event.getLevel());
    }

    @SubscribeEvent
    public void onBlockBroken(BlockEvent.BreakEvent event) {
        SurfaceCoatingData.removeAll(event.getPlayer().level(), event.getPos());
        OpticalFieldSources.invalidate(event.getLevel());
        OpticalWorldIndex.onBlockBroken(event.getLevel(), event.getPos());
        OpticalTraceCache.markChanged(event.getLevel(), event.getPos(), OpticalDirtyKind.STRUCTURE);
        OpticalNetworkIndex.markDirty(event.getLevel());
    }

    @SubscribeEvent
    public void onNeighborNotified(BlockEvent.NeighborNotifyEvent event) {
        OpticalTraceCache.markChanged(event.getLevel(), event.getPos(), OpticalDirtyKind.PARAMETER);
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
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
    }

    @EventBusSubscriber(modid = Spectralization.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    static class ClientModEvents {
        @SubscribeEvent
        static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerBlockEntityRenderer(SpectralBlockEntities.LENS_HOLDER.get(), LensHolderRenderer::new);
        }

        @SubscribeEvent
        static void registerScreens(RegisterMenuScreensEvent event) {
            event.register(SpectralMenus.CREATIVE_LIGHT_SOURCE.get(), CreativeLightSourceScreen::new);
            event.register(SpectralMenus.COATING_BRUSH.get(), CoatingBrushScreen::new);
        }
    }
}
