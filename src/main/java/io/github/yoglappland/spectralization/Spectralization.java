package io.github.yoglappland.spectralization;

import com.mojang.logging.LogUtils;
import io.github.yoglappland.spectralization.block.CreativeLightSourceBlock;
import io.github.yoglappland.spectralization.block.LensHolderBlock;
import io.github.yoglappland.spectralization.block.MirrorBlock;
import io.github.yoglappland.spectralization.block.PhotodetectorBlock;
import io.github.yoglappland.spectralization.client.renderer.LensHolderRenderer;
import io.github.yoglappland.spectralization.command.SpectralCommands;
import io.github.yoglappland.spectralization.item.PhosphorTubeItem;
import io.github.yoglappland.spectralization.registry.SpectralBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
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
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final DeferredItem<Item> LENS = ITEMS.registerSimpleItem("lens", new Item.Properties());
    public static final DeferredItem<Item> PHOSPHOR_DUST =
            ITEMS.registerSimpleItem("phosphor_dust", new Item.Properties());
    public static final DeferredItem<PhosphorTubeItem> PHOSPHOR_TUBE = ITEMS.register(
            "phosphor_tube",
            () -> new PhosphorTubeItem(new Item.Properties())
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

    public static final DeferredBlock<PhotodetectorBlock> PHOTODETECTOR = BLOCKS.register(
            "photodetector",
            () -> new PhotodetectorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(0.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion())
    );

    public static final DeferredItem<BlockItem> PHOTODETECTOR_ITEM =
            ITEMS.registerSimpleBlockItem("photodetector", PHOTODETECTOR);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> SPECTRALIZATION_TAB =
            CREATIVE_MODE_TABS.register("spectralization", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.spectralization"))
                    .withTabsBefore(CreativeModeTabs.FUNCTIONAL_BLOCKS)
                    .icon(() -> LENS.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(LENS.get());
                        output.accept(PHOSPHOR_DUST.get());
                        output.accept(PHOSPHOR_TUBE.get());
                        output.accept(LENS_HOLDER_ITEM.get());
                        output.accept(MIRROR_ITEM.get());
                        output.accept(CREATIVE_LIGHT_SOURCE_ITEM.get());
                        output.accept(PHOTODETECTOR_ITEM.get());
                    })
                    .build());

    public Spectralization(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        SpectralBlockEntities.register(modEventBus);

        NeoForge.EVENT_BUS.register(this);
        LOGGER.info("Spectralization initialized");
    }

    @SubscribeEvent
    public void onBlockDrops(BlockDropsEvent event) {
        if (!event.getState().is(Blocks.GLOWSTONE)) {
            return;
        }

        BlockPos pos = event.getPos();
        ItemStack spectralResidue = new ItemStack(Items.AMETHYST_SHARD);
        ItemEntity residueDrop = new ItemEntity(
                event.getLevel(),
                pos.getX() + 0.5,
                pos.getY() + 0.5,
                pos.getZ() + 0.5,
                spectralResidue
        );

        event.getDrops().add(residueDrop);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        SpectralCommands.register(event.getDispatcher());
    }

    @EventBusSubscriber(modid = Spectralization.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    static class ClientModEvents {
        @SubscribeEvent
        static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerBlockEntityRenderer(SpectralBlockEntities.LENS_HOLDER.get(), LensHolderRenderer::new);
        }
    }
}
