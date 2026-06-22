package io.github.yoglappland.spectralization.recipe;

import com.mojang.datafixers.util.Pair;
import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.optics.singular.SingularMaterialData;
import io.github.yoglappland.spectralization.tag.SpectralItemTags;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

public class SingularMaterialInfusionRecipe extends CustomRecipe {
    public SingularMaterialInfusionRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return findInputs(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        Pair<ItemStack, ItemStack> inputs = findInputs(input);

        if (inputs == null) {
            return ItemStack.EMPTY;
        }

        ResourceLocation sourceItemId = BuiltInRegistries.ITEM.getKey(inputs.getSecond().getItem());
        return SingularMaterialData.fromSource(sourceItemId.toString()).createStack();
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

            if (stack.hasCraftingRemainingItem()) {
                remainingItems.set(index, stack.getCraftingRemainingItem());
            }
        }

        return remainingItems;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return Spectralization.SINGULAR_MATERIAL_INFUSION_SERIALIZER.get();
    }

    @Nullable
    private static Pair<ItemStack, ItemStack> findInputs(CraftingInput input) {
        ItemStack singularMaterial = ItemStack.EMPTY;
        ItemStack sourceItem = ItemStack.EMPTY;

        for (int index = 0; index < input.size(); index++) {
            ItemStack stack = input.getItem(index);

            if (stack.isEmpty()) {
                continue;
            }

            if (stack.is(SpectralItemTags.SINGULAR_MATERIAL) && singularMaterial.isEmpty()) {
                singularMaterial = stack;
            } else if (!stack.is(SpectralItemTags.SINGULAR_MATERIAL) && sourceItem.isEmpty()) {
                sourceItem = stack;
            } else {
                return null;
            }
        }

        if (singularMaterial.isEmpty() || sourceItem.isEmpty()) {
            return null;
        }

        return Pair.of(singularMaterial, sourceItem);
    }
}
