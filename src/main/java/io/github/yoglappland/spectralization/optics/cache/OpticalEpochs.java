package io.github.yoglappland.spectralization.optics.cache;

public record OpticalEpochs(
        long structure,
        long topology,
        long parameter,
        long source,
        long field,
        long config
) {
    public static final OpticalEpochs ZERO = new OpticalEpochs(0L, 0L, 0L, 0L, 0L, 0L);

    public OpticalEpochs advance(OpticalDirtyKind dirtyKind) {
        return switch (dirtyKind) {
            case STRUCTURE -> new OpticalEpochs(structure + 1L, topology, parameter, source, field, config);
            case TOPOLOGY -> new OpticalEpochs(structure, topology + 1L, parameter, source, field, config);
            case PARAMETER -> new OpticalEpochs(structure, topology, parameter + 1L, source, field, config);
            case DATA -> new OpticalEpochs(structure, topology, parameter + 1L, source, field, config);
            case SOURCE -> new OpticalEpochs(structure, topology, parameter, source + 1L, field, config);
            case FIELD -> new OpticalEpochs(structure, topology, parameter, source, field + 1L, config);
            case CONFIG -> new OpticalEpochs(structure, topology, parameter, source, field, config + 1L);
        };
    }

    public boolean structurallyMatches(OpticalEpochs other) {
        return structure == other.structure && topology == other.topology;
    }

    public boolean parametersMatch(OpticalEpochs other) {
        return parameter == other.parameter
                && source == other.source
                && field == other.field
                && config == other.config;
    }

    public boolean fullyMatches(OpticalEpochs other) {
        return structurallyMatches(other) && parametersMatch(other);
    }
}
