package io.github.yoglappland.spectralization.block;

import com.mojang.serialization.MapCodec;
import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.blockentity.MachineContentsDropper;
import io.github.yoglappland.spectralization.blockentity.RecursiveGeneratorBlockEntity;
import io.github.yoglappland.spectralization.menu.RecursiveGeneratorMenu;
import io.github.yoglappland.spectralization.registry.SpectralBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class RecursiveGeneratorBlock extends BaseEntityBlock {
    public static final MapCodec<RecursiveGeneratorBlock> CODEC = simpleCodec(RecursiveGeneratorBlock::new);
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    private static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);

    public RecursiveGeneratorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(ACTIVE, Boolean.FALSE));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(ACTIVE, Boolean.FALSE);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RecursiveGeneratorBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof RecursiveGeneratorBlockEntity generator) {
            openMenu(player, generator);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof RecursiveGeneratorBlockEntity generator) {
            openMenu(player, generator);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        if (level.isClientSide || blockEntityType != SpectralBlockEntities.RECURSIVE_GENERATOR.get()) {
            return null;
        }

        return (tickerLevel, pos, tickerState, blockEntity) ->
                RecursiveGeneratorBlockEntity.tick(tickerLevel, pos, tickerState, (RecursiveGeneratorBlockEntity) blockEntity);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof RecursiveGeneratorBlockEntity generator) {
            generator.loadFromStack(stack, level.registryAccess());
            generator.updateActiveBlockState();
        }
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, net.minecraft.world.phys.HitResult target, LevelReader level, BlockPos pos, net.minecraft.world.entity.player.Player player) {
        ItemStack stack = new ItemStack(Spectralization.RECURSIVE_GENERATOR_ITEM.get());
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof RecursiveGeneratorBlockEntity generator && level instanceof Level concreteLevel) {
            generator.saveToStack(stack, concreteLevel.registryAccess());
        }
        return stack;
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        BlockEntity blockEntity = params.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY);
        ItemStack stack = new ItemStack(Spectralization.RECURSIVE_GENERATOR_ITEM.get());
        if (blockEntity instanceof RecursiveGeneratorBlockEntity generator) {
            generator.saveToStack(stack, params.getLevel().registryAccess());
        }
        return Collections.singletonList(stack);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            MachineContentsDropper.dropFromBlockEntity(state, level, pos, newState);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    private static void openMenu(Player player, RecursiveGeneratorBlockEntity generator) {
        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, menuPlayer) -> new RecursiveGeneratorMenu(containerId, inventory, generator),
                Component.translatable("container.spectralization.recursive_generator")
        ));
    }
}
