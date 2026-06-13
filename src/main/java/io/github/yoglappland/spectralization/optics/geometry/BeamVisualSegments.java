package io.github.yoglappland.spectralization.optics.geometry;

import io.github.yoglappland.spectralization.optics.BeamPacket;
import io.github.yoglappland.spectralization.optics.CoherenceKind;
import io.github.yoglappland.spectralization.optics.CompiledOpticalTrace;
import io.github.yoglappland.spectralization.optics.OpticalPropagationEdge;
import io.github.yoglappland.spectralization.optics.PlaneWaveComponent;
import io.github.yoglappland.spectralization.optics.SpectralColorMap;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.Direction;

public final class BeamVisualSegments {
    private static final int DEFAULT_BEAM_RGB = 0xFF2300;

    public static List<BeamVisualSegment> fromTrace(
            CompiledOpticalTrace trace,
            BeamVisibilityKind visibilityKind
    ) {
        Objects.requireNonNull(trace, "trace");
        Objects.requireNonNull(visibilityKind, "visibilityKind");

        return trace.propagationEdges().stream()
                .map(edge -> fromPropagationEdge(edge, visibilityKind))
                .filter(segment -> segment.geometry().visualLevel() > 0 || segment.visibilityKind() == BeamVisibilityKind.DEBUG)
                .toList();
    }

    public static BeamVisualSegment fromPropagationEdge(
            OpticalPropagationEdge edge,
            BeamVisibilityKind visibilityKind
    ) {
        Objects.requireNonNull(edge, "edge");
        Objects.requireNonNull(visibilityKind, "visibilityKind");

        BeamPacket beam = edge.beam();
        Direction direction = edge.from().side();
        CoherenceKind coherence = dominantCoherence(beam);

        return new BeamVisualSegment(
                edge.from().pos(),
                edge.to().pos(),
                direction,
                coherence,
                visibilityKind,
                BeamGeometryOps.sample(beam.envelope(), beam.totalPower()),
                beam.totalPower(),
                visibleColorRgb(beam, coherence)
        );
    }

    private static CoherenceKind dominantCoherence(BeamPacket beam) {
        double coherentPower = 0.0;
        double incoherentPower = 0.0;

        for (PlaneWaveComponent component : beam.components()) {
            if (component.coherence() == CoherenceKind.COHERENT) {
                coherentPower += component.power();
            } else {
                incoherentPower += component.power();
            }
        }

        return coherentPower >= incoherentPower ? CoherenceKind.COHERENT : CoherenceKind.INCOHERENT;
    }

    private static int visibleColorRgb(BeamPacket beam, CoherenceKind coherence) {
        return SpectralColorMap.mixVisibleRgbForComponents(beam.components(), coherence, DEFAULT_BEAM_RGB);
    }

    private BeamVisualSegments() {
    }
}
