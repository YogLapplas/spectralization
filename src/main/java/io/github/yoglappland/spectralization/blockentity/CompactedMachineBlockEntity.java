package io.github.yoglappland.spectralization.blockentity;

import io.github.yoglappland.spectralization.compact.CompactedMachineItemData;
import io.github.yoglappland.spectralization.optics.OutputBeam;
import io.github.yoglappland.spectralization.optics.cache.OpticalDirtyKind;
import io.github.yoglappland.spectralization.optics.cache.OpticalTraceCache;
import io.github.yoglappland.spectralization.registry.SpectralBlockEntities;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class CompactedMachineBlockEntity extends BlockEntity {
    private static final String DATA_TAG = "CompactedMachineData";

    private CompoundTag compactedData = new CompoundTag();

    public CompactedMachineBlockEntity(BlockPos pos, BlockState blockState) {
        super(SpectralBlockEntities.COMPACTED_MACHINE.get(), pos, blockState);
    }

    public void setCompactedData(CompoundTag compactedData) {
        this.compactedData = compactedData.copy();
        syncChanged();
    }

    public CompoundTag compactedData() {
        return compactedData.copy();
    }

    public Set<Direction> ioFaces() {
        return CompactedMachineItemData.ioFaces(compactedData);
    }

    public List<CompactedMachineItemData.Transfer> transfers() {
        return CompactedMachineItemData.transfers(compactedData);
    }

    public List<OutputBeam> sourceOutputs() {
        return CompactedMachineItemData.sources(compactedData);
    }

    private void syncChanged() {
        setChanged();

        if (level != null) {
            if (!level.isClientSide) {
                OpticalTraceCache.rememberSourceState(level, worldPosition);
                OpticalTraceCache.markChanged(level, worldPosition, OpticalDirtyKind.DATA);
                OpticalTraceCache.requestIntrinsicSourcesNear(level, worldPosition);
            }

            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        compactedData = tag.getCompound(DATA_TAG).copy();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(DATA_TAG, compactedData.copy());
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
