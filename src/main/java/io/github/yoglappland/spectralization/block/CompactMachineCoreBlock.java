package io.github.yoglappland.spectralization.block;

import io.github.yoglappland.spectralization.compact.CompactMachineNetworkData;
import io.github.yoglappland.spectralization.compact.CompactMachinePartKind;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class CompactMachineCoreBlock extends CompactMachinePartBlock {
    public CompactMachineCoreBlock(BlockBehaviour.Properties properties) {
        super(properties, CompactMachinePartKind.CORE);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hitResult
    ) {
        if (!level.isClientSide) {
            int connections = CompactMachineNetworkData.connections(level).stream()
                    .filter(connection -> connection.touches(pos))
                    .toList()
                    .size();
            player.displayClientMessage(Component.translatable(
                    state.getValue(ERROR)
                            ? "block.spectralization.compact_machine_core.message.error"
                            : "block.spectralization.compact_machine_core.message.ready",
                    connections
            ), true);
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
