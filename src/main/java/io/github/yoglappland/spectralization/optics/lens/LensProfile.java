package io.github.yoglappland.spectralization.optics.lens;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.optics.BeamEnvelope;
import io.github.yoglappland.spectralization.optics.geometry.BeamGeometryOps;
import io.github.yoglappland.spectralization.optics.geometry.BeamProfileKey;
import io.github.yoglappland.spectralization.optics.geometry.BeamProfileTransfer;
import io.github.yoglappland.spectralization.optics.geometry.PhaseSpaceMap;
import io.github.yoglappland.spectralization.registry.SpectralEnchantments;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public record LensProfile(
        String tag,
        int focalLengthUnits,
        int aperture,
        int quality,
        String material,
        int finishPermille,
        int clarityLevel
) {
    public static final int FOCAL_LENGTH_SCALE = 20;
    public static final int MIN_FOCAL_LENGTH_UNITS = 10;
    public static final int MAX_FOCAL_LENGTH_UNITS = 640;
    public static final int MIN_FOCAL_LENGTH = 1;
    public static final int MAX_FOCAL_LENGTH = MAX_FOCAL_LENGTH_UNITS / FOCAL_LENGTH_SCALE;
    public static final int MIN_APERTURE = 25;
    public static final int MAX_APERTURE = 200;
    public static final int MIN_QUALITY = 1;
    public static final int MAX_QUALITY = 3;
    public static final int MIN_FINISH_PERMILLE = 500;
    public static final int MAX_FINISH_PERMILLE = 1000;
    public static final int MAX_CLARITY_LEVEL = 3;
    public static final LensProfile STANDARD = new LensProfile("standard", 8, 100, 2);
    public static final List<String> PRESET_TAGS = List.of("short", "standard", "long", "wide", "precise");

    private static final String TAG_KEY = "spectralization_lens_tag";
    private static final String FOCAL_LENGTH_KEY = "spectralization_lens_focal_length";
    private static final String FOCAL_LENGTH_TENTHS_KEY = "spectralization_lens_focal_length_tenths";
    private static final String FOCAL_LENGTH_UNITS_KEY = "spectralization_lens_focal_length_units";
    private static final String APERTURE_KEY = "spectralization_lens_aperture";
    private static final String QUALITY_KEY = "spectralization_lens_quality";
    private static final String MATERIAL_KEY = "spectralization_lens_material";
    private static final String FINISH_KEY = "spectralization_lens_finish";

    public LensProfile {
        tag = normalizeTag(tag);
        focalLengthUnits = clampFocalLengthUnits(focalLengthUnits);
        aperture = clamp(aperture, MIN_APERTURE, MAX_APERTURE);
        quality = clamp(quality, MIN_QUALITY, MAX_QUALITY);
        material = LensMaterial.byId(material).id();
        finishPermille = clamp(finishPermille, MIN_FINISH_PERMILLE, MAX_FINISH_PERMILLE);
        clarityLevel = clamp(clarityLevel, 0, MAX_CLARITY_LEVEL);
    }

    public LensProfile(String tag, int focalLength, int aperture, int quality) {
        this(tag, focalLength, aperture, quality, LensMaterial.ORDINARY.id());
    }

    public LensProfile(String tag, int focalLength, int aperture, int quality, String material) {
        this(tag, focalLength * FOCAL_LENGTH_SCALE, aperture, quality, material, MAX_FINISH_PERMILLE, 0);
    }

    public static LensProfile withUnits(
            String tag,
            int focalLengthUnits,
            int aperture,
            int quality,
            String material,
            int finishPermille
    ) {
        return withUnits(tag, focalLengthUnits, aperture, quality, material, finishPermille, 0);
    }

    public static LensProfile withUnits(
            String tag,
            int focalLengthUnits,
            int aperture,
            int quality,
            String material,
            int finishPermille,
            int clarityLevel
    ) {
        return new LensProfile(tag, focalLengthUnits, aperture, quality, material, finishPermille, clarityLevel);
    }

    public static LensProfile withTenths(
            String tag,
            int focalLengthTenths,
            int aperture,
            int quality,
            String material,
            int finishPermille
    ) {
        return withUnits(tag, focalLengthTenths * 2, aperture, quality, material, finishPermille);
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
        int focalLength = focalLengthFromTag(tag);
        int clarityLevel = SpectralEnchantments.level(stack, SpectralEnchantments.CLARITY);

        return withUnits(
                tag.getString(TAG_KEY),
                focalLength,
                tag.contains(APERTURE_KEY) ? tag.getInt(APERTURE_KEY) : STANDARD.aperture(),
                tag.contains(QUALITY_KEY) ? tag.getInt(QUALITY_KEY) : STANDARD.quality(),
                tag.contains(MATERIAL_KEY) ? tag.getString(MATERIAL_KEY) : STANDARD.material(),
                tag.contains(FINISH_KEY) ? tag.getInt(FINISH_KEY) : STANDARD.finishPermille(),
                clarityLevel
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
            tag.putInt(FOCAL_LENGTH_KEY, this.focalLength());
            tag.putInt(FOCAL_LENGTH_TENTHS_KEY, Math.round(this.focalLengthUnits * 10.0F / FOCAL_LENGTH_SCALE));
            tag.putInt(FOCAL_LENGTH_UNITS_KEY, this.focalLengthUnits);
            tag.putInt(APERTURE_KEY, this.aperture);
            tag.putInt(QUALITY_KEY, this.quality);
            tag.putString(MATERIAL_KEY, this.material);
            tag.putInt(FINISH_KEY, this.finishPermille);
        });
    }

    public LensProfile withValues(int focalLength, int aperture, int quality) {
        return withUnits(tag, focalLength * FOCAL_LENGTH_SCALE, aperture, quality, material, finishPermille, clarityLevel);
    }

    public LensProfile withUnitValues(int focalLengthUnits, int aperture, int quality) {
        return withUnits(tag, focalLengthUnits, aperture, quality, material, finishPermille, clarityLevel);
    }

    public int focalLength() {
        return Math.round(focalLengthUnits / (float) FOCAL_LENGTH_SCALE);
    }

    public double focalLengthBlocks() {
        return focalLengthUnits / (double) FOCAL_LENGTH_SCALE;
    }

    public String focalLengthText() {
        return formatUnits(focalLengthUnits);
    }

    public LensMaterial materialProfile() {
        return LensMaterial.byId(material);
    }

    public double transmittance() {
        double finishFactor = finishPermille / (double) MAX_FINISH_PERMILLE;
        double rawTransmission = Math.max(0.0D, Math.min(1.0D, materialProfile().transmittance(quality) * finishFactor));
        double loss = 1.0D - rawTransmission;
        for (int level = 0; level < clarityLevel; level++) {
            loss *= 0.5D;
        }
        return Math.max(0.0D, Math.min(1.0D, 1.0D - loss));
    }

    public int transmittancePercent() {
        return (int) Math.round(transmittance() * 100.0D);
    }

    public BeamEnvelope transformTransmittedEnvelope(BeamEnvelope input) {
        Objects.requireNonNull(input, "input");

        double apertureRadius = opticalApertureRadius();
        double qualityMultiplier = switch (quality) {
            case 1 -> 1.5;
            case 3 -> 1.0;
            default -> 1.15;
        };

        return BeamGeometryOps.applyThinLens(input, focalLengthBlocks(), apertureRadius, qualityMultiplier);
    }

    public PhaseSpaceMap phaseSpaceMap() {
        return PhaseSpaceMap.thinLens(focalLengthBlocks());
    }

    public BeamProfileTransfer transformTransmittedProfile(BeamProfileKey input) {
        Objects.requireNonNull(input, "input");

        double apertureRadius = opticalApertureRadius();
        double qualityMultiplier = switch (quality) {
            case 1 -> 1.5;
            case 3 -> 1.0;
            default -> 1.15;
        };

        return input.toShape().thinLens(focalLengthBlocks(), apertureRadius, qualityMultiplier);
    }

    public String qualityNameKey() {
        return switch (quality) {
            case 1 -> "item.spectralization.lens.quality.coarse";
            case 3 -> "item.spectralization.lens.quality.precise";
            default -> "item.spectralization.lens.quality.clear";
        };
    }

    public static String formatUnits(int units) {
        if (units % FOCAL_LENGTH_SCALE == 0) {
            return Integer.toString(units / FOCAL_LENGTH_SCALE);
        }

        if (units * 10 % FOCAL_LENGTH_SCALE == 0) {
            return String.format(Locale.ROOT, "%.1f", units / (double) FOCAL_LENGTH_SCALE);
        }

        return String.format(Locale.ROOT, "%.2f", units / (double) FOCAL_LENGTH_SCALE);
    }

    public static String formatTenths(int tenths) {
        return formatUnits(tenths * 2);
    }

    public static int clampFocalLengthUnits(int focalLengthUnits) {
        return clamp(focalLengthUnits, MIN_FOCAL_LENGTH_UNITS, MAX_FOCAL_LENGTH_UNITS);
    }

    private static int focalLengthFromTag(CompoundTag tag) {
        if (tag.contains(FOCAL_LENGTH_UNITS_KEY)) {
            return clampFocalLengthUnits(tag.getInt(FOCAL_LENGTH_UNITS_KEY));
        }

        if (tag.contains(FOCAL_LENGTH_TENTHS_KEY)) {
            return clampFocalLengthUnits(tag.getInt(FOCAL_LENGTH_TENTHS_KEY) * 2);
        }

        return tag.contains(FOCAL_LENGTH_KEY)
                ? clampFocalLengthUnits(tag.getInt(FOCAL_LENGTH_KEY) * FOCAL_LENGTH_SCALE)
                : STANDARD.focalLengthUnits();
    }

    private static String normalizeTag(String tag) {
        if (tag == null || tag.isBlank()) {
            return "standard";
        }

        String normalized = tag.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "_");
        return normalized.isBlank() ? "standard" : normalized;
    }

    private double opticalApertureRadius() {
        return Math.min(1.0D, aperture / 100.0D);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

}
