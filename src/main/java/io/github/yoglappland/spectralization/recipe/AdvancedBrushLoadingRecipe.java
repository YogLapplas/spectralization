package io.github.yoglappland.spectralization.recipe;

import com.mojang.datafixers.util.Pair;
import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.item.BrushPaintSelection;
import io.github.yoglappland.spectralization.item.PaintBucketItem;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

public class AdvancedBrushLoadingRecipe extends CustomRecipe {
    public AdvancedBrushLoadingRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return findInputs(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        Pair<ItemStack, ItemStack> inputs = findInputs(input);

        if (inputs == null || !(inputs.getSecond().getItem() instanceof PaintBucketItem paintBucket)) {
            return ItemStack.EMPTY;
        }

        int usesLeft = Math.max(0, inputs.getSecond().getMaxDamage() - inputs.getSecond().getDamageValue());

        if (usesLeft <= 0) {
            return ItemStack.EMPTY;
        }

        ItemStack result = inputs.getFirst().copy();
        result.setCount(1);
        BrushPaintSelection.addPaint(result, paintBucket.treatmentKind(), usesLeft);
        return result;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> remainingItems = NonNullList.withSize(input.size(), ItemStack.EMPTY);

        for (int index = 0; index < input.size(); index++) {
            ItemStack stack = input.getItem(index);

            if (stack.getItem() instanceof PaintBucketItem paintBucket) {
                remainingItems.set(index, paintBucket.emptyBucketStack());
            } else if (stack.hasCraftingRemainingItem()) {
                remainingItems.set(index, stack.getCraftingRemainingItem());
            }
        }

        return remainingItems;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return Spectralization.ADVANCED_BRUSH_LOADING_SERIALIZER.get();
    }

    @Nullable
    private static Pair<ItemStack, ItemStack> findInputs(CraftingInput input) {
        ItemStack brush = ItemStack.EMPTY;
        ItemStack paint = ItemStack.EMPTY;

        for (int index = 0; index < input.size(); index++) {
            ItemStack stack = input.getItem(index);

            if (stack.isEmpty()) {
                continue;
            }

            if (stack.is(Spectralization.ADVANCED_BRUSH.get()) && brush.isEmpty() && stack.getCount() == 1) {
                brush = stack;
            } else if (stack.getItem() instanceof PaintBucketItem && paint.isEmpty() && stack.getCount() == 1) {
                paint = stack;
            } else {
                return null;
            }
        }

        if (brush.isEmpty() || paint.isEmpty()) {
            return null;
        }

        return Pair.of(brush, paint);
    }
}
