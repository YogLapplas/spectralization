package io.github.yoglappland.spectralization.menu;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.blockentity.CreativeLightSourceBlockEntity;
import io.github.yoglappland.spectralization.registry.SpectralMenus;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public class CreativeLightSourceMenu extends AbstractContainerMenu {
    public static final int BUTTON_REGION_DOWN = 0;
    public static final int BUTTON_REGION_UP = 1;
    public static final int BUTTON_BIN_DOWN = 2;
    public static final int BUTTON_BIN_UP = 3;
    public static final int BUTTON_POWER_DOWN = 4;
    public static final int BUTTON_POWER_UP = 5;
    public static final int BUTTON_COHERENCE = 6;
    public static final int BUTTON_MODEL_DOWN = 7;
    public static final int BUTTON_MODEL_UP = 8;
    public static final int BUTTON_RADIUS_DOWN = 9;
    public static final int BUTTON_RADIUS_UP = 10;
    public static final int BUTTON_DIVERGENCE_DOWN = 11;
    public static final int BUTTON_DIVERGENCE_UP = 12;
    public static final int BUTTON_FOCUS_DOWN = 13;
    public static final int BUTTON_FOCUS_UP = 14;
    public static final int BUTTON_MODE_M_DOWN = 15;
    public static final int BUTTON_MODE_M_UP = 16;
    public static final int BUTTON_MODE_N_DOWN = 17;
    public static final int BUTTON_MODE_N_UP = 18;

    private final CreativeLightSourceBlockEntity source;
    private final ContainerData data;

    public CreativeLightSourceMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, null, new SimpleContainerData(CreativeLightSourceBlockEntity.DATA_COUNT));
    }

    public CreativeLightSourceMenu(
            int containerId,
            Inventory inventory,
            CreativeLightSourceBlockEntity source
    ) {
        this(containerId, inventory, source, source.createDataAccess());
    }

    private CreativeLightSourceMenu(
            int containerId,
            Inventory inventory,
            CreativeLightSourceBlockEntity source,
            ContainerData data
    ) {
        super(SpectralMenus.CREATIVE_LIGHT_SOURCE.get(), containerId);
        this.source = source;
        this.data = data;
        this.addDataSlots(data);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        return switch (id) {
            case BUTTON_REGION_DOWN -> adjust(CreativeLightSourceBlockEntity.DATA_REGION, -1);
            case BUTTON_REGION_UP -> adjust(CreativeLightSourceBlockEntity.DATA_REGION, 1);
            case BUTTON_BIN_DOWN -> adjust(CreativeLightSourceBlockEntity.DATA_BIN, -1);
            case BUTTON_BIN_UP -> adjust(CreativeLightSourceBlockEntity.DATA_BIN, 1);
            case BUTTON_POWER_DOWN -> adjust(CreativeLightSourceBlockEntity.DATA_POWER, -10);
            case BUTTON_POWER_UP -> adjust(CreativeLightSourceBlockEntity.DATA_POWER, 10);
            case BUTTON_COHERENCE -> cycle(CreativeLightSourceBlockEntity.DATA_COHERENCE, 1, 2);
            case BUTTON_MODEL_DOWN -> cycle(CreativeLightSourceBlockEntity.DATA_BEAM_MODEL, -1, 4);
            case BUTTON_MODEL_UP -> cycle(CreativeLightSourceBlockEntity.DATA_BEAM_MODEL, 1, 4);
            case BUTTON_RADIUS_DOWN -> adjust(CreativeLightSourceBlockEntity.DATA_RADIUS_MILLI, -50);
            case BUTTON_RADIUS_UP -> adjust(CreativeLightSourceBlockEntity.DATA_RADIUS_MILLI, 50);
            case BUTTON_DIVERGENCE_DOWN -> adjust(CreativeLightSourceBlockEntity.DATA_DIVERGENCE_MILLI, -10);
            case BUTTON_DIVERGENCE_UP -> adjust(CreativeLightSourceBlockEntity.DATA_DIVERGENCE_MILLI, 10);
            case BUTTON_FOCUS_DOWN -> adjust(CreativeLightSourceBlockEntity.DATA_FOCUS_DISTANCE_MILLI, -100);
            case BUTTON_FOCUS_UP -> adjust(CreativeLightSourceBlockEntity.DATA_FOCUS_DISTANCE_MILLI, 100);
            case BUTTON_MODE_M_DOWN -> adjust(CreativeLightSourceBlockEntity.DATA_MODE_M, -1);
            case BUTTON_MODE_M_UP -> adjust(CreativeLightSourceBlockEntity.DATA_MODE_M, 1);
            case BUTTON_MODE_N_DOWN -> adjust(CreativeLightSourceBlockEntity.DATA_MODE_N, -1);
            case BUTTON_MODE_N_UP -> adjust(CreativeLightSourceBlockEntity.DATA_MODE_N, 1);
            default -> false;
        };
    }

    public int getData(int index) {
        return data.get(index);
    }

    public boolean setSpectrumWeight(int bin, int weight) {
        return setSpectrumWeight(bin, weight, false);
    }

    public boolean setSpectrumWeight(int bin, int weight, boolean exclusive) {
        if (source == null || source.isRemoved()) {
            return false;
        }

        if (bin < 0 || bin >= CreativeLightSourceBlockEntity.MAX_SPECTRUM_BINS) {
            return false;
        }

        return source.setSpectrumWeight(bin, weight, exclusive);
    }

    public boolean setPowerCenti(int powerCenti) {
        if (source == null || source.isRemoved()) {
            return false;
        }

        data.set(CreativeLightSourceBlockEntity.DATA_POWER, powerCenti);
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        if (source == null || source.isRemoved()) {
            return true;
        }

        return player.distanceToSqr(Vec3.atCenterOf(source.getBlockPos())) <= 64.0
                && source.getLevel() != null
                && source.getLevel().getBlockState(source.getBlockPos()).is(Spectralization.CREATIVE_LIGHT_SOURCE.get());
    }

    private boolean adjust(int index, int amount) {
        data.set(index, data.get(index) + amount);
        return true;
    }

    private boolean cycle(int index, int amount, int size) {
        int next = Math.floorMod(data.get(index) + amount, size);
        data.set(index, next);
        return true;
    }
}
