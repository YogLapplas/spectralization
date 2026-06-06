package io.github.yoglappland.spectralization.tag;

import io.github.yoglappland.spectralization.Spectralization;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

public final class SpectralItemTags {
    public static final TagKey<Item> LENS = TagKey.create(
            Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(Spectralization.MODID, "lens")
    );

    private SpectralItemTags() {
    }
}
