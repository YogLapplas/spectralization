package io.github.yoglappland.spectralization.menu;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.item.BrushPaintSelection;
import io.github.yoglappland.spectralization.optics.surface.SurfaceTreatmentKind;
import io.github.yoglappland.spectralization.registry.SpectralMenus;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

public class CoatingBrushMenu extends AbstractContainerMenu {
    public static final int DATA_SELECTED_INDEX = 0;
    public static final int DATA_CREATIVE = 1;
    public static final int DATA_SILVER_USES = 2;
    public static final int DATA_GOLD_USES = 3;
    public static final int DATA_COUNT = 4;

    private final Inventory inventory;
    private final ContainerData data;

    public CoatingBrushMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, new SimpleContainerData(DATA_COUNT));
    }

    private CoatingBrushMenu(int containerId, Inventory inventory, ContainerData data) {
        super(SpectralMenus.COATING_BRUSH.get(), containerId);
        this.inventory = inventory;
        this.data = data;
        refreshData();
        this.addDataSlots(data);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id < 0 || id >= BrushPaintSelection.SELECTABLE_TREATMENTS.size() || !isPaintVisible(id)) {
            return false;
        }

        ItemStack brush = brushStack(player);

        if (brush.isEmpty()) {
            return false;
        }

        SurfaceTreatmentKind treatmentKind = BrushPaintSelection.treatmentByIndex(id);
        BrushPaintSelection.setSelectedTreatment(brush, treatmentKind);
        refreshData();
        return true;
    }

    public int getData(int index) {
        return data.get(index);
    }

    public ItemStack iconFor(int index) {
        SurfaceTreatmentKind kind = BrushPaintSelection.treatmentByIndex(index);

        return switch (kind) {
            case GOLDING -> new ItemStack(Spectralization.GOLD_PAINT_BUCKET.get());
            case SILVERING -> new ItemStack(Spectralization.SILVER_PAINT_BUCKET.get());
            default -> ItemStack.EMPTY;
        };
    }

    public static ComponentKey labelFor(int index) {
        SurfaceTreatmentKind kind = BrushPaintSelection.treatmentByIndex(index);

        return switch (kind) {
            case GOLDING -> new ComponentKey("surface.spectralization.treatment.golding");
            case SILVERING -> new ComponentKey("surface.spectralization.treatment.silvering");
            default -> new ComponentKey("surface.spectralization.treatment.none");
        };
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return !brushStack(player).isEmpty();
    }

    @Override
    public void broadcastChanges() {
        refreshData();
        super.broadcastChanges();
    }

    public boolean isPaintVisible(int index) {
        return getData(DATA_CREATIVE) == 1 || usesFor(index) > 0;
    }

    public int usesFor(int index) {
        SurfaceTreatmentKind kind = BrushPaintSelection.treatmentByIndex(index);

        return switch (kind) {
            case GOLDING -> getData(DATA_GOLD_USES);
            case SILVERING -> getData(DATA_SILVER_USES);
            default -> 0;
        };
    }

    private void refreshData() {
        ItemStack brush = brushStack(inventory.player);
        data.set(DATA_SELECTED_INDEX, brush.isEmpty() ? 0 : BrushPaintSelection.selectedIndex(brush));
        data.set(DATA_CREATIVE, brush.is(Spectralization.CREATIVE_BRUSH.get()) ? 1 : 0);
        data.set(DATA_SILVER_USES, brush.isEmpty() ? 0 : BrushPaintSelection.storedUses(brush, SurfaceTreatmentKind.SILVERING));
        data.set(DATA_GOLD_USES, brush.isEmpty() ? 0 : BrushPaintSelection.storedUses(brush, SurfaceTreatmentKind.GOLDING));
    }

    private static ItemStack brushStack(Player player) {
        ItemStack mainHand = player.getMainHandItem();

        if (mainHand.is(Spectralization.ADVANCED_BRUSH.get()) || mainHand.is(Spectralization.CREATIVE_BRUSH.get())) {
            return mainHand;
        }

        return ItemStack.EMPTY;
    }

    public record ComponentKey(String key) {
    }
}
