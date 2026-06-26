package io.github.yoglappland.spectralization.optics.lens;

import io.github.yoglappland.spectralization.Spectralization;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public enum LensMaterial {
    ORDINARY("ordinary", "lens_material.spectralization.ordinary", 160, 480, 0.920D),
    SILVERED("silvered", "lens_material.spectralization.silvered", 60, 240, 0.580D),
    QUARTZ("quartz", "lens_material.spectralization.quartz", 280, 640, 0.995D),
    BOROSILICATE("borosilicate", "lens_material.spectralization.borosilicate", 120, 560, 0.975D),
    CROWN("crown", "lens_material.spectralization.crown", 80, 480, 0.985D),
    FLINT("flint", "lens_material.spectralization.flint", 20, 320, 0.900D),
    HEAVY("heavy", "lens_material.spectralization.heavy", 10, 200, 0.780D);

    private final String id;
    private final String translationKey;
    private final int minFocalLengthUnits;
    private final int maxFocalLengthUnits;
    private final double baseTransmittance;

    LensMaterial(
            String id,
            String translationKey,
            int minFocalLengthUnits,
            int maxFocalLengthUnits,
            double baseTransmittance
    ) {
        this.id = id;
        this.translationKey = translationKey;
        this.minFocalLengthUnits = minFocalLengthUnits;
        this.maxFocalLengthUnits = maxFocalLengthUnits;
        this.baseTransmittance = baseTransmittance;
    }

    public String id() {
        return id;
    }

    public String translationKey() {
        return translationKey;
    }

    public int minFocalLength() {
        return Math.round(minFocalLengthUnits() / (float) LensProfile.FOCAL_LENGTH_SCALE);
    }

    public int maxFocalLength() {
        return Math.round(maxFocalLengthUnits() / (float) LensProfile.FOCAL_LENGTH_SCALE);
    }

    public int minFocalLengthUnits() {
        return LensProfile.clampFocalLengthUnits(minFocalLengthUnits);
    }

    public int maxFocalLengthUnits() {
        return Math.max(minFocalLengthUnits(), LensProfile.clampFocalLengthUnits(maxFocalLengthUnits));
    }

    public String minFocalLengthText() {
        return LensProfile.formatUnits(minFocalLengthUnits());
    }

    public String maxFocalLengthText() {
        return LensProfile.formatUnits(maxFocalLengthUnits());
    }

    public double baseTransmittance() {
        return baseTransmittance;
    }

    public int transmittancePercent(int quality) {
        return (int) Math.round(transmittance(quality) * 100.0D);
    }

    public double transmittance(int quality) {
        double qualityFactor = switch (Math.max(LensProfile.MIN_QUALITY, Math.min(LensProfile.MAX_QUALITY, quality))) {
            case 1 -> 0.965D;
            case 3 -> 1.0D;
            default -> 0.990D;
        };
        return Math.max(0.0D, Math.min(0.995D, baseTransmittance * qualityFactor));
    }

    public static LensMaterial byId(String id) {
        String normalized = id == null ? "" : id.toLowerCase(Locale.ROOT);

        for (LensMaterial material : values()) {
            if (material.id.equals(normalized)) {
                return material;
            }
        }

        return ORDINARY;
    }

    public static Optional<LensMaterial> fromBlank(ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }

        if (stack.is(Items.GLASS) || stack.is(Items.GLASS_PANE)) {
            return Optional.of(ORDINARY);
        }

        if (stack.is(Spectralization.SILVER_GLASS_ITEM.get())) {
            return Optional.of(SILVERED);
        }

        if (stack.is(Spectralization.QUARTZ_GLASS_ITEM.get())) {
            return Optional.of(QUARTZ);
        }

        if (stack.is(Spectralization.BOROSILICATE_GLASS_ITEM.get())) {
            return Optional.of(BOROSILICATE);
        }

        if (stack.is(Spectralization.CROWN_GLASS_ITEM.get())) {
            return Optional.of(CROWN);
        }

        if (stack.is(Spectralization.FLINT_GLASS_ITEM.get())) {
            return Optional.of(FLINT);
        }

        if (stack.is(Spectralization.HEAVY_GLASS_ITEM.get())) {
            return Optional.of(HEAVY);
        }

        return Optional.empty();
    }
}
