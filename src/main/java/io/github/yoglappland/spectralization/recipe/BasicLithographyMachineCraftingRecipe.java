package io.github.yoglappland.spectralization.recipe;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.optics.lens.LensProfile;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

public class BasicLithographyMachineCraftingRecipe extends CustomRecipe {
    public BasicLithographyMachineCraftingRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        if (input.width() != 3 || input.height() != 3) {
            return false;
        }

        return input.getItem(0).is(Items.IRON_INGOT)
                && isShortFocalLens(input.getItem(1))
                && input.getItem(2).is(Items.IRON_INGOT)
                && input.getItem(3).is(Items.REDSTONE)
                && input.getItem(4).is(Items.BLAST_FURNACE)
                && input.getItem(5).is(Items.REDSTONE)
                && input.getItem(6).is(Items.IRON_INGOT)
                && input.getItem(7).is(Items.SMOOTH_STONE)
                && input.getItem(8).is(Items.IRON_INGOT);
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        return matches(input, null)
                ? new ItemStack(Spectralization.BASIC_LITHOGRAPHY_MACHINE_ITEM.get())
                : ItemStack.EMPTY;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width >= 3 && height >= 3;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return Spectralization.BASIC_LITHOGRAPHY_MACHINE_CRAFTING_SERIALIZER.get();
    }

    private static boolean isShortFocalLens(ItemStack stack) {
        return stack.is(Spectralization.LENS.get())
                && LensProfile.fromStack(stack).focalLengthBlocks() < 1.0D;
    }
}
