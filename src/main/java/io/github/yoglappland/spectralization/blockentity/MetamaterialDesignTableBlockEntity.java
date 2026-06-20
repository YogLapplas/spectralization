package io.github.yoglappland.spectralization.blockentity;

import io.github.yoglappland.spectralization.optics.metasurface.MetamaterialDesignRules;
import io.github.yoglappland.spectralization.optics.metasurface.MetamaterialStandardTemplate;
import io.github.yoglappland.spectralization.optics.metasurface.MetamaterialTemplateData;
import io.github.yoglappland.spectralization.registry.SpectralBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

public class MetamaterialDesignTableBlockEntity extends BlockEntity {
    public static final int MODE_STANDARD = 0;
    public static final int MODE_CUSTOM = 1;

    public static final int SLOT_LEFT_TOP = 0;
    public static final int SLOT_LEFT_MIDDLE = 1;
    public static final int SLOT_LEFT_BOTTOM = 2;
    public static final int SLOT_RIGHT_TOP = 3;
    public static final int SLOT_RIGHT_MIDDLE = 4;
    public static final int SLOT_RIGHT_BOTTOM = 5;
    public static final int SLOT_OUTPUT = 6;
    public static final int SLOT_COUNT = 7;

    public static final int SLOT_X_BUDGET = SLOT_LEFT_TOP;
    public static final int SLOT_Y_BUDGET = SLOT_LEFT_MIDDLE;
    public static final int SLOT_Z_BUDGET = SLOT_LEFT_BOTTOM;

    public static final int DATA_MODE = 0;
    public static final int DATA_STANDARD = 1;
    public static final int DATA_MIN_X = 2;
    public static final int DATA_MAX_X = 3;
    public static final int DATA_MIN_Y = 4;
    public static final int DATA_MAX_Y = 5;
    public static final int DATA_MIN_Z = 6;
    public static final int DATA_MAX_Z = 7;
    public static final int DATA_TARGET_X = 8;
    public static final int DATA_TARGET_Y = 9;
    public static final int DATA_TARGET_Z = 10;
    public static final int DATA_READY = 11;
    public static final int DATA_TARGET_IN_RANGE = 12;
    public static final int DATA_COUNT = 13;

    private static final String ITEMS_TAG = "items";
    private static final String MODE_TAG = "mode";
    private static final String STANDARD_TAG = "standard";

    private int mode = MODE_STANDARD;
    private int standardIndex = 0;

