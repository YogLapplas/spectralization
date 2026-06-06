package io.github.yoglappland.spectralization.registry;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.blockentity.LensHolderBlockEntity;
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

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITY_TYPES.register(modEventBus);
    }

    private SpectralBlockEntities() {
    }
}
