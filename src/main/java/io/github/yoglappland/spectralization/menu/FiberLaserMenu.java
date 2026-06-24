package io.github.yoglappland.spectralization.menu;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.blockentity.FiberLaserBlockEntity;
import io.github.yoglappland.spectralization.registry.SpectralMenus;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public class FiberLaserMenu extends AbstractContainerMenu {
    private final FiberLaserBlockEntity laser;
    private final ContainerData data;

    public FiberLaserMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, null, new SimpleContainerData(FiberLaserBlockEntity.DATA_COUNT));
    }

    public FiberLaserMenu(int containerId, Inventory inventory, FiberLaserBlockEntity laser) {
        this(containerId, inventory, laser, laser.createDataAccess());
    }

    private FiberLaserMenu(
            int containerId,
            Inventory inventory,
            FiberLaserBlockEntity laser,
            ContainerData data
    ) {
        super(SpectralMenus.FIBER_LASER.get(), containerId);
        this.laser = laser;
        this.data = data;
        addDataSlots(data);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (laser == null || id < 0 || id > 100) {
            return false;
        }

        laser.setEnergyPercent(id);
        return true;
    }

    public int getData(int index) {
        return data.get(index);
    }

    public int energyPerTick() {
        return getData(FiberLaserBlockEntity.DATA_ENERGY_PER_TICK);
    }

    public int maxEnergyPerTick() {
        return Math.max(1, getData(FiberLaserBlockEntity.DATA_MAX_ENERGY_PER_TICK));
    }

    public int gainX1000() {
        return getData(FiberLaserBlockEntity.DATA_GAIN_X1000);
    }

    public int actualEnergyPerTick() {
        return getData(FiberLaserBlockEntity.DATA_ACTUAL_ENERGY_PER_TICK);
    }

    public int energyStored() {
        return getData(FiberLaserBlockEntity.DATA_ENERGY_STORED);
    }

    public int energyCapacity() {
        return Math.max(1, getData(FiberLaserBlockEntity.DATA_ENERGY_CAPACITY));
    }

    public int pumpPercent() {
        return getData(FiberLaserBlockEntity.DATA_PUMP_PERCENT);
    }

    public boolean active() {
        return getData(FiberLaserBlockEntity.DATA_ACTIVE) != 0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        if (laser == null || laser.isRemoved()) {
            return true;
        }

        return player.distanceToSqr(Vec3.atCenterOf(laser.getBlockPos())) <= 64.0
                && laser.getLevel() != null
                && laser.getLevel().getBlockState(laser.getBlockPos()).is(Spectralization.FIBER_LASER.get());
    }
}
