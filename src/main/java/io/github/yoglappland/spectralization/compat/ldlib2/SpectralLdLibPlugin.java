package io.github.yoglappland.spectralization.compat.ldlib2;

import com.lowdragmc.lowdraglib2.plugin.ILDLibPlugin;
import com.lowdragmc.lowdraglib2.plugin.LDLibPlugin;
import io.github.yoglappland.spectralization.Spectralization;

@LDLibPlugin
public final class SpectralLdLibPlugin implements ILDLibPlugin {
    @Override
    public void onLoad() {
        ThermalSmelterLdLibUi.ensureOfficialEditorAssets();
        Spectralization.LOGGER.info("Spectralization LDLib2 integration loaded");
    }
}
