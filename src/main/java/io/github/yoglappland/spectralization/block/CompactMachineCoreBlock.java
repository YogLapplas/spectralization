package io.github.yoglappland.spectralization.block;

import io.github.yoglappland.spectralization.compact.CompactMachinePartKind;
import io.github.yoglappland.spectralization.menu.CompactMachineCoreMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleMenuProvider;
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
        if (level instanceof ServerLevel serverLevel) {
            BlockPos corePos = pos.immutable();
            player.openMenu(new SimpleMenuProvider(
                    (containerId, inventory, menuPlayer) ->
                            new CompactMachineCoreMenu(containerId, inventory, serverLevel, corePos),
                    Component.translatable("container.spectralization.compact_machine_core")
            ));
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
