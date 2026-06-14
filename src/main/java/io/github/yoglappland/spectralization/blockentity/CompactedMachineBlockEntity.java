package io.github.yoglappland.spectralization.blockentity;

import io.github.yoglappland.spectralization.compact.CompactedMachineItemData;
import io.github.yoglappland.spectralization.compact.CompactedMachineFaceColor;
import io.github.yoglappland.spectralization.compact.CompactedMachineTransform;
import io.github.yoglappland.spectralization.optics.OutputBeam;
import io.github.yoglappland.spectralization.optics.cache.OpticalDirtyKind;
import io.github.yoglappland.spectralization.optics.cache.OpticalTraceCache;
import io.github.yoglappland.spectralization.optics.topology.OpticalNetworkIndex;
import io.github.yoglappland.spectralization.registry.SpectralBlockEntities;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
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
        syncOpticalChanged(OpticalDirtyKind.DATA);
    }

    public CompoundTag compactedData() {
        return compactedData.copy();
    }

    public Direction facing() {
        return CompactedMachineItemData.facing(compactedData);
    }

    public void rotateHorizontal(boolean clockwise) {
        Direction oldFacing = facing();
        Direction newFacing = CompactedMachineTransform.rotateFacing(oldFacing, clockwise);
        if (oldFacing == newFacing) {
            return;
        }

        CompactedMachineItemData.setFacing(compactedData, newFacing);
        syncOpticalChanged(OpticalDirtyKind.TOPOLOGY);
    }

    public CompactedMachineFaceColor faceColor(Direction face) {
        return CompactedMachineItemData.faceColor(compactedData, face);
    }

    public void cycleFaceColor(Direction face) {
        CompactedMachineFaceColor oldColor = faceColor(face);
        CompactedMachineItemData.setFaceColor(compactedData, face, oldColor.next());
        syncVisualChanged();
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

    private void syncVisualChanged() {
        setChanged();
        sendVisualBlockUpdate();
    }

    private void syncOpticalChanged(OpticalDirtyKind opticalDirtyKind) {
        setChanged();

        if (level != null) {
            if (!level.isClientSide) {
                OpticalTraceCache.rememberSourceState(level, worldPosition);
                OpticalTraceCache.markChanged(level, worldPosition, opticalDirtyKind);
                if (opticalDirtyKind == OpticalDirtyKind.TOPOLOGY || opticalDirtyKind == OpticalDirtyKind.STRUCTURE) {
                    OpticalTraceCache.invalidateTopologyCaches(level);
                    OpticalNetworkIndex.markDirty(level);
                }
                OpticalTraceCache.requestIntrinsicSourceAt(level, worldPosition);
                OpticalTraceCache.requestIntrinsicSourcesNear(level, worldPosition);
            }

            sendVisualBlockUpdate();
        }
    }

    private void sendVisualBlockUpdate() {
        if (level == null) {
            return;
        }

        if (level.isClientSide) {
            requestVisualModelRefresh();
        } else {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        compactedData = tag.getCompound(DATA_TAG).copy();
        requestVisualModelRefresh();
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

    @Override
    public void onDataPacket(
            Connection net,
            ClientboundBlockEntityDataPacket pkt,
            HolderLookup.Provider lookupProvider
    ) {
        super.onDataPacket(net, pkt, lookupProvider);
        requestVisualModelRefresh();
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider lookupProvider) {
        super.handleUpdateTag(tag, lookupProvider);
        requestVisualModelRefresh();
    }

    private void requestVisualModelRefresh() {
        if (level != null && level.isClientSide) {
            requestModelDataUpdate();
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }
}
