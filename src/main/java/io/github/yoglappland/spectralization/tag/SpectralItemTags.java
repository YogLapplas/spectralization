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
    public static final TagKey<Item> STRAY_LIGHT_SOURCE = item("stray_light_source");
    public static final TagKey<Item> LIGHT_SOURCE_GENERATOR_FUEL = item("light_source_generator_fuel");
    public static final TagKey<Item> PUMP_SOURCE = item("pump_source");
    public static final TagKey<Item> SEED_LIGHT_SOURCE = item("seed_light_source");
    public static final TagKey<Item> LASER_GAIN_MEDIUM = item("laser_gain_medium");
    public static final TagKey<Item> HIGH_REFLECTANCE_MATERIAL = item("high_reflectance_material");
    public static final TagKey<Item> REFLECTIVE_MATERIAL = item("reflective_material");
    public static final TagKey<Item> OPTICAL_FIBER = item("optical_fiber");
    public static final TagKey<Item> METASURFACE_TEMPLATE = item("metasurface_template");
    public static final TagKey<Item> LITHOGRAPHY_MASK = item("lithography_mask");
    public static final TagKey<Item> MASKS = item("masks");
    public static final TagKey<Item> GENERIC_MASKS = item("masks/generic");
    public static final TagKey<Item> GRATING_MASKS = item("masks/grating");
    public static final TagKey<Item> WAVEGUIDE_MASKS = item("masks/waveguide");
    public static final TagKey<Item> CIRCUIT_MASKS = item("masks/circuit");
    public static final TagKey<Item> MOLDS = item("molds");
    public static final TagKey<Item> FIBER_MOLDS = item("molds/fiber");
    public static final TagKey<Item> SINGLE_MODE_FIBER_MOLDS = item("molds/single_mode_fiber");
    public static final TagKey<Item> PLATE_MOLDS = item("molds/plate");
    public static final TagKey<Item> WIRE_MOLDS = item("molds/wire");
    public static final TagKey<Item> CRYSTAL_MOLDS = item("molds/crystal");
    public static final TagKey<Item> STANDARD_METAMATERIAL_TEMPLATE = item("standard_metamaterial_template");
    public static final TagKey<Item> CUSTOM_METAMATERIAL_TEMPLATE = item("custom_metamaterial_template");
    public static final TagKey<Item> GRINDING_KNIVES = item("grinding_knives");
    public static final TagKey<Item> SINGULAR_MATERIAL = item("singular_material");
    public static final TagKey<Item> COLOR_OUT_OF_SPACE_MATERIAL = item("color_out_of_space_material");
    public static final TagKey<Item> UNSTABLE_MACHINE = item("unstable_machine");

    private SpectralItemTags() {
    }

    private static TagKey<Item> item(String path) {
        return TagKey.create(
                Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath(Spectralization.MODID, path)
        );
    }
}
