package io.github.yoglappland.spectralization.client.model;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.blockentity.MicrolizedMachineBlockEntity;
import io.github.yoglappland.spectralization.microlizer.MicrolizedMachineFaceColor;
import io.github.yoglappland.spectralization.microlizer.MicrolizedMachineTransform;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.Nullable;

public final class MicrolizedMachineDynamicModel extends BakedModelWrapper<BakedModel> {
    private static final ModelProperty<VisualState> VISUAL_STATE = new ModelProperty<>();
    private static final EnumMap<MicrolizedMachineFaceColor, ModelResourceLocation> COLOR_MODEL_LOCATIONS =
            new EnumMap<>(MicrolizedMachineFaceColor.class);

    static {
        for (MicrolizedMachineFaceColor color : MicrolizedMachineFaceColor.values()) {
            COLOR_MODEL_LOCATIONS.put(
                    color,
                    ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(
                            Spectralization.MODID,
                            "block/microlized_machine_" + color.serializedName()
                    ))
            );
        }
    }

    private final EnumMap<MicrolizedMachineFaceColor, BakedModel> colorModels;

    private MicrolizedMachineDynamicModel(
            BakedModel originalModel,
            EnumMap<MicrolizedMachineFaceColor, BakedModel> colorModels
    ) {
        super(originalModel);
        this.colorModels = colorModels;
    }

    public static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
        for (ModelResourceLocation location : COLOR_MODEL_LOCATIONS.values()) {
            event.register(location);
        }
    }

    public static void modifyBakingResult(ModelEvent.ModifyBakingResult event) {
        ModelResourceLocation microlizedMachineLocation = BlockModelShaper.stateToModelLocation(
                Spectralization.MICROLIZED_MACHINE.get().defaultBlockState()
        );
        BakedModel originalModel = event.getModels().get(microlizedMachineLocation);

        if (originalModel == null) {
            Spectralization.LOGGER.warn(
                    "Could not wrap microlized machine model: missing {}",
                    microlizedMachineLocation
            );
            return;
        }

        EnumMap<MicrolizedMachineFaceColor, BakedModel> colorModels = new EnumMap<>(MicrolizedMachineFaceColor.class);
        for (Map.Entry<MicrolizedMachineFaceColor, ModelResourceLocation> entry : COLOR_MODEL_LOCATIONS.entrySet()) {
            BakedModel colorModel = event.getModels().get(entry.getValue());

            if (colorModel == null) {
                Spectralization.LOGGER.warn(
                        "Missing microlized machine color model {}, falling back to base model",
                        entry.getValue()
                );
                colorModel = originalModel;
            }

            colorModels.put(entry.getKey(), colorModel);
        }

        event.getModels().put(microlizedMachineLocation, new MicrolizedMachineDynamicModel(originalModel, colorModels));
    }

    @Override
    public List<BakedQuad> getQuads(
            @Nullable BlockState state,
            @Nullable Direction side,
            RandomSource rand,
            ModelData extraData,
            @Nullable RenderType renderType
    ) {
        VisualState visualState = extraData.get(VISUAL_STATE);

        if (state == null || visualState == null) {
            return originalModel.getQuads(state, side, rand, extraData, renderType);
        }

        if (side == null) {
            return List.of();
        }

        Direction localFace = MicrolizedMachineTransform.worldToLocal(side, visualState.facing());
        MicrolizedMachineFaceColor color = visualState.faceColor(localFace);
        BakedModel colorModel = colorModels.getOrDefault(color, originalModel);

        return colorModel.getQuads(state, side, rand, ModelData.EMPTY, renderType);
    }

    @Override
    public ModelData getModelData(
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState state,
            ModelData modelData
    ) {
        if (!(level.getBlockEntity(pos) instanceof MicrolizedMachineBlockEntity microlizedMachine)) {
            return originalModel.getModelData(level, pos, state, modelData);
        }

        ModelData baseData = originalModel.getModelData(level, pos, state, modelData);
        return baseData.derive()
                .with(VISUAL_STATE, VisualState.from(microlizedMachine))
                .build();
    }

    private record VisualState(Direction facing, EnumMap<Direction, MicrolizedMachineFaceColor> faceColors) {
        static VisualState from(MicrolizedMachineBlockEntity microlizedMachine) {
            EnumMap<Direction, MicrolizedMachineFaceColor> colors = new EnumMap<>(Direction.class);

            for (Direction face : Direction.values()) {
                colors.put(face, microlizedMachine.faceColor(face));
            }

            return new VisualState(microlizedMachine.facing(), colors);
        }

        MicrolizedMachineFaceColor faceColor(Direction localFace) {
            return faceColors.getOrDefault(localFace, MicrolizedMachineFaceColor.CYAN);
        }
    }
}
