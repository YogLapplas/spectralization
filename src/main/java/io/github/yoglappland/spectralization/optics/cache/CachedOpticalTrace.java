package io.github.yoglappland.spectralization.optics.cache;

import io.github.yoglappland.spectralization.optics.OutputBeam;
import io.github.yoglappland.spectralization.optics.SpotRecord;
import io.github.yoglappland.spectralization.optics.compiler.CompiledReadoutLayer;
import io.github.yoglappland.spectralization.optics.compiler.CompiledPortGraph;
import io.github.yoglappland.spectralization.optics.compiler.ScalarPowerSolution;
import io.github.yoglappland.spectralization.network.BeamPathOverlayPayload;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.List;
import java.util.Objects;
import net.minecraft.world.level.Level;

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
        receiverOutputs = List.copyOf(receiverOutputs);
        hudSegments = List.copyOf(hudSegments);
        spotRecords = List.copyOf(spotRecords);
    }

    public boolean matches(OutputBeam outputBeam) {
        return sourceOutput.equals(outputBeam);
    }

    public void applyOutputs(Level level, boolean reliable, long step) {
        if (readoutLayer.size() == 0) {
            return;
        }

        applyCompiledOutputs(level, reliable && scalarPowerSolution.reliableForReadout(), step);
    }

    public List<ReceiverOutput> sampleCompiledOutputs() {
        return readoutLayer.sample(scalarPowerSolution);
    }

    public void applyCompiledOutputs(Level level, boolean reliable, long step) {
        for (ReceiverOutput receiverOutput : sampleCompiledOutputs()) {
            receiverOutput.apply(level, reliable, step);
        }
    }
}
