package io.github.yoglappland.spectralization.optics.fiber;

import io.github.yoglappland.spectralization.optics.lens.LensMaterial;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public record FiberMaterialProfile(
        LensMaterial material,
        boolean singleMode,
        double coreRadius,
        double numericalAperture,
        double maxPower,
        double portTransmission
) {
    public static final String TAG_KEY = "spectralization_fiber_profile";
    private static final String MATERIAL_KEY = "material";
    private static final String SINGLE_MODE_KEY = "single_mode";
    private static final String CORE_RADIUS_KEY = "core_radius";
    private static final String NUMERICAL_APERTURE_KEY = "numerical_aperture";
    private static final String MAX_POWER_KEY = "max_power";
    private static final String PORT_TRANSMISSION_KEY = "port_transmission";

    public static final FiberMaterialProfile DEFAULT_NORMAL = new FiberMaterialProfile(
            LensMaterial.ORDINARY,
            false,
            0.156D,
            0.045D,
            48.0D,
            0.955D
    );
    public static final FiberMaterialProfile DEFAULT_SINGLE_MODE = new FiberMaterialProfile(
            LensMaterial.QUARTZ,
            true,
            0.047D,
            0.024D,
            40.0D,
            0.998D
    );

    public FiberMaterialProfile {
        Objects.requireNonNull(material, "material");

        if (!Double.isFinite(coreRadius) || coreRadius <= 0.0D) {
            throw new IllegalArgumentException("Fiber core radius must be finite and positive");
        }

        if (!Double.isFinite(numericalAperture) || numericalAperture <= 0.0D) {
            throw new IllegalArgumentException("Fiber acceptance must be finite and positive");
        }

        if (!Double.isFinite(maxPower) || maxPower <= 0.0D) {
            throw new IllegalArgumentException("Fiber max power must be finite and positive");
        }

        if (!Double.isFinite(portTransmission) || portTransmission <= 0.0D || portTransmission > 1.0D) {
            throw new IllegalArgumentException("Fiber port transmission must be finite and in (0, 1]");
        }
    }

    public static Optional<FiberMaterialProfile> fromMaterial(LensMaterial material, boolean singleMode) {
        Objects.requireNonNull(material, "material");

        if (singleMode) {
            return switch (material) {
                case QUARTZ -> Optional.of(DEFAULT_SINGLE_MODE);
                case BOROSILICATE -> Optional.of(new FiberMaterialProfile(material, true, 0.063D, 0.026D, 48.0D, 0.990D));
                case CROWN -> Optional.of(new FiberMaterialProfile(material, true, 0.055D, 0.028D, 44.0D, 0.986D));
                case FLINT -> Optional.of(new FiberMaterialProfile(material, true, 0.047D, 0.034D, 55.0D, 0.965D));
                default -> Optional.empty();
            };
        }

        return switch (material) {
            case ORDINARY -> Optional.of(DEFAULT_NORMAL);
            case QUARTZ -> Optional.of(new FiberMaterialProfile(material, false, 0.125D, 0.038D, 56.0D, 0.994D));
            case BOROSILICATE -> Optional.of(new FiberMaterialProfile(material, false, 0.141D, 0.042D, 72.0D, 0.982D));
            case CROWN -> Optional.of(new FiberMaterialProfile(material, false, 0.125D, 0.044D, 64.0D, 0.976D));
            case FLINT -> Optional.of(new FiberMaterialProfile(material, false, 0.094D, 0.060D, 80.0D, 0.955D));
            case HEAVY -> Optional.of(new FiberMaterialProfile(material, false, 0.188D, 0.035D, 112.0D, 0.948D));
            default -> Optional.empty();
        };
    }

    public static FiberMaterialProfile defaultFor(boolean singleMode) {
        return singleMode ? DEFAULT_SINGLE_MODE : DEFAULT_NORMAL;
    }

    public static FiberMaterialProfile fromStack(ItemStack stack, boolean defaultSingleMode) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag root = data.copyTag();

        if (!root.contains(TAG_KEY)) {
            return defaultFor(defaultSingleMode);
        }

        return read(root.getCompound(TAG_KEY)).orElseGet(() -> defaultFor(defaultSingleMode));
    }

    public ItemStack applyTo(ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, root -> root.put(TAG_KEY, write()));
        return stack;
    }

    public CompoundTag write() {
        CompoundTag tag = new CompoundTag();
        tag.putString(MATERIAL_KEY, material.id());
        tag.putBoolean(SINGLE_MODE_KEY, singleMode);
        tag.putDouble(CORE_RADIUS_KEY, coreRadius);
        tag.putDouble(NUMERICAL_APERTURE_KEY, numericalAperture);
        tag.putDouble(MAX_POWER_KEY, maxPower);
        tag.putDouble(PORT_TRANSMISSION_KEY, portTransmission);
        return tag;
    }

    public static Optional<FiberMaterialProfile> read(CompoundTag tag) {
        if (tag == null || !tag.contains(MATERIAL_KEY)) {
            return Optional.empty();
        }

        LensMaterial material = LensMaterial.byId(tag.getString(MATERIAL_KEY));
        boolean singleMode = tag.getBoolean(SINGLE_MODE_KEY);

        try {
            return Optional.of(new FiberMaterialProfile(
                    material,
                    singleMode,
                    tag.contains(CORE_RADIUS_KEY) ? tag.getDouble(CORE_RADIUS_KEY) : defaultFor(singleMode).coreRadius(),
                    tag.contains(NUMERICAL_APERTURE_KEY)
                            ? tag.getDouble(NUMERICAL_APERTURE_KEY)
                            : defaultFor(singleMode).numericalAperture(),
                    tag.contains(MAX_POWER_KEY) ? tag.getDouble(MAX_POWER_KEY) : defaultFor(singleMode).maxPower(),
                    tag.contains(PORT_TRANSMISSION_KEY)
                            ? tag.getDouble(PORT_TRANSMISSION_KEY)
                            : defaultFor(singleMode).portTransmission()
            ));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public double coreDiameter() {
        return coreRadius * 2.0D;
    }

    public double portLossPercent() {
        return (1.0D - portTransmission) * 100.0D;
    }

    public String coreDiameterText() {
        return String.format(Locale.ROOT, "%.3f", coreDiameter());
    }

    public String maxPowerText() {
        return String.format(Locale.ROOT, "%.0f", maxPower);
    }

    public String portLossText() {
        return String.format(Locale.ROOT, "%.1f", portLossPercent());
    }

    public int coreScore() {
        return score(coreRadius, 0.20D);
    }

    public int capacityScore() {
        return score(maxPower, 128.0D);
    }

    public int lossScore() {
        return score(portLossPercent(), 8.0D);
    }

    private static int score(double value, double max) {
        return Math.max(0, Math.min(100, (int) Math.round(value * 100.0D / max)));
    }
}
