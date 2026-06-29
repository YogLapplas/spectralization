package io.github.yoglappland.spectralization.integration.jade;

import io.github.yoglappland.spectralization.block.MicrolizerCoreBlock;
import io.github.yoglappland.spectralization.microlizer.MicrolizerFrameInfo;
import io.github.yoglappland.spectralization.microlizer.MicrolizerNetworkData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public class MicrolizerCoreComponentProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    public static final MicrolizerCoreComponentProvider INSTANCE = new MicrolizerCoreComponentProvider();

    private static final String PRESENT_KEY = "spectralization_microlizer_present";
    private static final String VALID_KEY = "spectralization_microlizer_valid";
    private static final String SIZE_X_KEY = "spectralization_microlizer_size_x";
    private static final String SIZE_Y_KEY = "spectralization_microlizer_size_y";
    private static final String SIZE_Z_KEY = "spectralization_microlizer_size_z";
    private static final String WORK_SIZE_X_KEY = "spectralization_microlizer_work_size_x";
    private static final String WORK_SIZE_Y_KEY = "spectralization_microlizer_work_size_y";
    private static final String WORK_SIZE_Z_KEY = "spectralization_microlizer_work_size_z";
    private static final String MICROLIZER_BLOCKS_KEY = "spectralization_microlizer_tagged_blocks";
    private static final String MICROLIZER_TYPES_KEY = "spectralization_microlizer_tagged_types";
    private static final String PAYLOAD_BLOCKS_KEY = "spectralization_microlizer_payload_blocks";
    private static final String PAYLOAD_TYPES_KEY = "spectralization_microlizer_payload_types";
    private static final String IO_PORTS_KEY = "spectralization_microlizer_io_ports";

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getLevel() instanceof ServerLevel level)
                || !(accessor.getBlock() instanceof MicrolizerCoreBlock)) {
            return;
        }

        MicrolizerFrameInfo info = MicrolizerNetworkData.frameInfoAt(level, accessor.getPosition());
        data.putBoolean(PRESENT_KEY, info.present());
        data.putBoolean(VALID_KEY, info.valid());
        data.putInt(SIZE_X_KEY, info.sizeX());
        data.putInt(SIZE_Y_KEY, info.sizeY());
        data.putInt(SIZE_Z_KEY, info.sizeZ());
        data.putInt(WORK_SIZE_X_KEY, info.workSizeX());
        data.putInt(WORK_SIZE_Y_KEY, info.workSizeY());
        data.putInt(WORK_SIZE_Z_KEY, info.workSizeZ());
        data.putInt(MICROLIZER_BLOCKS_KEY, info.microlizerBlockCount());
        data.putInt(MICROLIZER_TYPES_KEY, info.microlizerTypeCount());
        data.putInt(PAYLOAD_BLOCKS_KEY, info.payloadBlockCount());
        data.putInt(PAYLOAD_TYPES_KEY, info.payloadTypeCount());
        data.putInt(IO_PORTS_KEY, info.ioPortCount());
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (!data.contains(PRESENT_KEY)) {
            return;
        }

        if (!data.getBoolean(PRESENT_KEY)) {
            tooltip.add(Component.translatable("jade.spectralization.microlizer_core.missing"));
            return;
        }

        tooltip.add(Component.translatable(
                "jade.spectralization.microlizer_core.frame",
                status(data.getBoolean(VALID_KEY)),
                data.getInt(SIZE_X_KEY),
                data.getInt(SIZE_Y_KEY),
                data.getInt(SIZE_Z_KEY),
                data.getInt(IO_PORTS_KEY)
        ));
        tooltip.add(Component.translatable(
                "jade.spectralization.microlizer_core.work_area",
                data.getInt(WORK_SIZE_X_KEY),
                data.getInt(WORK_SIZE_Y_KEY),
                data.getInt(WORK_SIZE_Z_KEY)
        ));
        tooltip.add(Component.translatable(
                "jade.spectralization.microlizer_core.microlizer_parts",
                data.getInt(MICROLIZER_BLOCKS_KEY),
                data.getInt(MICROLIZER_TYPES_KEY)
        ));
        tooltip.add(Component.translatable(
                "jade.spectralization.microlizer_core.contents",
                data.getInt(PAYLOAD_BLOCKS_KEY),
                data.getInt(PAYLOAD_TYPES_KEY)
        ));
    }

    @Override
    public ResourceLocation getUid() {
        return SpectralJadePlugin.MICROLIZER_CORE;
    }

    private static Component status(boolean valid) {
        return Component.translatable("jade.spectralization.microlizer_core.status." + (valid ? "valid" : "error"));
    }
}
