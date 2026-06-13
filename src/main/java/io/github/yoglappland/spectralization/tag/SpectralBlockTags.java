package io.github.yoglappland.spectralization.tag;

import io.github.yoglappland.spectralization.Spectralization;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public final class SpectralBlockTags {
    public static final TagKey<Block> STRAY_LIGHT_SOURCE = block("stray_light_source");
    public static final TagKey<Block> PUMP_SOURCE = block("pump_source");
    public static final TagKey<Block> LASER_GAIN_MEDIUM = block("laser_gain_medium");
    public static final TagKey<Block> HIGH_REFLECTANCE_MATERIAL = block("high_reflectance_material");
    public static final TagKey<Block> REFLECTIVE_MATERIAL = block("reflective_material");
    public static final TagKey<Block> HOLOGRAPHIC_STORAGE = block("holographic_storage");

    private SpectralBlockTags() {
    }

    private static TagKey<Block> block(String path) {
        return TagKey.create(
                Registries.BLOCK,
                ResourceLocation.fromNamespaceAndPath(Spectralization.MODID, path)
        );
    }
}
