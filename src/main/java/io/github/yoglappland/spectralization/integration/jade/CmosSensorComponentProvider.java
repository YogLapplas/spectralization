package io.github.yoglappland.spectralization.integration.jade;

import io.github.yoglappland.spectralization.blockentity.CmosSensorBlockEntity;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public class CmosSensorComponentProvider implements IBlockComponentProvider {
    public static final CmosSensorComponentProvider INSTANCE = new CmosSensorComponentProvider();

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (!(accessor.getBlockEntity() instanceof CmosSensorBlockEntity sensor)) {
            return;
        }

        tooltip.add(Component.translatable(
                "jade.spectralization.cmos_sensor.power",
                formatPower(sensor.getPowerForSignal()),
                Component.translatable("jade.spectralization.sensor.reliability."
                        + (sensor.isOutputReliable() ? "reliable" : "unstable"))
        ));
    }

    @Override
    public ResourceLocation getUid() {
        return SpectralJadePlugin.CMOS_SENSOR;
    }

    private static String formatPower(double power) {
        double absolutePower = Math.abs(power);

        if ((absolutePower > 0.0 && absolutePower < 0.01) || absolutePower >= 10000.0) {
            return String.format(Locale.ROOT, "%.3e", power);
        }

        return String.format(Locale.ROOT, "%.3f", power);
    }
}
