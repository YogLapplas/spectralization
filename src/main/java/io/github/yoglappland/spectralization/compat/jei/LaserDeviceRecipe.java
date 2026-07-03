package io.github.yoglappland.spectralization.compat.jei;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.blockentity.FiberLaserBlockEntity;
import io.github.yoglappland.spectralization.optics.OpticalMaterialProfiles;
import java.util.List;
import net.minecraft.world.item.ItemStack;

public record LaserDeviceRecipe(
        ItemStack stack,
        double singlePassGain,
        double gainLimit,
        double handlingLimit
) {
    public static List<LaserDeviceRecipe> recipes() {
        return List.of(
                new LaserDeviceRecipe(
                        new ItemStack(Spectralization.RUBY_BLOCK_ITEM.get()),
                        OpticalMaterialProfiles.rubyMaximumEffectiveSinglePassGain(),
                        OpticalMaterialProfiles.rubyScheduledGainUpperLimit(),
                        OpticalMaterialProfiles.rubyHandlingLimit()
                ),
                new LaserDeviceRecipe(
                        new ItemStack(Spectralization.FIBER_LASER_ITEM.get()),
                        FiberLaserBlockEntity.maximumScheduledCoherentBaseGain(),
                        FiberLaserBlockEntity.maximumScheduledCoherentBaseGain(),
                        Double.NaN
                )
        );
    }
}
