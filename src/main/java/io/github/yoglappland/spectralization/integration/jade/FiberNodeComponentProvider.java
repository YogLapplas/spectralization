package io.github.yoglappland.spectralization.integration.jade;

import io.github.yoglappland.spectralization.optics.fiber.FiberNetworkIndex;
import io.github.yoglappland.spectralization.optics.fiber.FiberNetworkSnapshot;
import io.github.yoglappland.spectralization.optics.fiber.FiberNode;
import io.github.yoglappland.spectralization.optics.fiber.FiberNodeBlock;
import java.util.Locale;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public class FiberNodeComponentProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    public static final FiberNodeComponentProvider INSTANCE = new FiberNodeComponentProvider();
    private static final String USAGE_KEY = "spectralization_fiber_usage";
    private static final String CAPACITY_KEY = "spectralization_fiber_capacity";
    private static final String CORE_RADIUS_KEY = "spectralization_fiber_core_radius";
    private static final String NUMERICAL_APERTURE_KEY = "spectralization_fiber_numerical_aperture";
    private static final String TRANSMISSION_KEY = "spectralization_fiber_transmission";

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getLevel() instanceof ServerLevel level)
                || !(accessor.getBlock() instanceof FiberNodeBlock fiberNodeBlock)) {
            return;
        }

        FiberNetworkSnapshot snapshot = FiberNetworkIndex.snapshot(level);
        FiberNode node = snapshot.nodeAt(accessor.getPosition())
                .orElseGet(() -> new FiberNode(
                        accessor.getPosition(),
                        fiberNodeBlock.fiberNodeKind(accessor.getBlockState(), level, accessor.getPosition()),
                        fiberNodeBlock.fiberNodeProfile(accessor.getBlockState(), level, accessor.getPosition())
                ));

        data.putInt(USAGE_KEY, snapshot.nodeUsage(accessor.getPosition()));
        data.putInt(CAPACITY_KEY, node.profile().maxConnections());
        data.putDouble(CORE_RADIUS_KEY, node.profile().coreRadius());
        data.putDouble(NUMERICAL_APERTURE_KEY, node.profile().numericalAperture());
        data.putDouble(TRANSMISSION_KEY, node.profile().transmissionPerBlock());
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();

        if (!data.contains(CAPACITY_KEY)) {
            return;
        }

        tooltip.add(Component.translatable(
                "jade.spectralization.fiber_node.connections",
                data.getInt(USAGE_KEY),
                data.getInt(CAPACITY_KEY)
        ));
        tooltip.add(Component.translatable(
                "jade.spectralization.fiber_node.profile",
                format(data.getDouble(CORE_RADIUS_KEY)),
                format(data.getDouble(NUMERICAL_APERTURE_KEY)),
                formatPercent(data.getDouble(TRANSMISSION_KEY))
        ));
    }

    @Override
    public ResourceLocation getUid() {
        return SpectralJadePlugin.FIBER_NODE;
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static String formatPercent(double value) {
        return String.format(Locale.ROOT, "%.1f%%", value * 100.0D);
    }
}
