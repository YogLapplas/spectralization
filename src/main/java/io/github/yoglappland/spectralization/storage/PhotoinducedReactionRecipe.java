package io.github.yoglappland.spectralization.storage;

import io.github.yoglappland.spectralization.Spectralization;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class PhotoinducedReactionRecipe {
    public static final int ONE_SECOND_TICKS = 20;
    private static final double STANDARD_POWER = 25.0;
    private static final int FULL_WEIGHT = 100_000;

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

    private static final List<Item> DYE_COLORS = List.of(
            Items.WHITE_DYE,
            Items.ORANGE_DYE,
            Items.MAGENTA_DYE,
            Items.LIGHT_BLUE_DYE,
            Items.YELLOW_DYE,
            Items.LIME_DYE,
            Items.PINK_DYE,
            Items.GRAY_DYE,
            Items.LIGHT_GRAY_DYE,
            Items.CYAN_DYE,
            Items.PURPLE_DYE,
            Items.BLUE_DYE,
            Items.BROWN_DYE,
            Items.GREEN_DYE,
            Items.RED_DYE,
            Items.BLACK_DYE
    );

    private static final List<Item> DEAD_CORAL_BLOCKS = List.of(
            Items.DEAD_TUBE_CORAL_BLOCK,
            Items.DEAD_BRAIN_CORAL_BLOCK,
            Items.DEAD_BUBBLE_CORAL_BLOCK,
            Items.DEAD_FIRE_CORAL_BLOCK,
            Items.DEAD_HORN_CORAL_BLOCK
    );

    private static final List<ItemStack> GLINT_DIAMOND_TOOLS = List.of(
            glint(Items.DIAMOND_PICKAXE),
            glint(Items.DIAMOND_AXE),
            glint(Items.DIAMOND_SHOVEL),
            glint(Items.DIAMOND_HOE),
            glint(Items.DIAMOND_SWORD)
    );

    private static final List<PhotoinducedReactionRecipe> RECIPES = List.of(
            new PhotoinducedReactionRecipe(
                    "coal_to_diamond",
                    stack -> stack.is(Items.COAL),
                    100.0,
                    ONE_SECOND_TICKS,
                    () -> List.of(new ItemStack(Items.COAL)),
                    stack -> List.of(result(Items.DIAMOND, FULL_WEIGHT))
            ),
            new PhotoinducedReactionRecipe(
                    "silicon_to_wafer",
                    stack -> stack.is(Spectralization.SILICON.get()),
                    10.0,
                    ONE_SECOND_TICKS,
                    () -> List.of(new ItemStack(Spectralization.SILICON.get())),
                    stack -> List.of(result(Spectralization.BLANK_WAFER.get(), FULL_WEIGHT))
            ),
            new PhotoinducedReactionRecipe(
                    "wool_color_shift",
                    stack -> stack.is(ItemTags.WOOL),
                    20.0,
                    ONE_SECOND_TICKS,
                    PhotoinducedReactionRecipe::woolColorStacks,
                    PhotoinducedReactionRecipe::alternateWoolColorResults
            ),
            dyeRecipe(
                    "black_dye_fragmentation",
                    Items.BLACK_DYE,
                    result(Items.INK_SAC, 30_000),
                    result(Items.WITHER_ROSE, 30_000),
                    result(Items.SCULK_VEIN, 5_000),
                    result(Items.BLACKSTONE, 35_000)
            ),
            dyeRecipe(
                    "light_gray_dye_fragmentation",
                    Items.LIGHT_GRAY_DYE,
                    result(Items.CHAIN, 15_000),
                    result(Items.IRON_BARS, 15_000),
                    result(Items.RAIL, 15_000),
                    result(Items.MINECART, 15_000),
                    result(Items.SMOOTH_STONE, 40_000)
            ),
            new PhotoinducedReactionRecipe(
                    "gray_dye_fragmentation",
                    stack -> stack.is(Items.GRAY_DYE),
                    STANDARD_POWER,
                    ONE_SECOND_TICKS,
                    () -> List.of(new ItemStack(Items.GRAY_DYE)),
                    stack -> {
                        List<WeightedResult> results = new ArrayList<>();
                        results.add(result(Items.ANDESITE, 50_000));
                        results.add(result(Items.TUFF, 49_000));
                        results.addAll(distributed(DEAD_CORAL_BLOCKS, 1_000));
                        return results;
                    }
            ),
            dyeRecipe(
                    "white_dye_fragmentation",
                    Items.WHITE_DYE,
                    result(Items.SNOW_BLOCK, 20_000),
                    result(Items.SNOWBALL, 20_000),
                    result(Items.BONE, 20_000),
                    result(Items.BONE_MEAL, 20_000),
                    result(Items.QUARTZ, 20_000)
            ),
            dyeRecipe(
                    "brown_dye_fragmentation",
                    Items.BROWN_DYE,
                    result(Items.ROTTEN_FLESH, 70_000),
                    result(Items.LEATHER, 15_000),
                    result(Items.RABBIT_HIDE, 14_000),
                    result(Items.RABBIT_FOOT, 1_000)
            ),
            dyeRecipe(
                    "red_dye_fragmentation",
                    Items.RED_DYE,
                    result(Items.REDSTONE, 10_000),
                    result(Items.NETHERRACK, 60_000),
                    result(Items.RED_MUSHROOM_BLOCK, 10_000),
                    result(Items.NETHER_WART, 10_000),
                    result(Items.NETHER_WART_BLOCK, 10_000)
            ),
            new PhotoinducedReactionRecipe(
                    "orange_dye_fragmentation",
                    stack -> stack.is(Items.ORANGE_DYE),
                    STANDARD_POWER,
                    ONE_SECOND_TICKS,
                    () -> List.of(new ItemStack(Items.ORANGE_DYE)),
                    stack -> {
                        List<WeightedResult> results = new ArrayList<>(distributed(vanillaItemsContaining("copper"), 95_000));
                        results.add(result(Items.COPPER_INGOT, 5_000));
                        return results;
                    }
            ),
            dyeRecipe(
                    "yellow_dye_fragmentation",
                    Items.YELLOW_DYE,
                    result(Items.GLOWSTONE_DUST, 9_900),
                    result(Items.SAND, 40_000),
                    result(Items.SANDSTONE, 50_000),
                    result(Spectralization.YAG_CRYSTAL.get(), 100)
            ),
            dyeRecipe(
                    "lime_dye_fragmentation",
                    Items.LIME_DYE,
                    result(Items.SLIME_BALL, 20_000),
                    result(Items.SUGAR_CANE, 20_000),
                    result(Items.VERDANT_FROGLIGHT, 60_000)
            ),
            dyeRecipe(
                    "green_dye_fragmentation",
                    Items.GREEN_DYE,
                    result(Items.OAK_LEAVES, 20_000),
                    result(Items.SEA_PICKLE, 20_000),
                    result(Items.MOSS_BLOCK, 20_000),
                    result(Items.WHEAT_SEEDS, 20_000),
                    result(Items.KELP, 20_000)
            ),
            dyeRecipe(
                    "cyan_dye_fragmentation",
                    Items.CYAN_DYE,
                    result(Items.CLAY_BALL, 50_000),
                    result(Items.CLAY, 20_000),
                    result(Items.PRISMARINE, 15_000),
                    result(Items.WARPED_WART_BLOCK, 15_000)
            ),
            new PhotoinducedReactionRecipe(
                    "light_blue_dye_fragmentation",
                    stack -> stack.is(Items.LIGHT_BLUE_DYE),
                    STANDARD_POWER,
                    ONE_SECOND_TICKS,
                    () -> List.of(new ItemStack(Items.LIGHT_BLUE_DYE)),
                    stack -> {
                        List<WeightedResult> results = new ArrayList<>();
                        results.add(result(Items.DIAMOND, 100));
                        results.addAll(distributedStacks(GLINT_DIAMOND_TOOLS, 10));
                        results.add(result(Items.GLOW_LICHEN, 1_000));
                        results.add(none(98_890));
                        return results;
                    }
            ),
            dyeRecipe(
                    "blue_dye_fragmentation",
                    Items.BLUE_DYE,
                    result(Items.ICE, 40_000),
                    result(Items.PACKED_ICE, 25_000),
                    result(Items.BLUE_ICE, 15_000),
                    result(Items.LAPIS_LAZULI, 19_000),
                    result(Items.HEART_OF_THE_SEA, 1_000)
            ),
            dyeRecipe(
                    "purple_dye_fragmentation",
                    Items.PURPLE_DYE,
                    result(Items.AMETHYST_SHARD, 30_000),
                    result(Items.AMETHYST_BLOCK, 4_995),
                    result(Items.SMALL_AMETHYST_BUD, 25_000),
                    result(Items.MEDIUM_AMETHYST_BUD, 20_000),
                    result(Items.LARGE_AMETHYST_BUD, 15_000),
                    result(Items.AMETHYST_CLUSTER, 5_000),
                    result(Items.BUDDING_AMETHYST, 50)
            ),
            new PhotoinducedReactionRecipe(
                    "magenta_dye_fragmentation",
                    stack -> stack.is(Items.MAGENTA_DYE),
                    STANDARD_POWER,
                    ONE_SECOND_TICKS,
                    () -> List.of(new ItemStack(Items.MAGENTA_DYE)),
                    stack -> {
                        List<WeightedResult> results = new ArrayList<>(distributed(DYE_COLORS, 90_000));
                        results.add(none(10_000));
                        return results;
                    }
            ),
            new PhotoinducedReactionRecipe(
                    "pink_dye_fragmentation",
                    stack -> stack.is(Items.PINK_DYE),
                    STANDARD_POWER,
                    ONE_SECOND_TICKS,
                    () -> List.of(new ItemStack(Items.PINK_DYE)),
                    stack -> {
                        List<WeightedResult> results = new ArrayList<>(distributed(vanillaItemsContaining("cherry"), 99_500));
                        results.add(result(Spectralization.ROD_OF_STRAWBERRY.get(), 500));
                        return results;
                    }
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
        Set<Item> seen = new LinkedHashSet<>();
        return weightedResults(source).stream()
                .map(WeightedResult::stack)
                .filter(stack -> !stack.isEmpty())
                .filter(stack -> seen.add(stack.getItem()))
                .map(stack -> stack.copyWithCount(1))
                .toList();
    }

    public List<WeightedResult> weightedResults(ItemStack source) {
        return results.weightedResults(source).stream()
                .filter(result -> result.weight() > 0)
                .toList();
    }

    public boolean hasRandomResults() {
        List<ItemStack> inputs = displayInputs();
        return !inputs.isEmpty() && weightedResults(inputs.getFirst()).size() > 1;
    }

    public List<ProbabilityEntry> probabilityEntries() {
        List<ItemStack> inputs = displayInputs();
        if (inputs.isEmpty()) {
            return List.of();
        }

        List<WeightedResult> weightedResults = weightedResults(inputs.getFirst());
        int totalWeight = weightedResults.stream()
                .mapToInt(WeightedResult::weight)
                .sum();
        if (totalWeight <= 0) {
            return List.of();
        }

        Map<String, ProbabilityBucket> buckets = new LinkedHashMap<>();
        for (WeightedResult result : weightedResults) {
            String key = probabilityKey(result.stack());
            ProbabilityBucket bucket = buckets.get(key);
            if (bucket == null) {
                buckets.put(key, new ProbabilityBucket(result.stack(), result.weight()));
            } else {
                bucket.add(result.weight());
            }
        }

        return buckets.values().stream()
                .map(bucket -> new ProbabilityEntry(bucket.stack(), bucket.weight(), totalWeight))
                .sorted(Comparator.comparingInt(ProbabilityEntry::weight).reversed())
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

    private static List<WeightedResult> alternateWoolColorResults(ItemStack source) {
        List<WeightedResult> results = new ArrayList<>();
        int sourceIndex = woolColorIndex(source.getItem());

        for (int index = 0; index < WOOL_COLORS.size(); index++) {
            if (index != sourceIndex) {
                results.add(result(WOOL_COLORS.get(index), FULL_WEIGHT / (WOOL_COLORS.size() - 1)));
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

    private static PhotoinducedReactionRecipe dyeRecipe(String id, Item dye, WeightedResult... results) {
        return new PhotoinducedReactionRecipe(
                id,
                stack -> stack.is(dye),
                STANDARD_POWER,
                ONE_SECOND_TICKS,
                () -> List.of(new ItemStack(dye)),
                stack -> List.of(results)
        );
    }

    private static WeightedResult result(Item item, int weight) {
        return result(new ItemStack(item), weight);
    }

    private static WeightedResult result(ItemStack stack, int weight) {
        return new WeightedResult(stack, weight);
    }

    private static WeightedResult none(int weight) {
        return new WeightedResult(ItemStack.EMPTY, weight);
    }

    private static List<WeightedResult> distributed(List<Item> items, int totalWeight) {
        return distributedStacks(
                items.stream()
                        .map(ItemStack::new)
                        .toList(),
                totalWeight
        );
    }

    private static List<WeightedResult> distributedStacks(List<ItemStack> stacks, int totalWeight) {
        List<ItemStack> validStacks = stacks.stream()
                .filter(stack -> !stack.isEmpty())
                .toList();
        if (validStacks.isEmpty() || totalWeight <= 0) {
            return List.of();
        }

        int baseWeight = totalWeight / validStacks.size();
        int remainder = totalWeight % validStacks.size();
        List<WeightedResult> results = new ArrayList<>(validStacks.size());
        for (int index = 0; index < validStacks.size(); index++) {
            int weight = baseWeight + (index < remainder ? 1 : 0);
            if (weight > 0) {
                results.add(result(validStacks.get(index), weight));
            }
        }
        return results;
    }

    private static List<Item> vanillaItemsContaining(String fragment) {
        return BuiltInRegistries.ITEM.stream()
                .filter(item -> item != Items.AIR)
                .filter(item -> {
                    ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
                    return "minecraft".equals(id.getNamespace()) && id.getPath().contains(fragment);
                })
                .sorted(Comparator.comparing(item -> BuiltInRegistries.ITEM.getKey(item).toString()))
                .toList();
    }

    private static ItemStack glint(Item item) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return stack;
    }

    private static String probabilityKey(ItemStack stack) {
        if (stack.isEmpty()) {
            return "empty";
        }

        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id + "|" + stack.getComponents();
    }

    public record WeightedResult(ItemStack stack, int weight) {
        public WeightedResult {
            if (weight <= 0) {
                throw new IllegalArgumentException("Photoinduced result weight must be positive");
            }
            stack = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        }
    }

    public record ProbabilityEntry(ItemStack stack, int weight, int totalWeight) {
        public double chancePercent() {
            return totalWeight <= 0 ? 0.0 : weight * 100.0 / totalWeight;
        }
    }

    private static final class ProbabilityBucket {
        private final ItemStack stack;
        private int weight;

        private ProbabilityBucket(ItemStack stack, int weight) {
            this.stack = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
            this.weight = weight;
        }

        private ItemStack stack() {
            return stack;
        }

        private int weight() {
            return weight;
        }

        private void add(int amount) {
            weight += amount;
        }
    }

    @FunctionalInterface
    private interface ResultProvider {
        List<WeightedResult> weightedResults(ItemStack source);
    }
}
