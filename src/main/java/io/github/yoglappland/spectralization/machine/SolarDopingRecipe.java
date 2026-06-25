package io.github.yoglappland.spectralization.machine;

import io.github.yoglappland.spectralization.Spectralization;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ItemLike;

public record SolarDopingRecipe(
        String id,
        Item input,
        List<WeightedResult> results,
        double baseChance,
        int accentColor
) {
    private static final List<SolarDopingRecipe> RECIPES = List.of(
            recipe(
                    "corundum_ruby_doping",
                    Spectralization.CORUNDUM.get(),
                    () -> new ItemStack(Spectralization.RUBY.get()),
                    0.000060,
                    0xFFFF6F8E,
                    DopingEnvironment.OVERWORLD,
                    DopingEnvironment.OTHER
            ),
            weightedRecipe(
                    "yag_rare_earth_doping",
                    Spectralization.YAG_CRYSTAL.get(),
                    0.000050,
                    0xFFBFA8FF,
                    weightedResult(Spectralization.CE_YAG_CRYSTAL.get(), 35, DopingEnvironment.OVERWORLD, DopingEnvironment.OTHER),
                    weightedResult(Spectralization.ND_YAG_CRYSTAL.get(), 35, DopingEnvironment.OVERWORLD, DopingEnvironment.OTHER),
                    weightedResult(Spectralization.YB_YAG_CRYSTAL.get(), 15, DopingEnvironment.NETHER, DopingEnvironment.END, DopingEnvironment.OTHER),
                    weightedResult(Spectralization.ER_YAG_CRYSTAL.get(), 15, DopingEnvironment.NETHER, DopingEnvironment.END, DopingEnvironment.OTHER)
            ),
            weightedRecipe(
                    "fluorite_rare_earth_doping",
                    Spectralization.FLUORITE.get(),
                    0.000055,
                    0xFF96E6FF,
                    weightedResult(Spectralization.CE_FLUORITE_CRYSTAL.get(), 30, DopingEnvironment.OVERWORLD, DopingEnvironment.OTHER),
                    weightedResult(Spectralization.ND_FLUORITE_CRYSTAL.get(), 30, DopingEnvironment.OVERWORLD, DopingEnvironment.OTHER),
                    weightedResult(Spectralization.YB_FLUORITE_CRYSTAL.get(), 20, DopingEnvironment.NETHER, DopingEnvironment.END, DopingEnvironment.OTHER),
                    weightedResult(Spectralization.ER_FLUORITE_CRYSTAL.get(), 20, DopingEnvironment.NETHER, DopingEnvironment.END, DopingEnvironment.OTHER)
            ),
            weightedRecipe(
                    "silica_rare_earth_doping",
                    Items.QUARTZ,
                    0.000050,
                    0xFFDDEFFF,
                    weightedResult(Spectralization.CE_DOPED_SILICA.get(), 25, DopingEnvironment.OVERWORLD, DopingEnvironment.OTHER),
                    weightedResult(Spectralization.ND_DOPED_SILICA.get(), 25, DopingEnvironment.OVERWORLD, DopingEnvironment.OTHER),
                    weightedResult(Spectralization.YB_DOPED_SILICA.get(), 25, DopingEnvironment.NETHER, DopingEnvironment.END, DopingEnvironment.OTHER),
                    weightedResult(Spectralization.ER_DOPED_SILICA.get(), 25, DopingEnvironment.NETHER, DopingEnvironment.END, DopingEnvironment.OTHER)
            ),
            weightedRecipe(
                    "silicon_electronic_doping",
                    Spectralization.SILICON.get(),
                    0.000065,
                    0xFF9BA6B5,
                    weightedResult(Spectralization.BORON_DOPED_SILICON.get(), 35, DopingEnvironment.OVERWORLD, DopingEnvironment.OTHER),
                    weightedResult(Spectralization.PHOSPHORUS_DOPED_SILICON.get(), 35, DopingEnvironment.OVERWORLD, DopingEnvironment.OTHER),
                    weightedResult(Spectralization.ARSENIC_DOPED_SILICON.get(), 20, DopingEnvironment.NETHER, DopingEnvironment.OTHER),
                    weightedResult(Spectralization.ERBIUM_DOPED_SILICON.get(), 10, DopingEnvironment.END, DopingEnvironment.OTHER)
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
        return find(input, filter, null);
    }

    public static Optional<SolarDopingRecipe> find(
            ItemStack input,
            ItemStack filter,
            @Nullable DopingEnvironment environment
    ) {
        if (input.isEmpty()) {
            return Optional.empty();
        }

        for (SolarDopingRecipe recipe : RECIPES) {
            if (recipe.matches(input, filter)) {
                return environment == null ? Optional.of(recipe) : recipe.forEnvironment(environment);
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

    public Optional<SolarDopingRecipe> forEnvironment(DopingEnvironment environment) {
        if (!environment.usable()) {
            return Optional.empty();
        }

        List<WeightedResult> filteredResults = results.stream()
                .filter(result -> result.allows(environment))
                .toList();
        if (filteredResults.isEmpty()) {
            return Optional.empty();
        }

        if (filteredResults.size() == results.size()) {
            return Optional.of(this);
        }

        return Optional.of(new SolarDopingRecipe(id, input, filteredResults, baseChance, accentColor));
    }

    public Set<DopingEnvironment> availableEnvironments() {
        EnumSet<DopingEnvironment> environments = EnumSet.noneOf(DopingEnvironment.class);
        for (WeightedResult result : results) {
            environments.addAll(result.environments());
        }
        environments.remove(DopingEnvironment.UNAVAILABLE);
        return Set.copyOf(environments);
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

    public enum DopingEnvironment {
        UNAVAILABLE(0, "unavailable"),
        OVERWORLD(1, "overworld"),
        NETHER(2, "nether"),
        END(3, "end"),
        OTHER(4, "other");

        private final int dataId;
        private final String translationKey;

        DopingEnvironment(int dataId, String translationKey) {
            this.dataId = dataId;
            this.translationKey = translationKey;
        }

        public int dataId() {
            return dataId;
        }

        public boolean usable() {
            return this != UNAVAILABLE;
        }

        public String translationKey() {
            return "screen.spectralization.solar_doping_chamber.dimension." + translationKey;
        }

        public static DopingEnvironment byDataId(int dataId) {
            for (DopingEnvironment environment : values()) {
                if (environment.dataId == dataId) {
                    return environment;
                }
            }

            return UNAVAILABLE;
        }

        public static DopingEnvironment fromLevel(Level level) {
            ResourceKey<Level> dimension = level.dimension();
            if (dimension.equals(Level.OVERWORLD)) {
                return OVERWORLD;
            }

            if (dimension.equals(Level.NETHER)) {
                return NETHER;
            }

            if (dimension.equals(Level.END)) {
                return END;
            }

            return OTHER;
        }

        public static DopingEnvironment solarFromLevel(Level level) {
            return fromLevel(level) == OVERWORLD ? OVERWORLD : UNAVAILABLE;
        }

        private static Set<DopingEnvironment> setOf(DopingEnvironment... environments) {
            EnumSet<DopingEnvironment> set = EnumSet.noneOf(DopingEnvironment.class);
            for (DopingEnvironment environment : environments) {
                if (environment.usable()) {
                    set.add(environment);
                }
            }

            return Set.copyOf(set);
        }
    }

    public record WeightedResult(Supplier<ItemStack> result, int weight, Set<DopingEnvironment> environments) {
        public WeightedResult {
            Objects.requireNonNull(result, "result");
            environments = Set.copyOf(environments);
            if (weight <= 0) {
                throw new IllegalArgumentException("Solar doping result weight must be positive");
            }
            if (environments.isEmpty()) {
                throw new IllegalArgumentException("Solar doping result must be available in at least one dimension");
            }
        }

        public ItemStack resultStack() {
            ItemStack stack = result.get();
            stack.setCount(Math.min(1, Math.max(0, stack.getCount())));
            return stack;
        }

        public boolean allows(DopingEnvironment environment) {
            return environment.usable() && environments.contains(environment);
        }
    }

    private static SolarDopingRecipe recipe(
            String id,
            ItemLike input,
            Supplier<ItemStack> result,
            double baseChance,
            int accentColor,
            DopingEnvironment... environments
    ) {
        return new SolarDopingRecipe(
                id,
                input.asItem(),
                List.of(new WeightedResult(result, 100, DopingEnvironment.setOf(environments))),
                baseChance,
                accentColor
        );
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

    private static WeightedResult weightedResult(ItemLike result, int weight, DopingEnvironment... environments) {
        return new WeightedResult(() -> new ItemStack(result), weight, DopingEnvironment.setOf(environments));
    }
}
