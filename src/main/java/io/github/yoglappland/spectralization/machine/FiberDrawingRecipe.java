package io.github.yoglappland.spectralization.machine;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.optics.fiber.FiberMaterialProfile;
import io.github.yoglappland.spectralization.optics.lens.LensMaterial;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

public record FiberDrawingRecipe(
        String id,
        LensMaterial material,
        Supplier<ItemStack> materialStack,
        Supplier<ItemStack> moldStack,
        Supplier<ItemStack> resultStack,
        boolean singleMode
) {
    private static final List<FiberDrawingRecipe> RECIPES = List.of(
            normalItem("ordinary_fiber", LensMaterial.ORDINARY, Items.GLASS),
            normal("quartz_fiber", LensMaterial.QUARTZ, Spectralization.QUARTZ_GLASS_ITEM),
            singleMode("quartz_single_mode_fiber", LensMaterial.QUARTZ, Spectralization.QUARTZ_GLASS_ITEM),
            normal("borosilicate_fiber", LensMaterial.BOROSILICATE, Spectralization.BOROSILICATE_GLASS_ITEM),
            singleMode("borosilicate_single_mode_fiber", LensMaterial.BOROSILICATE, Spectralization.BOROSILICATE_GLASS_ITEM),
            normal("crown_fiber", LensMaterial.CROWN, Spectralization.CROWN_GLASS_ITEM),
            singleMode("crown_single_mode_fiber", LensMaterial.CROWN, Spectralization.CROWN_GLASS_ITEM),
            normal("flint_fiber", LensMaterial.FLINT, Spectralization.FLINT_GLASS_ITEM),
            singleMode("flint_single_mode_fiber", LensMaterial.FLINT, Spectralization.FLINT_GLASS_ITEM),
            normal("heavy_fiber", LensMaterial.HEAVY, Spectralization.HEAVY_GLASS_ITEM)
    );

    public FiberDrawingRecipe {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Fiber drawing recipe id must not be blank");
        }

        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(materialStack, "materialStack");
        Objects.requireNonNull(moldStack, "moldStack");
        Objects.requireNonNull(resultStack, "resultStack");
    }

    public static List<FiberDrawingRecipe> recipes() {
        return RECIPES;
    }

    public ItemStack materialItem() {
        return materialStack.get().copy();
    }

    public ItemStack moldItem() {
        return moldStack.get().copy();
    }

    public ItemStack resultItem() {
        ItemStack result = resultStack.get().copy();
        FiberMaterialProfile.fromMaterial(material, singleMode)
                .ifPresent(profile -> profile.applyTo(result));
        return result;
    }

    public ItemStack returnedMoldItem() {
        return moldItem();
    }

    public FiberDrawingParameters parameters() {
        return FiberDrawingParameters.from(material, singleMode);
    }

    public Optional<FiberMaterialProfile> profile() {
        return FiberMaterialProfile.fromMaterial(material, singleMode);
    }

    private static FiberDrawingRecipe normalItem(String id, LensMaterial material, ItemLike glass) {
        return recipeFromStack(id, material, () -> new ItemStack(glass), Spectralization.FIBER_MOLD,
                Spectralization.OPTICAL_FIBER_COIL, false);
    }

    private static FiberDrawingRecipe singleModeItem(String id, LensMaterial material, ItemLike glass) {
        return recipeFromStack(id, material, () -> new ItemStack(glass), Spectralization.SINGLE_MODE_FIBER_MOLD,
                Spectralization.SINGLE_MODE_FIBER_COIL, true);
    }

    private static FiberDrawingRecipe normal(String id, LensMaterial material, Supplier<? extends ItemLike> glass) {
        return recipe(id, material, glass, Spectralization.FIBER_MOLD, Spectralization.OPTICAL_FIBER_COIL, false);
    }

    private static FiberDrawingRecipe singleMode(String id, LensMaterial material, Supplier<? extends ItemLike> glass) {
        return recipe(id, material, glass, Spectralization.SINGLE_MODE_FIBER_MOLD,
                Spectralization.SINGLE_MODE_FIBER_COIL, true);
    }

    private static FiberDrawingRecipe recipeFromStack(
            String id,
            LensMaterial material,
            Supplier<ItemStack> materialStack,
            Supplier<? extends ItemLike> mold,
            Supplier<? extends ItemLike> result,
            boolean singleMode
    ) {
        return new FiberDrawingRecipe(
                id,
                material,
                materialStack,
                () -> new ItemStack(mold.get()),
                () -> new ItemStack(result.get()),
                singleMode
        );
    }

    private static FiberDrawingRecipe recipe(
            String id,
            LensMaterial material,
            Supplier<? extends ItemLike> glass,
            Supplier<? extends ItemLike> mold,
            Supplier<? extends ItemLike> result,
            boolean singleMode
    ) {
        return new FiberDrawingRecipe(
                id,
                material,
                () -> new ItemStack(glass.get()),
                () -> new ItemStack(mold.get()),
                () -> new ItemStack(result.get()),
                singleMode
        );
    }
}
