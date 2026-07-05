package io.github.yoglappland.spectralization.compat.jei;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.blockentity.FiberLaserBlockEntity;
import io.github.yoglappland.spectralization.optics.OpticalMaterialProfiles;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public record LaserDeviceRecipe(
        ItemStack stack,
        double gainPerPumpUnit,
        double referencePump,
        double nominalSinglePassGain,
        double saturationPower,
        double handlingLimit
) {
    public static List<LaserDeviceRecipe> recipes() {
        return List.of(
                gainMedium(
                        new ItemStack(Spectralization.RUBY_BLOCK_ITEM.get()),
                        Spectralization.RUBY_BLOCK.get().defaultBlockState()
                ),
                gainMedium(
                        new ItemStack(Spectralization.CE_YAG_CRYSTAL_BLOCK_ITEM.get()),
                        Spectralization.CE_YAG_CRYSTAL_BLOCK.get().defaultBlockState()
                ),
                gainMedium(
                        new ItemStack(Spectralization.ND_YAG_CRYSTAL_BLOCK_ITEM.get()),
                        Spectralization.ND_YAG_CRYSTAL_BLOCK.get().defaultBlockState()
                ),
                gainMedium(
                        new ItemStack(Spectralization.YB_YAG_CRYSTAL_BLOCK_ITEM.get()),
                        Spectralization.YB_YAG_CRYSTAL_BLOCK.get().defaultBlockState()
                ),
                gainMedium(
                        new ItemStack(Spectralization.ER_YAG_CRYSTAL_BLOCK_ITEM.get()),
                        Spectralization.ER_YAG_CRYSTAL_BLOCK.get().defaultBlockState()
                ),
                gainMedium(
                        new ItemStack(Spectralization.CE_FLUORITE_CRYSTAL_BLOCK_ITEM.get()),
                        Spectralization.CE_FLUORITE_CRYSTAL_BLOCK.get().defaultBlockState()
                ),
                gainMedium(
                        new ItemStack(Spectralization.ND_FLUORITE_CRYSTAL_BLOCK_ITEM.get()),
                        Spectralization.ND_FLUORITE_CRYSTAL_BLOCK.get().defaultBlockState()
                ),
                gainMedium(
                        new ItemStack(Spectralization.YB_FLUORITE_CRYSTAL_BLOCK_ITEM.get()),
                        Spectralization.YB_FLUORITE_CRYSTAL_BLOCK.get().defaultBlockState()
                ),
                gainMedium(
                        new ItemStack(Spectralization.ER_FLUORITE_CRYSTAL_BLOCK_ITEM.get()),
                        Spectralization.ER_FLUORITE_CRYSTAL_BLOCK.get().defaultBlockState()
                ),
                fiberLaser()
        );
    }

    private static LaserDeviceRecipe gainMedium(ItemStack stack, BlockState state) {
        return new LaserDeviceRecipe(
                stack,
                OpticalMaterialProfiles.gainPerPumpUnitFor(state),
                OpticalMaterialProfiles.referencePumpFor(state),
                OpticalMaterialProfiles.maximumEffectiveSinglePassGainFor(state),
                OpticalMaterialProfiles.saturationPowerFor(state),
                OpticalMaterialProfiles.handlingLimitFor(state)
        );
    }

    private static LaserDeviceRecipe fiberLaser() {
        return new LaserDeviceRecipe(
                new ItemStack(Spectralization.FIBER_LASER_ITEM.get()),
                Double.NaN,
                Double.NaN,
                FiberLaserBlockEntity.maximumScheduledCoherentBaseGain(),
                FiberLaserBlockEntity.maximumSaturatedCoherentExtraOutput(),
                Double.NaN
        );
    }
}
