package io.github.yoglappland.spectralization.optics.cache;

import io.github.yoglappland.spectralization.optics.OutputBeam;
import io.github.yoglappland.spectralization.optics.SpotRecord;
import io.github.yoglappland.spectralization.optics.compiler.CompiledReadoutLayer;
import io.github.yoglappland.spectralization.optics.compiler.CompiledPortGraph;
import io.github.yoglappland.spectralization.optics.compiler.ScalarPowerSolution;
import io.github.yoglappland.spectralization.optics.projection.SpotProjectionAllocation;
import io.github.yoglappland.spectralization.network.BeamPathOverlayPayload;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.List;
import java.util.Objects;

public record CachedOpticalTrace(
        int networkId,
        OutputBeam sourceOutput,
        List<ReceiverOutput> receiverOutputs,
        LongSet dependencies,
        CompiledPortGraph portGraph,
        CompiledReadoutLayer readoutLayer,
        ScalarPowerSolution scalarPowerSolution,
        List<BeamPathOverlayPayload.Segment> hudSegments,
        List<SpotRecord> spotRecords,
        LongSet projectionDependencies,
        List<SpotProjectionAllocation> spotAllocations,
        boolean unstable
) {
    public CachedOpticalTrace {
        Objects.requireNonNull(sourceOutput, "sourceOutput");
        Objects.requireNonNull(receiverOutputs, "receiverOutputs");
        Objects.requireNonNull(dependencies, "dependencies");
        Objects.requireNonNull(portGraph, "portGraph");
        Objects.requireNonNull(readoutLayer, "readoutLayer");
        Objects.requireNonNull(scalarPowerSolution, "scalarPowerSolution");
        Objects.requireNonNull(hudSegments, "hudSegments");
        Objects.requireNonNull(spotRecords, "spotRecords");
        Objects.requireNonNull(projectionDependencies, "projectionDependencies");
        Objects.requireNonNull(spotAllocations, "spotAllocations");
        receiverOutputs = List.copyOf(receiverOutputs);
        hudSegments = List.copyOf(hudSegments);
        spotRecords = List.copyOf(spotRecords);
        spotAllocations = List.copyOf(spotAllocations);
    }

    public boolean matches(OutputBeam outputBeam) {
        return sourceOutput.equals(outputBeam);
    }

    public List<ReceiverOutput> sampleCompiledOutputs() {
        return readoutLayer.sample(scalarPowerSolution);
    }

    public CachedOpticalTrace withSpotLayer(
            List<SpotRecord> spotRecords,
            LongSet projectionDependencies,
            List<SpotProjectionAllocation> spotAllocations
    ) {
        return new CachedOpticalTrace(
                networkId,
                sourceOutput,
                receiverOutputs,
                dependencies,
                portGraph,
                readoutLayer,
                scalarPowerSolution,
                hudSegments,
                spotRecords,
                projectionDependencies,
                spotAllocations,
                unstable
        );
    }
}
