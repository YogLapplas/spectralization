package io.github.yoglappland.spectralization.machine;

import io.github.yoglappland.spectralization.Spectralization;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

public record BasicLithographyRecipe(
        List<ItemCost> itemCosts,
        List<Item> templateItems,
        Supplier<ItemStack> result,
        OutputKind outputKind,
        int processTicks,
        double requiredCoherentPower
) {
    private static final List<BasicLithographyRecipe> RECIPES = List.of(
            new BasicLithographyRecipe(
                    List.of(cost(Items.IRON_INGOT, 1)),
                    List.of(),
                    () -> new ItemStack(Spectralization.BASIC_MASK.get()),
                    OutputKind.TEMPLATE,
                    80,
                    0.0
            ),
            new BasicLithographyRecipe(
                    List.of(
                            cost(Items.COPPER_INGOT, 1),
                            cost(Items.REDSTONE, 1),
                            cost(Items.SLIME_BALL, 1),
                            cost(Spectralization.BLANK_WAFER.get(), 1)
                    ),
                    List.of(Spectralization.BASIC_MASK.get(), Spectralization.CIRCUIT_MASK.get()),
                    () -> new ItemStack(Spectralization.PRIMITIVE_CIRCUIT_BOARD.get()),
                    OutputKind.ITEM,
                    120,
                    0.0
            )
    );

    public BasicLithographyRecipe {
        itemCosts = List.copyOf(itemCosts);
        templateItems = List.copyOf(templateItems);

        if (processTicks <= 0) {
            throw new IllegalArgumentException("Lithography process ticks must be positive");
        }

        if (!Double.isFinite(requiredCoherentPower) || requiredCoherentPower < 0.0) {
            throw new IllegalArgumentException("Lithography coherent power must be finite and non-negative");
        }
    }

    public static Optional<Match> find(ItemStack templateA, ItemStack templateB, List<ItemStack> itemInputs) {
        for (BasicLithographyRecipe recipe : RECIPES) {
            List<Integer> templateSlots = recipe.matchingTemplateSlots(templateA, templateB);

            if (templateSlots != null && recipe.matchesItemInputs(itemInputs)) {
                return Optional.of(new Match(recipe, templateSlots));
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
        return !templateItems.isEmpty();
    }

    private List<Integer> matchingTemplateSlots(ItemStack templateA, ItemStack templateB) {
        List<ItemStack> stacks = List.of(templateA, templateB);

        if (templateItems.isEmpty()) {
            return templateA.isEmpty() && templateB.isEmpty() ? List.of() : null;
        }

        boolean[] used = new boolean[stacks.size()];
        List<Integer> slots = new ArrayList<>();

        for (Item templateItem : templateItems) {
            int foundSlot = -1;

            for (int slot = 0; slot < stacks.size(); slot++) {
                if (!used[slot] && stacks.get(slot).is(templateItem)) {
                    foundSlot = slot;
                    break;
                }
            }

            if (foundSlot < 0) {
                return null;
            }

            used[foundSlot] = true;
            slots.add(foundSlot);
        }

        for (int slot = 0; slot < stacks.size(); slot++) {
            if (!stacks.get(slot).isEmpty() && !used[slot]) {
                return null;
            }
        }

        return List.copyOf(slots);
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

    public record Match(BasicLithographyRecipe recipe, List<Integer> templateSlots) {
        public Match {
            templateSlots = List.copyOf(templateSlots);
        }
    }
}
