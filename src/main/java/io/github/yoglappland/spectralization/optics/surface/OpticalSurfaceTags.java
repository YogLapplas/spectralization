package io.github.yoglappland.spectralization.optics.surface;

import io.github.yoglappland.spectralization.Spectralization;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public final class OpticalSurfaceTags {
    public static final TagKey<Block> WORLD_COATABLE = block("world_coatable");
    public static final TagKey<Block> ITEM_COATABLE = block("item_coatable");
    public static final TagKey<Block> SURFACE_LOCKED = block("surface_locked");

    private static TagKey<Block> block(String path) {
        return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(Spectralization.MODID, path));
    }

    private OpticalSurfaceTags() {
    }
}
