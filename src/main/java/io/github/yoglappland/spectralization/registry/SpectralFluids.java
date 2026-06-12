package io.github.yoglappland.spectralization.registry;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.fluid.MoltenMaterialFluidType;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.pathfinder.PathType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class SpectralFluids {
    private static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, Spectralization.MODID);
    private static final DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(Registries.FLUID, Spectralization.MODID);

    public static final MoltenFluidSet MOLTEN_SILVER =
            register("molten_silver", 0xFFD8D8DC, 1235, 5600, 8);
    public static final MoltenFluidSet MOLTEN_GOLD =
            register("molten_gold", 0xFFFFC33D, 1337, 6200, 10);
    public static final MoltenFluidSet MOLTEN_COPPER =
            register("molten_copper", 0xFFE27B39, 1358, 5700, 9);
    public static final MoltenFluidSet MOLTEN_SILICA =
            register("molten_silica", 0xFFEAF7FF, 1980, 9000, 7);
    public static final MoltenFluidSet MOLTEN_ALUMINA =
            register("molten_alumina", 0xFFDCEEFF, 2320, 9800, 7);
    public static final MoltenFluidSet MOLTEN_TITANIUM_DIOXIDE =
            register("molten_titanium_dioxide", 0xFFC95B33, 2110, 8800, 8);
    public static final MoltenFluidSet MOLTEN_FLUORITE =
            register("molten_fluorite", 0xFFB78CFF, 1680, 7200, 8);
    public static final MoltenFluidSet MOLTEN_YTTRIUM_OXIDE =
            register("molten_yttrium_oxide", 0xFFF0E3AA, 2420, 9800, 7);
    public static final MoltenFluidSet MOLTEN_YAG =
            register("molten_yag", 0xFFD7E878, 2240, 9600, 9);

    public static void register(IEventBus modEventBus) {
        FLUID_TYPES.register(modEventBus);
        FLUIDS.register(modEventBus);
    }

    @SuppressWarnings("unchecked")
    private static MoltenFluidSet register(String name, int tint, int temperature, int viscosity, int lightLevel) {
        DeferredHolder<FluidType, FluidType> type = FLUID_TYPES.register(
                name,
                () -> new MoltenMaterialFluidType(
                        FluidType.Properties.create()
                                .density(3000)
                                .temperature(temperature)
                                .viscosity(viscosity)
                                .lightLevel(lightLevel)
                                .canSwim(false)
                                .canDrown(false)
                                .canPushEntity(false)
                                .pathType(PathType.LAVA)
                                .adjacentPathType(PathType.DANGER_FIRE)
                                .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL_LAVA)
                                .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY_LAVA),
                        tint
                )
        );

        DeferredHolder<Fluid, FlowingFluid>[] still = new DeferredHolder[1];
        DeferredHolder<Fluid, FlowingFluid>[] flowing = new DeferredHolder[1];
        DeferredBlock<LiquidBlock>[] block = new DeferredBlock[1];
        DeferredItem<BucketItem>[] bucket = new DeferredItem[1];

        Supplier<BaseFlowingFluid.Properties> properties = () -> new BaseFlowingFluid.Properties(
                type::get,
                still[0]::get,
                flowing[0]::get
        ).bucket(bucket[0]::get)
                .block(block[0]::get)
                .slopeFindDistance(2)
                .levelDecreasePerBlock(2)
                .tickRate(18)
                .explosionResistance(100.0F);

        still[0] = FLUIDS.register(name, () -> new BaseFlowingFluid.Source(properties.get()));
        flowing[0] = FLUIDS.register(name + "_flowing", () -> new BaseFlowingFluid.Flowing(properties.get()));
        block[0] = Spectralization.BLOCKS.register(
                name,
                () -> new LiquidBlock(
                        still[0].get(),
                        BlockBehaviour.Properties.ofFullCopy(Blocks.LAVA)
                                .mapColor(MapColor.FIRE)
                                .lightLevel(state -> lightLevel)
                                .pushReaction(PushReaction.DESTROY)
                                .sound(SoundType.EMPTY)
                )
        );
        bucket[0] = Spectralization.ITEMS.register(
                name + "_bucket",
                () -> new BucketItem(
                        still[0].get(),
                        new Item.Properties()
                                .craftRemainder(Items.BUCKET)
                                .stacksTo(1)
                )
        );

        return new MoltenFluidSet(name, tint, type, still[0], flowing[0], block[0], bucket[0]);
    }

    public record MoltenFluidSet(
            String name,
            int tint,
            DeferredHolder<FluidType, FluidType> type,
            DeferredHolder<Fluid, FlowingFluid> still,
            DeferredHolder<Fluid, FlowingFluid> flowing,
            DeferredBlock<LiquidBlock> block,
            DeferredItem<BucketItem> bucket
    ) {
    }

    private SpectralFluids() {
    }
}
