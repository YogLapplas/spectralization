package io.github.yoglappland.spectralization.storage;

import io.github.yoglappland.spectralization.Spectralization;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class PhotoinducedReactionRecipe {
    public static final int ONE_SECOND_TICKS = 20;

    private static final List<Item> WOOL_COLORS = List.of(
            Items.WHITE_WOOL,
            Items.ORANGE_WOOL,
            Items.MAGENTA_WOOL,
            Items.LIGHT_BLUE_WOOL,
            Items.YELLOW_WOOL,
            Items.LIME_WOOL,
            Items.PINK_WOOL,
            Items.GRAY_WOOL,
            Items.LIGHT_GRAY_WOOL,
            Items.CYAN_WOOL,
            Items.PURPLE_WOOL,
            Items.BLUE_WOOL,
            Items.BROWN_WOOL,
            Items.GREEN_WOOL,
            Items.RED_WOOL,
            Items.BLACK_WOOL
    );

    private static final List<PhotoinducedReactionRecipe> RECIPES = List.of(
            new PhotoinducedReactionRecipe(
                    "coal_to_diamond",
                    stack -> stack.is(Items.COAL),
                    100.0,
                    ONE_SECOND_TICKS,
                    () -> List.of(new ItemStack(Items.COAL)),
                    stack -> List.of(new ItemStack(Items.DIAMOND))
            ),
            new PhotoinducedReactionRecipe(
                    "silicon_to_wafer",
                    stack -> stack.is(Spectralization.SILICON.get()),
                    10.0,
                    ONE_SECOND_TICKS,
                    () -> List.of(new ItemStack(Spectralization.SILICON.get())),
                    stack -> List.of(new ItemStack(Spectralization.BLANK_WAFER.get()))
            ),
            new PhotoinducedReactionRecipe(
                    "wool_color_shift",
                    stack -> stack.is(ItemTags.WOOL),
                    20.0,
                    ONE_SECOND_TICKS,
                    PhotoinducedReactionRecipe::woolColorStacks,
                    PhotoinducedReactionRecipe::alternateWoolColors
            )
    );

    private final String id;
    private final Predicate<ItemStack> input;
    private final double requiredCoherentPower;
    private final int processTicks;
    private final Supplier<List<ItemStack>> displayInputs;
    private final ResultProvider results;

    private PhotoinducedReactionRecipe(
            String id,
            Predicate<ItemStack> input,
            double requiredCoherentPower,
            int processTicks,
            Supplier<List<ItemStack>> displayInputs,
            ResultProvider results
    ) {
        this.id = id;
        this.input = input;
        this.requiredCoherentPower = requiredCoherentPower;
        this.processTicks = processTicks;
        this.displayInputs = displayInputs;
        this.results = results;
    }

    public static List<PhotoinducedReactionRecipe> recipes() {
        return RECIPES;
    }

    public static Optional<PhotoinducedReactionRecipe> find(ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }

        return RECIPES.stream()
                .filter(recipe -> recipe.matches(stack))
                .findFirst();
    }

    public String id() {
        return id;
    }

    public double requiredCoherentPower() {
        return requiredCoherentPower;
    }

    public int processTicks() {
        return processTicks;
    }

    public List<ItemStack> displayInputs() {
        return displayInputs.get().stream()
                .filter(stack -> !stack.isEmpty())
                .map(stack -> stack.copyWithCount(1))
                .toList();
    }

    public List<ItemStack> displayResults() {
        if ("wool_color_shift".equals(id)) {
            return woolColorStacks();
        }

        List<ItemStack> inputs = displayInputs();
        return inputs.isEmpty() ? List.of() : possibleResults(inputs.getFirst());
    }

    public List<ItemStack> possibleResults(ItemStack source) {
        return results.possibleResults(source).stream()
                .filter(stack -> !stack.isEmpty())
                .map(stack -> stack.copyWithCount(1))
                .toList();
    }

    private boolean matches(ItemStack stack) {
        return input.test(stack);
    }

    private static List<ItemStack> woolColorStacks() {
        return WOOL_COLORS.stream()
                .map(ItemStack::new)
                .toList();
    }

    private static List<ItemStack> alternateWoolColors(ItemStack source) {
        List<ItemStack> results = new ArrayList<>();
        int sourceIndex = woolColorIndex(source.getItem());

        for (int index = 0; index < WOOL_COLORS.size(); index++) {
            if (index != sourceIndex) {
                results.add(new ItemStack(WOOL_COLORS.get(index)));
            }
        }

        return results;
    }

    private static int woolColorIndex(Item item) {
        for (int index = 0; index < WOOL_COLORS.size(); index++) {
            if (WOOL_COLORS.get(index) == item) {
                return index;
            }
        }

        return -1;
    }

    @FunctionalInterface
    private interface ResultProvider {
        List<ItemStack> possibleResults(ItemStack source);
    }
}
