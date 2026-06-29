package io.github.yoglappland.spectralization.machine;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.Arrays;

public record RecursiveGeneratorState(int[] remaining, int energy) {
    public static final int MAX_LAYERS = 16;
    public static final int BASE_OUTPUT_FE_PER_TICK = 250;
    public static final int CAPACITY = 1_000_000_000;
    public static final int MAX_EXTRACT_PER_TICK = BASE_OUTPUT_FE_PER_TICK << (MAX_LAYERS - 1);
    public static final int BAR_SCALE_TICKS = 1_020;
    public static final int RECURSION_AGE_TICKS = 60;
    public static final int[] OUTER_DURATION_TICKS = filledDurations();
    public static final int[] OUTPUT_FE_PER_TICK = doubledOutputs();

    private static final String ROOT_KEY = "spectralization_recursive_generator";
    private static final String REMAINING_KEY = "remaining";
    private static final String ENERGY_KEY = "energy";

    public RecursiveGeneratorState {
        remaining = normalizedRemaining(remaining);
        energy = clampEnergy(energy);
    }

    public static RecursiveGeneratorState empty() {
        return new RecursiveGeneratorState(new int[MAX_LAYERS], 0);
    }

    public static RecursiveGeneratorState fromStack(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return empty();
        }
        CompoundTag root = data.copyTag();
        if (!root.contains(ROOT_KEY)) {
            return empty();
        }
        CompoundTag tag = root.getCompound(ROOT_KEY);
        int[] remaining = tag.getIntArray(REMAINING_KEY);
        int energy = tag.getInt(ENERGY_KEY);
        return new RecursiveGeneratorState(remaining, energy);
    }

    public static boolean hasSavedState(ItemStack stack) {
        return fromStack(stack).hasState();
    }

    public static void writeToStack(ItemStack stack, RecursiveGeneratorState state) {
        stack.remove(DataComponents.BLOCK_ENTITY_DATA);
        if (!state.hasState()) {
            clearStack(stack);
            return;
        }

        stack.setCount(1);
        stack.set(DataComponents.MAX_STACK_SIZE, 1);
        CompoundTag root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        CompoundTag tag = new CompoundTag();
        tag.putIntArray(REMAINING_KEY, state.remaining());
        tag.putInt(ENERGY_KEY, state.energy());
        root.put(ROOT_KEY, tag);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
    }

    public static void clearStack(ItemStack stack) {
        stack.remove(DataComponents.CUSTOM_DATA);
        stack.remove(DataComponents.BLOCK_ENTITY_DATA);
        stack.set(DataComponents.MAX_STACK_SIZE, stack.getItem().getDefaultMaxStackSize());
    }

    public static RecursiveGeneratorState promotedFrom(RecursiveGeneratorState inner, int existingEnergy) {
        int[] next = new int[MAX_LAYERS];
        int count = 0;
        for (int layer = 0; layer < MAX_LAYERS; layer++) {
            int aged = inner.remaining[layer] - RECURSION_AGE_TICKS;
            if (aged > 0) {
                next[count++] = aged;
            }
        }

        if (count >= MAX_LAYERS) {
            System.arraycopy(next, 1, next, 0, MAX_LAYERS - 1);
            next[MAX_LAYERS - 1] = 0;
            count = MAX_LAYERS - 1;
        }

        next[count] = Math.max(0, OUTER_DURATION_TICKS[count] - RECURSION_AGE_TICKS);
        int combinedEnergy = clampEnergy(existingEnergy + inner.energy);
        return new RecursiveGeneratorState(next, combinedEnergy);
    }

    public static int expectedPromotedActiveLayerCount(RecursiveGeneratorState inner) {
        return promotedFrom(inner, 0).activeLayerCount();
    }

    public boolean hasState() {
        return energy > 0 || hasRemaining();
    }

    public boolean hasRemaining() {
        for (int value : remaining) {
            if (value > 0) {
                return true;
            }
        }
        return false;
    }

    public int activeLayerCount() {
        int count = 0;
        for (int value : remaining) {
            if (value > 0) {
                count++;
            }
        }
        return count;
    }

    public int recursionDepth() {
        int active = activeLayerCount();
        return active <= 0 ? 0 : Math.min(active - 1, MAX_LAYERS - 1);
    }

    public int currentOutputPerTick() {
        if (!hasRemaining()) {
            return 0;
        }
        return OUTPUT_FE_PER_TICK[recursionDepth()];
    }

    public RecursiveGeneratorState withEnergy(int newEnergy) {
        return new RecursiveGeneratorState(remaining, newEnergy);
    }

    public RecursiveGeneratorState tickRemaining() {
        int[] next = Arrays.copyOf(remaining, MAX_LAYERS);
        for (int i = 0; i < next.length; i++) {
            if (next[i] > 0) {
                next[i]--;
            }
        }
        return new RecursiveGeneratorState(next, energy);
    }

    public int remainingAt(int layer) {
        if (layer < 0 || layer >= MAX_LAYERS) {
            return 0;
        }
        return remaining[layer];
    }

    public int highestRemainingLayer() {
        for (int layer = MAX_LAYERS - 1; layer >= 0; layer--) {
            if (remaining[layer] > 0) {
                return layer;
            }
        }
        return -1;
    }

    private static int[] normalizedRemaining(int[] source) {
        int[] out = new int[MAX_LAYERS];
        if (source == null) {
            return out;
        }
        int count = 0;
        for (int i = 0; i < Math.min(source.length, MAX_LAYERS); i++) {
            int value = Math.max(0, source[i]);
            if (value > 0) {
                out[count++] = value;
            }
        }
        return out;
    }

    private static int clampEnergy(int value) {
        return Math.max(0, Math.min(CAPACITY, value));
    }

    private static int[] filledDurations() {
        int[] durations = new int[MAX_LAYERS];
        Arrays.fill(durations, BAR_SCALE_TICKS);
        return durations;
    }

    private static int[] doubledOutputs() {
        int[] outputs = new int[MAX_LAYERS];
        int output = BASE_OUTPUT_FE_PER_TICK;
        for (int i = 0; i < outputs.length; i++) {
            outputs[i] = output;
            output *= 2;
        }
        return outputs;
    }
}
