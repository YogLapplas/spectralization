package io.github.yoglappland.spectralization.menu;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.blockentity.SpectrometerBlockEntity;
import io.github.yoglappland.spectralization.optics.SpectralRegion;
import io.github.yoglappland.spectralization.registry.SpectralMenus;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public class SpectrometerMenu extends AbstractContainerMenu {
    public static final int BUTTON_REGION_DOWN = 0;
    public static final int BUTTON_REGION_UP = 1;

    private final SpectrometerBlockEntity spectrometer;
    private final ContainerData data;

    public SpectrometerMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, null, new SimpleContainerData(SpectrometerBlockEntity.DATA_COUNT));
    }

    public SpectrometerMenu(int containerId, Inventory inventory, SpectrometerBlockEntity spectrometer) {
        this(containerId, inventory, spectrometer, spectrometer.createDataAccess());
    }

    private SpectrometerMenu(
            int containerId,
            Inventory inventory,
            SpectrometerBlockEntity spectrometer,
            ContainerData data
    ) {
        super(SpectralMenus.SPECTROMETER.get(), containerId);
        this.spectrometer = spectrometer;
        this.data = data;
        this.addDataSlots(data);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        return switch (id) {
            case BUTTON_REGION_DOWN -> cycleRegion(-1);
            case BUTTON_REGION_UP -> cycleRegion(1);
            default -> false;
        };
    }

    public int getData(int index) {
        return data.get(index);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        if (spectrometer == null || spectrometer.isRemoved()) {
            return true;
        }

        return player.distanceToSqr(Vec3.atCenterOf(spectrometer.getBlockPos())) <= 64.0
                && spectrometer.getLevel() != null
                && spectrometer.getLevel().getBlockState(spectrometer.getBlockPos()).is(Spectralization.SPECTROMETER.get());
    }

    private boolean cycleRegion(int amount) {
        int next = Math.floorMod(
                data.get(SpectrometerBlockEntity.DATA_REGION) + amount,
                SpectralRegion.values().length
        );
        data.set(SpectrometerBlockEntity.DATA_REGION, next);
        return true;
    }
}
