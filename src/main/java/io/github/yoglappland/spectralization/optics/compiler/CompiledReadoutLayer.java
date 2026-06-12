package io.github.yoglappland.spectralization.optics.compiler;

import io.github.yoglappland.spectralization.optics.cache.ReceiverOutput;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record CompiledReadoutLayer(
        List<OpticalReadoutBinding> bindings,
        CompiledBeamProfileLayer beamProfileLayer
) {
    public static final CompiledReadoutLayer EMPTY =
            new CompiledReadoutLayer(List.of(), CompiledBeamProfileLayer.EMPTY);

    public CompiledReadoutLayer(List<OpticalReadoutBinding> bindings) {
        this(bindings, CompiledBeamProfileLayer.EMPTY);
    }

    public CompiledReadoutLayer {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(beamProfileLayer, "beamProfileLayer");
        bindings = List.copyOf(bindings);
    }

    public List<ReceiverOutput> sample(ScalarPowerSolution solution) {
        List<ReceiverOutput> outputs = new ArrayList<>();

        for (OpticalReadoutBinding binding : bindings) {
            ReceiverOutput output = binding.sample(solution, beamProfileLayer);

            if (output != null) {
                outputs.add(output);
            }
        }

        return outputs;
    }

    public int size() {
        return bindings.size();
    }
}
