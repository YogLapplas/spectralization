package io.github.yoglappland.spectralization.blockentity;

import io.github.yoglappland.spectralization.optics.cache.OpticalDirtyKind;
import io.github.yoglappland.spectralization.optics.cache.OpticalTraceCache;
import io.github.yoglappland.spectralization.registry.SpectralBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class LensHolderBlockEntity extends BlockEntity {
    private static final String LENS_TAG = "Lens";

    private ItemStack lens = ItemStack.EMPTY;

    public LensHolderBlockEntity(BlockPos pos, BlockState blockState) {
        super(SpectralBlockEntities.LENS_HOLDER.get(), pos, blockState);
    }

    public ItemStack getLens() {
        return this.lens;
    }

    public boolean hasLens() {
        return !this.lens.isEmpty();
    }

    public void setLens(ItemStack lens) {
        this.lens = lens.copyWithCount(1);
        this.syncChanged();
    }

    public ItemStack removeLens() {
        ItemStack removedLens = this.lens;
        this.lens = ItemStack.EMPTY;
        this.syncChanged();
        return removedLens;
    }

    private void syncChanged() {
        this.setChanged();

        if (this.level != null) {
            if (!this.level.isClientSide) {
                OpticalTraceCache.markChanged(this.level, this.worldPosition, OpticalDirtyKind.STRUCTURE);
            }

            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.lens = ItemStack.parseOptional(registries, tag.getCompound(LENS_TAG));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(LENS_TAG, this.lens.saveOptional(registries));
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        this.saveAdditional(tag, registries);
        return tag;
    }
}