    private final ItemStackHandler items = new ItemStackHandler(SLOT_COUNT) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return switch (slot) {
                case SLOT_LEFT_TOP, SLOT_LEFT_MIDDLE, SLOT_LEFT_BOTTOM,
                        SLOT_RIGHT_TOP, SLOT_RIGHT_MIDDLE, SLOT_RIGHT_BOTTOM -> isDesignMaterial(stack);
                default -> false;
            };
        }

        @Override
        public int getSlotLimit(int slot) {
            return switch (slot) {
                case SLOT_LEFT_TOP, SLOT_LEFT_MIDDLE, SLOT_LEFT_BOTTOM,
                        SLOT_RIGHT_TOP, SLOT_RIGHT_MIDDLE, SLOT_RIGHT_BOTTOM -> 1;
                default -> super.getSlotLimit(slot);
            };
        }

        @Override
        protected void onContentsChanged(int slot) {
            syncChanged();
        }
    };

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return getData(index);
        }

        @Override
        public void set(int index, int value) {
            setData(index, value);
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public MetamaterialDesignTableBlockEntity(BlockPos pos, BlockState blockState) {
        super(SpectralBlockEntities.METAMATERIAL_DESIGN_TABLE.get(), pos, blockState);
    }

    public ItemStackHandler items() {
        return items;
    }

    public ItemStackHandler getItems(Direction side) {
        return items;
    }

    public ItemStack getStackForDisplay(int slot) {
        if (slot < 0 || slot >= items.getSlots()) {
            return ItemStack.EMPTY;
        }

        return items.getStackInSlot(slot);
    }

    public ContainerData createDataAccess() {
        return data;
    }

    public void toggleMode() {
        mode = mode == MODE_STANDARD ? MODE_CUSTOM : MODE_STANDARD;
        setChanged();
    }

    public void adjustStandard(int amount) {
        standardIndex = Math.floorMod(standardIndex + amount, MetamaterialStandardTemplate.values().length);
        setChanged();
    }

    public boolean design() {
        if (level == null || level.isClientSide || !canDesign()) {
            return false;
        }

        MetamaterialDesignRules.DesignEnvelope envelope = currentEnvelope();
        MetamaterialTemplateData template = mode == MODE_STANDARD
                ? MetamaterialTemplateData.standard(selectedStandard())
                : MetamaterialTemplateData.custom(envelope.random(level.random));

        consumeBudget();
        items.setStackInSlot(SLOT_OUTPUT, template.createStack());
        setChanged();
        return true;
    }

    public void dropContents(Level level, BlockPos pos) {
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            ItemStack stack = items.getStackInSlot(slot);

            if (!stack.isEmpty()) {
                Block.popResource(level, pos, stack);
            }
        }
    }

    public static boolean isDesignMaterial(ItemStack stack) {
        return MetamaterialDesignRules.isDesignMaterial(stack);
    }

    private void setData(int index, int value) {
        switch (index) {
            case DATA_MODE -> {
                mode = value == MODE_CUSTOM ? MODE_CUSTOM : MODE_STANDARD;
                setChanged();
            }
            case DATA_STANDARD -> {
                standardIndex = Math.floorMod(value, MetamaterialStandardTemplate.values().length);
                setChanged();
            }
            default -> {
            }
        }
    }

    private int getData(int index) {
        MetamaterialDesignRules.DesignEnvelope envelope = currentEnvelope();
        MetamaterialStandardTemplate standard = selectedStandard();

        return switch (index) {
            case DATA_MODE -> mode;
            case DATA_STANDARD -> Math.floorMod(standardIndex, MetamaterialStandardTemplate.values().length);
            case DATA_MIN_X -> envelope.x().min();
            case DATA_MAX_X -> envelope.x().max();
            case DATA_MIN_Y -> envelope.y().min();
            case DATA_MAX_Y -> envelope.y().max();
            case DATA_MIN_Z -> envelope.z().min();
            case DATA_MAX_Z -> envelope.z().max();
            case DATA_TARGET_X -> standard.vector().x();
            case DATA_TARGET_Y -> standard.vector().y();
            case DATA_TARGET_Z -> standard.vector().z();
            case DATA_READY -> canDesign() ? 1 : 0;
            case DATA_TARGET_IN_RANGE -> targetInRange() ? 1 : 0;
            default -> 0;
        };
    }

    private boolean canDesign() {
        if (!items.getStackInSlot(SLOT_OUTPUT).isEmpty()) {
            return false;
        }

        MetamaterialDesignRules.DesignEnvelope envelope = currentEnvelope();
        if (!envelope.complete()) {
            return false;
        }

        return mode == MODE_CUSTOM || envelope.contains(selectedStandard().vector());
    }

    private boolean targetInRange() {
        MetamaterialDesignRules.DesignEnvelope envelope = currentEnvelope();
        return envelope.complete()
                && (mode == MODE_CUSTOM || envelope.contains(selectedStandard().vector()));
    }

    private MetamaterialStandardTemplate selectedStandard() {
        return MetamaterialStandardTemplate.byIndex(standardIndex);
    }

    private MetamaterialDesignRules.DesignEnvelope currentEnvelope() {
        return MetamaterialDesignRules.envelope(
                items.getStackInSlot(SLOT_X_BUDGET),
                items.getStackInSlot(SLOT_Y_BUDGET),
                items.getStackInSlot(SLOT_Z_BUDGET)
        );
    }

    private void consumeBudget() {
        items.extractItem(SLOT_X_BUDGET, 1, false);
        items.extractItem(SLOT_Y_BUDGET, 1, false);
        items.extractItem(SLOT_Z_BUDGET, 1, false);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        mode = tag.getInt(MODE_TAG) == MODE_CUSTOM ? MODE_CUSTOM : MODE_STANDARD;
        standardIndex = Mth.clamp(tag.getInt(STANDARD_TAG), 0, MetamaterialStandardTemplate.values().length - 1);

        if (tag.contains(ITEMS_TAG)) {
            items.deserializeNBT(registries, tag.getCompound(ITEMS_TAG));
            normalizeItemSlots();
        }
    }

    private void normalizeItemSlots() {
        if (items.getSlots() == SLOT_COUNT) {
            return;
        }

        ItemStack[] savedStacks = new ItemStack[Math.min(items.getSlots(), SLOT_COUNT)];

        for (int slot = 0; slot < savedStacks.length; slot++) {
            savedStacks[slot] = items.getStackInSlot(slot).copy();
        }

        items.setSize(SLOT_COUNT);

        for (int slot = 0; slot < savedStacks.length; slot++) {
            items.setStackInSlot(slot, savedStacks[slot]);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(ITEMS_TAG, items.serializeNBT(registries));
        tag.putInt(MODE_TAG, mode);
        tag.putInt(STANDARD_TAG, standardIndex);
    }

    private void syncChanged() {
        setChanged();

        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }
}
