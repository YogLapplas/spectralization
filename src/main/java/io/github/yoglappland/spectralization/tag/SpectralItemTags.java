package io.github.yoglappland.spectralization.tag;

import io.github.yoglappland.spectralization.Spectralization;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

public final class SpectralItemTags {
    public static final TagKey<Item> LENS = item("lens");
    public static final TagKey<Item> FILTER = item("filter");
    public static final TagKey<Item> MIRROR = item("mirror");
    public static final TagKey<Item> OPTICAL_PLATE = item("optical_plate");
    public static final TagKey<Item> PAINT_BUCKET = item("paint_bucket");

    private SpectralItemTags() {
    }

    private static TagKey<Item> item(String path) {
        return TagKey.create(
                Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath(Spectralization.MODID, path)
        );
    }
}
