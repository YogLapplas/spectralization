package io.github.yoglappland.spectralization.block;

import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import io.github.yoglappland.spectralization.blockentity.MachineContentsDropper;
import io.github.yoglappland.spectralization.blockentity.ThermalSmelterBlockEntity;
import io.github.yoglappland.spectralization.compat.ldlib2.ThermalSmelterLdLibUi;
import io.github.yoglappland.spectralization.heat.PhotothermalReceiverBlock;
import io.github.yoglappland.spectralization.menu.ThermalSmelterMenu;
import io.github.yoglappland.spectralization.optics.BeamPacket;
import io.github.yoglappland.spectralization.optics.CompiledOpticalNetwork;
import io.github.yoglappland.spectralization.optics.OpticalReceiver;
import io.github.yoglappland.spectralization.optics.OpticalResult;
import io.github.yoglappland.spectralization.registry.SpectralBlockEntities;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ThermalSmelterBlock extends HorizontalFacingEntityBlock implements OpticalReceiver, PhotothermalReceiverBlock, BlockUIMenuType.BlockUI {
    public static final Direction PHOTOTHERMAL_INPUT_SIDE = Direction.WEST;
    public static final Direction SECONDARY_PHOTOTHERMAL_INPUT_SIDE = Direction.EAST;
    public static final Direction ITEM_PORT_SIDE = Direction.SOUTH;
    public static final Direction RESERVED_FIBER_SIDE = Direction.DOWN;

    private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 16.0);

    public ThermalSmelterBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
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
        if (stack.is(Items.DIAMOND) && level.getBlockEntity(pos) instanceof ThermalSmelterBlockEntity smelter) {
            if (!level.isClientSide) {
                if (smelter.upgradeParallelCount()) {
                    if (!player.getAbilities().instabuild) {
                        stack.shrink(1);
                    }
                    player.displayClientMessage(Component.literal("PAR " + smelter.parallelCount() + "/4"), true);
                } else {
                    player.displayClientMessage(Component.literal("PAR MAX"), true);
                }
            }

            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        openMenu(level, pos, player);
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    private static void openMenu(Level level, BlockPos pos, Player player) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ThermalSmelterBlockEntity smelter) {
            player.openMenu(new SimpleMenuProvider(
                    (containerId, inventory, menuPlayer) -> new ThermalSmelterMenu(containerId, inventory, smelter),
                    Component.translatable("container.spectralization.thermal_smelter")
            ));
        }
    }

    @Override
    public ModularUI createUI(BlockUIMenuType.BlockUIHolder holder) {
        if (holder.player.level().getBlockEntity(holder.pos) instanceof ThermalSmelterBlockEntity smelter) {
            return ThermalSmelterLdLibUi.create(smelter, holder.player);
        }

        return ModularUI.of(com.lowdragmc.lowdraglib2.gui.ui.UI.empty(), holder.player);
    }

    @Override
    public boolean stillValid(BlockUIMenuType.BlockUIHolder holder) {
        return holder.player.distanceToSqr(Vec3.atCenterOf(holder.pos)) <= 64.0
                && holder.player.level().getBlockEntity(holder.pos) instanceof ThermalSmelterBlockEntity
                && holder.player.level().getBlockState(holder.pos).is(this);
    }

    @Override
    public Component getUIDisplayName(BlockUIMenuType.BlockUIHolder holder) {
        return Component.translatable("container.spectralization.thermal_smelter");
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ThermalSmelterBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        if (level.isClientSide || blockEntityType != SpectralBlockEntities.THERMAL_SMELTER.get()) {
            return null;
        }

        return (tickerLevel, pos, tickerState, blockEntity) ->
                ThermalSmelterBlockEntity.tick(tickerLevel, pos, (ThermalSmelterBlockEntity) blockEntity);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        MachineContentsDropper.dropFromBlockEntity(state, level, pos, newState);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public CompiledOpticalNetwork compileOpticalNetwork(BlockState state, Level level, BlockPos pos) {
        return CompiledOpticalNetwork.builder().build();
    }

    @Override
    public OpticalResult receiveBeam(BeamPacket input, Direction incomingDirection, BlockState state, Level level, BlockPos pos) {
        return compileOpticalNetwork(state, level, pos).interact(input, incomingDirection);
    }

    @Override
    public Direction photothermalReceivingSide(BlockState state) {
        return localToWorld(state, PHOTOTHERMAL_INPUT_SIDE);
    }

    @Override
    public Set<Direction> photothermalReceivingSides(BlockState state) {
        return Set.of(localToWorld(state, PHOTOTHERMAL_INPUT_SIDE), localToWorld(state, SECONDARY_PHOTOTHERMAL_INPUT_SIDE));
    }

    public static Direction itemPortSide(BlockState state) {
        return localToWorld(state, ITEM_PORT_SIDE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
