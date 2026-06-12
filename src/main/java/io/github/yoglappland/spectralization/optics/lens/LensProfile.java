package io.github.yoglappland.spectralization.optics.lens;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.optics.BeamEnvelope;
import io.github.yoglappland.spectralization.optics.geometry.BeamGeometryOps;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public record LensProfile(
        String tag,
        int focalLength,
        int aperture,
        int quality
) {
    public static final int MIN_FOCAL_LENGTH = 1;
    public static final int MAX_FOCAL_LENGTH = 64;
    public static final int MIN_APERTURE = 25;
    public static final int MAX_APERTURE = 200;
    public static final int MIN_QUALITY = 1;
    public static final int MAX_QUALITY = 3;
    public static final LensProfile STANDARD = new LensProfile("standard", 8, 100, 2);
    public static final List<String> PRESET_TAGS = List.of("short", "standard", "long", "wide", "precise");

    private static final String TAG_KEY = "spectralization_lens_tag";
    private static final String FOCAL_LENGTH_KEY = "spectralization_lens_focal_length";
    private static final String APERTURE_KEY = "spectralization_lens_aperture";
    private static final String QUALITY_KEY = "spectralization_lens_quality";

    public LensProfile {
        tag = normalizeTag(tag);
        focalLength = clamp(focalLength, MIN_FOCAL_LENGTH, MAX_FOCAL_LENGTH);
        aperture = clamp(aperture, MIN_APERTURE, MAX_APERTURE);
        quality = clamp(quality, MIN_QUALITY, MAX_QUALITY);
    }

    public static LensProfile preset(String tag) {
        return switch (normalizeTag(tag)) {
            case "short" -> new LensProfile("short", 4, 100, 2);
            case "long" -> new LensProfile("long", 16, 100, 2);
            case "wide" -> new LensProfile("wide", 8, 150, 2);
            case "precise" -> new LensProfile("precise", 8, 100, 3);
            default -> STANDARD;
        };
    }

    public static LensProfile fromStack(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");

        if (!stack.is(Spectralization.LENS.get())) {
            return STANDARD;
        }

        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = data.copyTag();

        return new LensProfile(
                tag.getString(TAG_KEY),
                tag.contains(FOCAL_LENGTH_KEY) ? tag.getInt(FOCAL_LENGTH_KEY) : STANDARD.focalLength(),
                tag.contains(APERTURE_KEY) ? tag.getInt(APERTURE_KEY) : STANDARD.aperture(),
                tag.contains(QUALITY_KEY) ? tag.getInt(QUALITY_KEY) : STANDARD.quality()
        );
    }

    public ItemStack createStack() {
        ItemStack stack = new ItemStack(Spectralization.LENS.get());
        applyTo(stack);
        return stack;
    }

    public void applyTo(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putString(TAG_KEY, this.tag);
            tag.putInt(FOCAL_LENGTH_KEY, this.focalLength);
            tag.putInt(APERTURE_KEY, this.aperture);
            tag.putInt(QUALITY_KEY, this.quality);
        });
    }

    public LensProfile withValues(int focalLength, int aperture, int quality) {
        return new LensProfile(tag, focalLength, aperture, quality);
    }

    public BeamEnvelope transformTransmittedEnvelope(BeamEnvelope input) {
        Objects.requireNonNull(input, "input");

        double apertureRadius = aperture / 100.0;
        double qualityMultiplier = switch (quality) {
            case 1 -> 1.5;
            case 3 -> 1.0;
            default -> 1.15;
        };

        return BeamGeometryOps.applyThinLens(input, focalLength, apertureRadius, qualityMultiplier);
    }

    public String qualityNameKey() {
        return switch (quality) {
            case 1 -> "item.spectralization.lens.quality.coarse";
            case 3 -> "item.spectralization.lens.quality.precise";
            default -> "item.spectralization.lens.quality.clear";
        };
    }

    private static String normalizeTag(String tag) {
        if (tag == null || tag.isBlank()) {
            return "standard";
        }

        String normalized = tag.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "_");
        return normalized.isBlank() ? "standard" : normalized;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

}
