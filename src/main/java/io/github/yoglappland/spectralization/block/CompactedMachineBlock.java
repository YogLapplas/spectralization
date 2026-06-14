package io.github.yoglappland.spectralization.block;

import io.github.yoglappland.spectralization.blockentity.CompactedMachineBlockEntity;
import io.github.yoglappland.spectralization.compact.CompactedMachineItemData;
import io.github.yoglappland.spectralization.optics.BeamPacket;
import io.github.yoglappland.spectralization.optics.CompiledOpticalNetwork;
import io.github.yoglappland.spectralization.optics.OpticalElement;
import io.github.yoglappland.spectralization.optics.OpticalResult;
import io.github.yoglappland.spectralization.optics.OpticalSource;
import io.github.yoglappland.spectralization.optics.OutputBeam;
import io.github.yoglappland.spectralization.optics.topology.OpticalTopologyProvider;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public class CompactedMachineBlock extends Block implements EntityBlock, OpticalElement, OpticalTopologyProvider, OpticalSource {
    public CompactedMachineBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CompactedMachineBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(
            Level level,
            BlockPos pos,
            BlockState state,
            @Nullable LivingEntity placer,
            ItemStack stack
    ) {
        super.setPlacedBy(level, pos, state, placer, stack);

        if (!level.isClientSide && level.getBlockEntity(pos) instanceof CompactedMachineBlockEntity compactedMachine) {
            compactedMachine.setCompactedData(CompactedMachineItemData.copyRoot(stack));
        }
    }

    @Override
    public CompiledOpticalNetwork compileOpticalNetwork(BlockState state, Level level, BlockPos pos) {
        List<CompactedMachineItemData.Transfer> transfers = transfers(level, pos);
        if (transfers.isEmpty()) {
            return CompiledOpticalNetwork.builder().build();
        }

        CompiledOpticalNetwork.Builder builder = CompiledOpticalNetwork.builder();

        for (CompactedMachineItemData.Transfer transfer : transfers) {
            builder.addRule(
                    transfer.fromFace(),
                    transfer.toFace(),
                    CompiledOpticalNetwork.scale(transfer.gain())
            );
        }

        return builder.build();
    }

    @Override
    public Set<Direction> potentialOutgoingDirections(
            BlockState state,
            Level level,
            BlockPos pos,
            Direction incomingDirection
    ) {
        Set<Direction> outgoingDirections = new HashSet<>();

        for (CompactedMachineItemData.Transfer transfer : transfers(level, pos)) {
            if (transfer.fromFace() == incomingDirection) {
                outgoingDirections.add(transfer.toFace());
            }
        }

        return outgoingDirections.isEmpty() ? Set.of() : Set.copyOf(outgoingDirections);
    }

    @Override
    public OpticalResult interact(
            BeamPacket input,
            Direction incomingDirection,
            BlockState state,
            Level level,
            BlockPos pos
    ) {
        return compileOpticalNetwork(state, level, pos).interact(input, incomingDirection);
    }

    @Override
    public List<OutputBeam> getOutputBeams(BlockState state, Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof CompactedMachineBlockEntity compactedMachine) {
            return compactedMachine.sourceOutputs();
        }

        return List.of();
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    private static List<CompactedMachineItemData.Transfer> transfers(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof CompactedMachineBlockEntity compactedMachine) {
            return compactedMachine.transfers();
        }

        return List.of();
    }
}
