package io.github.yoglappland.spectralization.machine;

import io.github.yoglappland.spectralization.Spectralization;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

public record BasicLithographyRecipe(
        List<ItemCost> itemCosts,
        @Nullable Item templateItem,
        Supplier<ItemStack> result,
        OutputKind outputKind,
        int processTicks,
        double requiredCoherentPower
) {
    private static final List<BasicLithographyRecipe> RECIPES = List.of(
            new BasicLithographyRecipe(
                    List.of(cost(Items.IRON_INGOT, 1)),
                    null,
                    () -> new ItemStack(Spectralization.BASIC_MASK.get()),
                    OutputKind.ITEM,
                    80,
                    0.0
            ),
            new BasicLithographyRecipe(
                    List.of(
                            cost(Items.IRON_INGOT, 1),
                            cost(Items.REDSTONE, 1),
                            cost(Items.COPPER_INGOT, 1)
                    ),
                    Spectralization.BASIC_MASK.get(),
                    () -> new ItemStack(Spectralization.PRIMITIVE_CIRCUIT_BOARD.get()),
                    OutputKind.ITEM,
                    120,
                    0.0
            ),
            new BasicLithographyRecipe(
                    List.of(
                            cost(Spectralization.COATED_WAFER.get(), 1),
                            cost(Items.REDSTONE, 1),
                            cost(Items.COPPER_INGOT, 1)
                    ),
                    Spectralization.BASIC_MASK.get(),
                    () -> new ItemStack(Spectralization.PRIMITIVE_CIRCUIT_BOARD.get()),
                    OutputKind.ITEM,
                    120,
                    0.0
            )
    );

    public BasicLithographyRecipe {
        itemCosts = List.copyOf(itemCosts);

        if (processTicks <= 0) {
            throw new IllegalArgumentException("Lithography process ticks must be positive");
        }

        if (!Double.isFinite(requiredCoherentPower) || requiredCoherentPower < 0.0) {
            throw new IllegalArgumentException("Lithography coherent power must be finite and non-negative");
        }
    }

    public static Optional<Match> find(ItemStack templateA, ItemStack templateB, List<ItemStack> itemInputs) {
        for (BasicLithographyRecipe recipe : RECIPES) {
            int templateSlot = recipe.matchingTemplateSlot(templateA, templateB);

            if (templateSlot >= -1 && recipe.matchesItemInputs(itemInputs)) {
                return Optional.of(new Match(recipe, templateSlot));
            }
        }

        return Optional.empty();
    }

    public static boolean isPotentialInput(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        return RECIPES.stream().anyMatch(recipe -> recipe.itemCosts.stream()
                .anyMatch(cost -> stack.is(cost.item())));
    }

    public static List<BasicLithographyRecipe> recipes() {
        return RECIPES;
    }

    public ItemStack resultStack() {
        return result.get();
    }

    public boolean usesTemplate() {
        return templateItem != null;
    }

    private int matchingTemplateSlot(ItemStack templateA, ItemStack templateB) {
        if (templateItem == null) {
            return templateA.isEmpty() && templateB.isEmpty() ? -1 : -2;
        }

        if (templateA.is(templateItem)) {
            return 0;
        }

        if (templateB.is(templateItem)) {
            return 1;
        }

        return -2;
    }

    private boolean matchesItemInputs(List<ItemStack> itemInputs) {
        for (ItemStack stack : itemInputs) {
            if (!stack.isEmpty() && itemCosts.stream().noneMatch(cost -> stack.is(cost.item()))) {
                return false;
            }
        }

        for (ItemCost cost : itemCosts) {
            int count = 0;

            for (ItemStack stack : itemInputs) {
                if (stack.is(cost.item())) {
                    count += stack.getCount();
                }
            }

            if (count < cost.count()) {
                return false;
            }
        }

        return true;
    }

    private static ItemCost cost(ItemLike item, int count) {
        return new ItemCost(item.asItem(), count);
    }

    public enum OutputKind {
        TEMPLATE,
        ITEM
    }

    public record ItemCost(Item item, int count) {
        public ItemCost {
            if (count <= 0) {
                throw new IllegalArgumentException("Lithography item cost must be positive");
            }
        }
    }

    public record Match(BasicLithographyRecipe recipe, int templateSlot) {
    }
}
