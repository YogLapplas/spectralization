package io.github.yoglappland.spectralization.optics;

import java.util.List;
import java.util.Objects;
import net.minecraft.core.Direction;

public record BeamPacket(List<PlaneWaveComponent> components, BeamEnvelope envelope) {
    public static final int MAX_COMPONENTS = 8;

    public BeamPacket {
        Objects.requireNonNull(components, "components");
        Objects.requireNonNull(envelope, "envelope");

        components = List.copyOf(components);

        if (components.size() > MAX_COMPONENTS) {
            throw new IllegalArgumentException("Beam packet has too many components: " + components.size());
        }
    }

    public static BeamPacket empty(BeamEnvelope envelope) {
        return new BeamPacket(List.of(), envelope);
    }

    public static BeamPacket single(PlaneWaveComponent component, BeamEnvelope envelope) {
        return new BeamPacket(List.of(component), envelope);
    }

    public boolean isEmpty() {
        return components.isEmpty() || totalPower() <= 0.0;
    }

    public double totalPower() {
        double power = 0.0;

        for (PlaneWaveComponent component : components) {
            power += component.power();
        }

        return power;
    }

    public BeamPacket withDirection(Direction direction) {
        return new BeamPacket(
                components.stream()
                        .map(component -> component.withDirection(direction))
                        .toList(),
                envelope
        );
    }

    public BeamPacket scalePower(double factor) {
        if (!Double.isFinite(factor) || factor < 0.0) {
            throw new IllegalArgumentException("Beam power scale must be finite and non-negative");
        }

        return new BeamPacket(
                components.stream()
                        .map(component -> component.withPower(component.power() * factor))
                        .toList(),
                envelope
        );
    }
}
