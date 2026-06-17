package io.github.yoglappland.spectralization.optics.metasurface;

import io.github.yoglappland.spectralization.Spectralization;
import java.util.Locale;
import net.minecraft.resources.ResourceLocation;

public enum MetamaterialStandardTemplate {
    PHASE_LATTICE("phase_lattice", new MetamaterialVector(-4, 0, 2)),
    BROADBAND_GRATING("broadband_grating", new MetamaterialVector(0, 3, -2)),
    HOLOGRAPHIC_CHANNEL("holographic_channel", new MetamaterialVector(4, -2, 3)),
    SOLAR_DOPING_MASK("solar_doping_mask", new MetamaterialVector(6, 5, -3));

    private final String id;
    private final MetamaterialVector vector;

    MetamaterialStandardTemplate(String id, MetamaterialVector vector) {
        this.id = id;
        this.vector = vector;
    }

    public String idString() {
        return id;
    }

    public ResourceLocation id() {
        return ResourceLocation.fromNamespaceAndPath(Spectralization.MODID, id);
    }

    public MetamaterialVector vector() {
        return vector;
    }

    public String translationKey() {
        return "metamaterial." + Spectralization.MODID + ".standard." + id;
    }

    public static MetamaterialStandardTemplate byIndex(int index) {
        MetamaterialStandardTemplate[] values = values();
        return values[Math.floorMod(index, values.length)];
    }

    public static MetamaterialStandardTemplate byId(String id) {
        if (id == null || id.isBlank()) {
            return PHASE_LATTICE;
        }

        String normalized = id.toLowerCase(Locale.ROOT);
        for (MetamaterialStandardTemplate template : values()) {
            if (template.id.equals(normalized)
                    || template.id().toString().equals(normalized)) {
                return template;
            }
        }

        return PHASE_LATTICE;
    }
}
