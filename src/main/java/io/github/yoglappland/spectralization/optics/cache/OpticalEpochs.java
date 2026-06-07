package io.github.yoglappland.spectralization.optics.cache;

public record OpticalEpochs(
        long structure,
        long parameter,
        long field,
        long config
) {
    public static final OpticalEpochs ZERO = new OpticalEpochs(0L, 0L, 0L, 0L);

    public OpticalEpochs advance(OpticalDirtyKind dirtyKind) {
        return switch (dirtyKind) {
            case STRUCTURE -> new OpticalEpochs(structure + 1L, parameter, field, config);
            case PARAMETER, SOURCE -> new OpticalEpochs(structure, parameter + 1L, field, config);
            case FIELD -> new OpticalEpochs(structure, parameter, field + 1L, config);
            case CONFIG -> new OpticalEpochs(structure, parameter, field, config + 1L);
        };
    }

    public boolean structurallyMatches(OpticalEpochs other) {
        return structure == other.structure;
    }

    public boolean parametersMatch(OpticalEpochs other) {
        return parameter == other.parameter && field == other.field && config == other.config;
    }

    public boolean fullyMatches(OpticalEpochs other) {
        return structurallyMatches(other) && parametersMatch(other);
    }
}
