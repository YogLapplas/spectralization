package io.github.yoglappland.spectralization.optics;

import io.github.yoglappland.spectralization.optics.geometry.SpatialModeCoupling;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    public BeamPacket withEnvelope(BeamEnvelope envelope) {
        return new BeamPacket(components, envelope);
    }

    public BeamPacket withCoherence(CoherenceKind coherence) {
        return new BeamPacket(
                components.stream()
                        .map(component -> component.withCoherence(coherence))
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

    public BeamPacket applySpatialCoupling(SpatialModeCoupling coupling) {
        Objects.requireNonNull(coupling, "coupling");

        if (isEmpty()) {
            return empty(coupling.orderedEnvelope());
        }

        List<PlaneWaveComponent> transformed = new ArrayList<>();

        for (PlaneWaveComponent component : components) {
            double orderedPower = component.power() * coupling.orderedFraction();
            double strayPower = component.power() * coupling.strayFraction();

            if (orderedPower > 0.0) {
                transformed.add(component.withPower(orderedPower));
            }

            if (strayPower > 0.0) {
                transformed.add(component.withCoherence(CoherenceKind.INCOHERENT).withPower(strayPower));
            }
        }

        return new BeamPacket(normalizeComponents(transformed), coupling.orderedEnvelope());
    }

    private static List<PlaneWaveComponent> normalizeComponents(Collection<PlaneWaveComponent> rawComponents) {
        Map<ComponentKey, Double> powerByKey = new LinkedHashMap<>();

        for (PlaneWaveComponent component : rawComponents) {
            if (component.power() <= 0.0) {
                continue;
            }

            ComponentKey key = new ComponentKey(component.frequency(), component.direction(), component.coherence());
            powerByKey.merge(key, component.power(), Double::sum);
        }

        return powerByKey.entrySet().stream()
                .map(entry -> entry.getKey().component(entry.getValue()))
                .sorted(Comparator.comparingDouble(PlaneWaveComponent::power).reversed())
                .limit(MAX_COMPONENTS)
                .toList();
    }

    private record ComponentKey(FrequencyKey frequency, Direction direction, CoherenceKind coherence) {
        private PlaneWaveComponent component(double power) {
            return new PlaneWaveComponent(frequency, power, direction, coherence);
        }
    }
}
