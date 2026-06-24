package io.github.yoglappland.spectralization.machine;

import io.github.yoglappland.spectralization.Spectralization;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Blocks;

public record SolarDopingRecipe(
        String id,
        Item input,
        List<WeightedResult> results,
        double baseChance,
        int accentColor
) {
    private static final List<SolarDopingRecipe> RECIPES = List.of(
            recipe(
                    "primitive_board_doping",
                    Spectralization.PRIMITIVE_CIRCUIT_BOARD.get(),
                    () -> new ItemStack(Spectralization.ADVANCED_CIRCUIT_BOARD.get()),
                    0.000060,
                    0xFF9FE7DF
            ),
            recipe(
                    "advanced_board_doping",
                    Spectralization.ADVANCED_CIRCUIT_BOARD.get(),
                    () -> new ItemStack(Spectralization.PRECISION_CIRCUIT_BOARD.get()),
                    0.000040,
                    0xFFC49BFF
            ),
            weightedRecipe(
                    "white_concrete_random_doping",
                    Blocks.WHITE_CONCRETE,
                    0.000100,
                    0xFFE6D9FF,
                    weightedResult(Blocks.RED_CONCRETE, 20),
                    weightedResult(Blocks.YELLOW_CONCRETE, 30),
                    weightedResult(Blocks.BLUE_CONCRETE, 50)
            )
    );

    public SolarDopingRecipe {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Solar doping recipe id must not be blank");
        }

        Objects.requireNonNull(input, "input");
        results = List.copyOf(results);
        if (results.isEmpty()) {
            throw new IllegalArgumentException("Solar doping recipe must have at least one result");
        }

        if (!Double.isFinite(baseChance) || baseChance <= 0.0) {
            throw new IllegalArgumentException("Solar doping base chance must be finite and positive");
        }
    }

    public static Optional<SolarDopingRecipe> find(ItemStack input, ItemStack filter) {
        if (input.isEmpty()) {
            return Optional.empty();
        }

        for (SolarDopingRecipe recipe : RECIPES) {
            if (recipe.matches(input, filter)) {
                return Optional.of(recipe);
            }
        }

        return Optional.empty();
    }

    public static boolean isPotentialInput(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        return RECIPES.stream().anyMatch(recipe -> stack.is(recipe.input));
    }

    public static boolean isKnownResult(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        return RECIPES.stream().anyMatch(recipe -> recipe.resultStacks().stream()
                .anyMatch(result -> ItemStack.isSameItemSameComponents(result, stack)));
    }

    public static List<SolarDopingRecipe> recipes() {
        return RECIPES;
    }

    public ItemStack resultStack() {
        return results.getFirst().resultStack();
    }

    public List<ItemStack> resultStacks() {
        List<ItemStack> stacks = new ArrayList<>(results.size());
        for (WeightedResult result : results) {
            stacks.add(result.resultStack());
        }

        return List.copyOf(stacks);
    }

    public ItemStack rollResult(RandomSource random) {
        int total = totalResultWeight();
        int pick = random.nextInt(total);
        for (WeightedResult result : results) {
            pick -= result.weight();
            if (pick < 0) {
                return result.resultStack();
            }
        }

        return resultStack();
    }

    public boolean hasRandomResults() {
        return results.size() > 1;
    }

    public int totalResultWeight() {
        int total = 0;
        for (WeightedResult result : results) {
            total += result.weight();
        }

        return Math.max(1, total);
    }

    public int expectedTicks(double heightMultiplier, double arrayMultiplier) {
        return estimate(heightMultiplier, arrayMultiplier).expectedTicks();
    }

    public int maxTicks(double heightMultiplier, double arrayMultiplier) {
        return estimate(heightMultiplier, arrayMultiplier).maxTicks();
    }

    private SolarDopingChanceLut.Estimate estimate(double heightMultiplier, double arrayMultiplier) {
        return SolarDopingChanceLut.estimate(effectiveBaseChance(heightMultiplier, arrayMultiplier));
    }

    public double effectiveBaseChance(double heightMultiplier, double arrayMultiplier) {
        double multiplier = Math.max(0.0, heightMultiplier) * Math.max(0.0, arrayMultiplier);
        return Math.max(0.0, Math.min(1.0, baseChance * multiplier));
    }

    private boolean matches(ItemStack inputStack, @Nullable ItemStack filterStack) {
        return inputStack.is(input);
    }

    public record WeightedResult(Supplier<ItemStack> result, int weight) {
        public WeightedResult {
            Objects.requireNonNull(result, "result");
            if (weight <= 0) {
                throw new IllegalArgumentException("Solar doping result weight must be positive");
            }
        }

        public ItemStack resultStack() {
            ItemStack stack = result.get();
            stack.setCount(Math.min(1, Math.max(0, stack.getCount())));
            return stack;
        }
    }

    private static SolarDopingRecipe recipe(
            String id,
            ItemLike input,
            Supplier<ItemStack> result,
            double baseChance,
            int accentColor
    ) {
        return new SolarDopingRecipe(id, input.asItem(), List.of(new WeightedResult(result, 100)), baseChance, accentColor);
    }

    private static SolarDopingRecipe weightedRecipe(
            String id,
            ItemLike input,
            double baseChance,
            int accentColor,
            WeightedResult... results
    ) {
        return new SolarDopingRecipe(id, input.asItem(), List.of(results), baseChance, accentColor);
    }

    private static WeightedResult weightedResult(ItemLike result, int weight) {
        return new WeightedResult(() -> new ItemStack(result), weight);
    }
}
