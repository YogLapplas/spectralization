package io.github.yoglappland.spectralization.optics.cache;

import io.github.yoglappland.spectralization.optics.compiler.CompiledPortGraph;
import io.github.yoglappland.spectralization.optics.compiler.CompiledReadoutLayer;
import io.github.yoglappland.spectralization.optics.compiler.PortGraphNode;
import io.github.yoglappland.spectralization.optics.compiler.ScalarPowerSolution;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.world.level.Level;

public record CachedOpticalSystem(
        int systemId,
        CompiledPortGraph graph,
        CompiledPortGraph passiveGraph,
        CompiledPortGraph coherentGraph,
        Map<PortGraphNode, Double> sourcePowersByNode,
        CompiledReadoutLayer readoutLayer,
        ScalarPowerSolution solution,
        List<ReceiverOutput> receiverOutputs,
        int sourceCount,
        OpticalEpochs epochs,
        boolean structurallyFresh,
        boolean parametricallyFresh,
        boolean usableForGameplay
) {
    public CachedOpticalSystem {
        if (systemId <= 0) {
            throw new IllegalArgumentException("Optical system id must be positive");
        }

        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(passiveGraph, "passiveGraph");
        Objects.requireNonNull(coherentGraph, "coherentGraph");
        Objects.requireNonNull(sourcePowersByNode, "sourcePowersByNode");
        Objects.requireNonNull(readoutLayer, "readoutLayer");
        Objects.requireNonNull(solution, "solution");
        Objects.requireNonNull(receiverOutputs, "receiverOutputs");
        Objects.requireNonNull(epochs, "epochs");

        if (sourceCount < 0) {
            throw new IllegalArgumentException("Optical system source count must be non-negative");
        }

        sourcePowersByNode = Map.copyOf(sourcePowersByNode);
        receiverOutputs = List.copyOf(receiverOutputs);
    }

    public void applyOutputs(Level level, boolean reliable, long step) {
        if (reliable && !usableForGameplay) {
            return;
        }

        boolean sampleReliable = reliable && usableForGameplay;

        for (ReceiverOutput receiverOutput : receiverOutputs) {
            receiverOutput.apply(level, sampleReliable, step);
        }
    }

    public CachedOpticalSystem withRuntimeState(
            int systemId,
            OpticalEpochs epochs,
            boolean structurallyFresh,
            boolean parametricallyFresh,
            boolean usableForGameplay
    ) {
        return new CachedOpticalSystem(
                systemId,
                graph,
                passiveGraph,
                coherentGraph,
                sourcePowersByNode,
                readoutLayer,
                solution,
                receiverOutputs,
                sourceCount,
                epochs,
                structurallyFresh,
                parametricallyFresh,
                usableForGameplay
        );
    }
}
