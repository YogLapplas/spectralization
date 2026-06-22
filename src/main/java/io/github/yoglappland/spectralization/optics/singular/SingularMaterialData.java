package io.github.yoglappland.spectralization.optics.singular;

import io.github.yoglappland.spectralization.Spectralization;
import java.util.Objects;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public record SingularMaterialData(
        String sourceId,
        long seed,
        boolean resolved
) {
    public static final String PRIMORDIAL_SOURCE = "spectralization:primordial";

    private static final String SOURCE_KEY = "spectralization_singular_source";
    private static final String SEED_KEY = "spectralization_singular_seed";
    private static final String RESOLVED_KEY = "spectralization_singular_resolved";

    public static final SingularMaterialData PRIMORDIAL =
            new SingularMaterialData(PRIMORDIAL_SOURCE, 0L, false);

    public SingularMaterialData {
        sourceId = SingularMaterialGenerator.normalizeSource(sourceId);
    }

    public static SingularMaterialData fromSource(String sourceId) {
        return new SingularMaterialData(sourceId, 0L, false);
    }

    public static SingularMaterialData fromStack(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");

        if (!stack.is(Spectralization.SINGULAR_MATERIAL.get())) {
            return PRIMORDIAL;
        }

        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = data.copyTag();
        String sourceId = tag.getString(SOURCE_KEY);
        boolean resolved = tag.getBoolean(RESOLVED_KEY);
        long seed = tag.getLong(SEED_KEY);
        return new SingularMaterialData(sourceId, seed, resolved);
    }

    public ItemStack createStack() {
        ItemStack stack = new ItemStack(Spectralization.SINGULAR_MATERIAL.get());
        applyTo(stack);
        return stack;
    }

    public void applyTo(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putString(SOURCE_KEY, sourceId);
            tag.putLong(SEED_KEY, seed);
            tag.putBoolean(RESOLVED_KEY, resolved);
        });
    }

    public SingularMaterialData resolved(long worldSeed) {
        if (resolved) {
            return this;
        }

        return new SingularMaterialData(sourceId, SingularMaterialGenerator.seedFor(worldSeed, sourceId), true);
    }

    public long renderSeed() {
        return resolved ? seed : SingularMaterialGenerator.fallbackSeed(sourceId);
    }

    public static boolean resolveInWorld(ItemStack stack, long worldSeed) {
        SingularMaterialData data = fromStack(stack);
        if (data.resolved()) {
            return false;
        }

        data.resolved(worldSeed).applyTo(stack);
        return true;
    }
}
