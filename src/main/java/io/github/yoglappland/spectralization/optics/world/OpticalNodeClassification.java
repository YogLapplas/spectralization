package io.github.yoglappland.spectralization.optics.world;

public record OpticalNodeClassification(
        boolean source,
        boolean element,
        boolean receiver,
        boolean material,
        boolean fieldSource
) {
    public static final OpticalNodeClassification NONE =
            new OpticalNodeClassification(false, false, false, false, false);

    public boolean optical() {
        return source || element || receiver || material || fieldSource;
    }
}
