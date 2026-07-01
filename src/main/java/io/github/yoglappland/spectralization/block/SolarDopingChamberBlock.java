package io.github.yoglappland.spectralization.block;

import io.github.yoglappland.spectralization.blockentity.MachineContentsDropper;
import io.github.yoglappland.spectralization.blockentity.SolarDopingChamberBlockEntity;
import io.github.yoglappland.spectralization.diagnostics.SpectralDiagnostics;
import io.github.yoglappland.spectralization.menu.SolarDopingChamberMenu;
import io.github.yoglappland.spectralization.registry.SpectralBlockEntities;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class SolarDopingChamberBlock extends HorizontalFacingEntityBlock {
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    private static final VoxelShape BASE = Block.box(0.0, 0.0, 0.0, 16.0, 7.0, 16.0);
    private static final VoxelShape TOP = Block.box(0.0, 14.0, 0.0, 16.0, 16.0, 16.0);
    private static final VoxelShape POST_NW = Block.box(0.0, 6.0, 0.0, 1.0, 15.0, 1.0);
    private static final VoxelShape POST_NE = Block.box(15.0, 6.0, 0.0, 16.0, 15.0, 1.0);
    private static final VoxelShape POST_SW = Block.box(0.0, 6.0, 15.0, 1.0, 15.0, 16.0);
    private static final VoxelShape POST_SE = Block.box(15.0, 6.0, 15.0, 16.0, 15.0, 16.0);
    private static final VoxelShape SHAPE = Shapes.or(BASE, TOP, POST_NW, POST_NE, POST_SW, POST_SE);

    private final String subsystem;
    private final String containerKey;

    public SolarDopingChamberBlock(Properties properties) {
        this(properties, "solar_doping", "container.spectralization.solar_doping_chamber");
    }

    public SolarDopingChamberBlock(Properties properties, String subsystem, String containerKey) {
        super(properties);
        this.subsystem = subsystem;
        this.containerKey = containerKey;
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(ACTIVE, false));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SolarDopingChamberBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        if (level.isClientSide || blockEntityType != SpectralBlockEntities.SOLAR_DOPING_CHAMBER.get()) {
            return null;
        }

        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof SolarDopingChamberBlockEntity chamber) {
                SolarDopingChamberBlockEntity.tick(tickLevel, pos, chamber);
            }
        };
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof SolarDopingChamberBlockEntity chamber) {
            SpectralDiagnostics.event(level, subsystem, "menu_opened")
                    .pos("machine", pos)
                    .field("player", player.getScoreboardName())
                    .write();
            player.openMenu(new SimpleMenuProvider(
                    (containerId, inventory, menuPlayer) -> new SolarDopingChamberMenu(containerId, inventory, chamber),
                    Component.translatable(containerKey)
            ));
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof SolarDopingChamberBlockEntity chamber) {
            SpectralDiagnostics.event(level, subsystem, "machine_removed")
                    .pos("machine", pos)
                    .field("replacement", newState.getBlock())
                    .write();
        }

        MachineContentsDropper.dropFromBlockEntity(state, level, pos, newState);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ACTIVE);
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
