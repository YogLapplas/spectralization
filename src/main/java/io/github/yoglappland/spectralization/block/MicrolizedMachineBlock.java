package io.github.yoglappland.spectralization.block;

import io.github.yoglappland.spectralization.blockentity.MicrolizedMachineBlockEntity;
import io.github.yoglappland.spectralization.microlizer.MicrolizedMachineItemData;
import io.github.yoglappland.spectralization.microlizer.MicrolizedMachineTransform;
import io.github.yoglappland.spectralization.menu.MicrolizedMachineMenu;
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
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class MicrolizedMachineBlock extends Block implements EntityBlock, OpticalElement, OpticalTopologyProvider, OpticalSource {
    public MicrolizedMachineBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MicrolizedMachineBlockEntity(pos, state);
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

        if (!level.isClientSide && level.getBlockEntity(pos) instanceof MicrolizedMachineBlockEntity microlizedMachine) {
            microlizedMachine.setMicrolizedData(MicrolizedMachineItemData.copyRoot(stack));
        }
    }

    @Override
    public CompiledOpticalNetwork compileOpticalNetwork(BlockState state, Level level, BlockPos pos) {
        List<MicrolizedMachineItemData.Transfer> transfers = transfers(level, pos);
        if (transfers.isEmpty()) {
            return CompiledOpticalNetwork.builder().build();
        }

        CompiledOpticalNetwork.Builder builder = CompiledOpticalNetwork.builder();
        Direction facing = facing(level, pos);

        for (MicrolizedMachineItemData.Transfer transfer : transfers) {
            builder.addRule(
                    MicrolizedMachineTransform.localToWorld(transfer.fromFace(), facing),
                    MicrolizedMachineTransform.localToWorld(transfer.toFace(), facing),
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
        Direction facing = facing(level, pos);
        Direction localIncoming = MicrolizedMachineTransform.worldToLocal(incomingDirection, facing);

        for (MicrolizedMachineItemData.Transfer transfer : transfers(level, pos)) {
            if (transfer.fromFace() == localIncoming) {
                outgoingDirections.add(MicrolizedMachineTransform.localToWorld(transfer.toFace(), facing));
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
        if (level.getBlockEntity(pos) instanceof MicrolizedMachineBlockEntity microlizedMachine) {
            Direction facing = microlizedMachine.facing();
            return microlizedMachine.sourceOutputs().stream()
                    .map(output -> rotateOutput(output, facing))
                    .toList();
        }

        return List.of();
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hitResult
    ) {
        openMenu(level, pos, player);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected ItemInteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hitResult
    ) {
        openMenu(level, pos, player);
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        if (level.getBlockEntity(pos) instanceof MicrolizedMachineBlockEntity microlizedMachine) {
            return MicrolizedMachineItemData.createStackFromRoot(microlizedMachine.microlizedData());
        }

        return super.getCloneItemStack(level, pos, state);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide
                && !player.getAbilities().instabuild
                && player.hasCorrectToolForDrops(state, level, pos)
                && level.getBlockEntity(pos) instanceof MicrolizedMachineBlockEntity microlizedMachine) {
            popResource(level, pos, MicrolizedMachineItemData.createStackFromRoot(microlizedMachine.microlizedData()));
        }

        return super.playerWillDestroy(level, pos, state, player);
    }

    private static List<MicrolizedMachineItemData.Transfer> transfers(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof MicrolizedMachineBlockEntity microlizedMachine) {
            return microlizedMachine.transfers();
        }

        return List.of();
    }

    private static Direction facing(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof MicrolizedMachineBlockEntity microlizedMachine) {
            return microlizedMachine.facing();
        }

        return Direction.NORTH;
    }

    private static OutputBeam rotateOutput(OutputBeam output, Direction facing) {
        Direction worldDirection = MicrolizedMachineTransform.localToWorld(output.outgoingDirection(), facing);
        return new OutputBeam(worldDirection, output.beam().withDirection(worldDirection));
    }

    private static void openMenu(Level level, BlockPos pos, Player player) {
        if (level instanceof ServerLevel serverLevel) {
            BlockPos machinePos = pos.immutable();
            player.openMenu(new SimpleMenuProvider(
                    (containerId, inventory, menuPlayer) ->
                            new MicrolizedMachineMenu(containerId, inventory, serverLevel, machinePos),
                    Component.translatable("container.spectralization.microlized_machine")
            ), buffer -> MicrolizedMachineMenu.writeSnapshot(buffer, serverLevel, machinePos));
        }
    }
}
