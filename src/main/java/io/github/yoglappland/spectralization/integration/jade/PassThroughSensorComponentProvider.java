package io.github.yoglappland.spectralization.integration.jade;

import io.github.yoglappland.spectralization.block.PassThroughSensorBlock;
import io.github.yoglappland.spectralization.blockentity.PassThroughSensorBlockEntity;
import java.util.Locale;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public class PassThroughSensorComponentProvider implements IBlockComponentProvider {
    public static final PassThroughSensorComponentProvider INSTANCE = new PassThroughSensorComponentProvider();

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (!(accessor.getBlockEntity() instanceof PassThroughSensorBlockEntity sensor)) {
            return;
        }

        Direction positiveZDirection = accessor.getBlockState().getValue(PassThroughSensorBlock.FACING);
        tooltip.add(Component.translatable(
                "jade.spectralization.pass_through_sensor.directional_power",
                directionName(positiveZDirection),
                formatPower(sensor.getPositiveZPower())
        ));
        tooltip.add(Component.translatable(
                "jade.spectralization.pass_through_sensor.directional_power",
                directionName(positiveZDirection.getOpposite()),
                formatPower(sensor.getNegativeZPower())
        ));
    }

    @Override
    public ResourceLocation getUid() {
        return SpectralJadePlugin.PASS_THROUGH_SENSOR;
    }

    private static String formatPower(double power) {
        double absolutePower = Math.abs(power);

        if ((absolutePower > 0.0 && absolutePower < 0.01) || absolutePower >= 10000.0) {
            return String.format(Locale.ROOT, "%.3e", power);
        }

        return String.format(Locale.ROOT, "%.3f", power);
    }

    private static Component directionName(Direction direction) {
        return Component.translatable("direction.spectralization." + direction.getSerializedName());
    }
}
