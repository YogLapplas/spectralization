package io.github.yoglappland.spectralization.optics;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.Direction;

public class CompiledOpticalNetwork {
    private static final InteractionEffect NO_EFFECT = (input, incomingDirection, result) -> {
    };

    private final List<OpticalScatteringRule> rules;
    private final AbsorptionModel absorptionModel;
    private final InteractionEffect interactionEffect;

    private CompiledOpticalNetwork(
            List<OpticalScatteringRule> rules,
            AbsorptionModel absorptionModel,
            InteractionEffect interactionEffect
    ) {
        this.rules = List.copyOf(rules);
        this.absorptionModel = Objects.requireNonNull(absorptionModel, "absorptionModel");
        this.interactionEffect = Objects.requireNonNull(interactionEffect, "interactionEffect");
    }

    public static Builder builder() {
        return new Builder();
    }

    public static CompiledOpticalNetwork legacy(OpticalElement element, net.minecraft.world.level.block.state.BlockState state,
            net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) {
        Objects.requireNonNull(element, "element");
        return new CompiledOpticalNetwork(
                List.of(),
                residualAbsorption(),
                (input, incomingDirection, result) -> {
                }
        ) {
            @Override
            public OpticalResult interact(BeamPacket input, Direction incomingDirection) {
                return element.interact(input, incomingDirection, state, level, pos);
            }
        };
    }

    public OpticalResult interact(BeamPacket input, Direction incomingDirection) {
        return scatter(input, incomingDirection, true);
    }

    public OpticalResult scatterWithoutEffects(BeamPacket input, Direction incomingDirection) {
        return scatter(input, incomingDirection, false);
    }

    private OpticalResult scatter(BeamPacket input, Direction incomingDirection, boolean applyInteractionEffect) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(incomingDirection, "incomingDirection");

        if (input.isEmpty()) {
            return OpticalResult.empty();
        }

        List<OutputBeam> outputs = new ArrayList<>();

        for (OpticalScatteringRule rule : rules) {
            if (!rule.matches(incomingDirection)) {
                continue;
            }

            OutputBeam output = rule.apply(input, incomingDirection);

            if (!output.beam().isEmpty()) {
                outputs.add(output);
            }
        }

        double absorbedPower = absorptionModel.absorbedPower(input, incomingDirection, outputs);
        OpticalResult result = new OpticalResult(outputs, absorbedPower, absorbedPower);

        if (applyInteractionEffect) {
            interactionEffect.apply(input, incomingDirection, result);
        }

        return result;
    }

    public Set<Direction> outputDirections(BeamPacket input, Direction incomingDirection) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(incomingDirection, "incomingDirection");

        if (input.isEmpty()) {
            return Set.of();
        }

        Set<Direction> directions = EnumSet.noneOf(Direction.class);

        for (OpticalScatteringRule rule : rules) {
            if (!rule.matches(incomingDirection)) {
                continue;
            }

            OutputBeam output = rule.apply(input, incomingDirection);

            if (!output.beam().isEmpty()) {
                directions.add(output.outgoingDirection());
            }
        }

        return directions;
    }

    public static BeamTransform passThrough() {
        return (input, incomingDirection, outgoingDirection) -> input.withDirection(outgoingDirection);
    }

    public static BeamTransform scale(double factor) {
        if (!Double.isFinite(factor) || factor < 0.0) {
            throw new IllegalArgumentException("Optical scale factor must be finite and non-negative");
        }

        return (input, incomingDirection, outgoingDirection) -> input.withDirection(outgoingDirection).scalePower(factor);
    }

    public static AbsorptionModel residualAbsorption() {
        return (input, incomingDirection, outputs) -> Math.max(0.0, input.totalPower() - outputPower(outputs));
    }

    private static double outputPower(List<OutputBeam> outputs) {
        double power = 0.0;

        for (OutputBeam output : outputs) {
            power += output.beam().totalPower();
        }

        return power;
    }

    @FunctionalInterface
    public interface BeamTransform {
        BeamPacket transform(BeamPacket input, Direction incomingDirection, Direction outgoingDirection);
    }

    @FunctionalInterface
    public interface AbsorptionModel {
        double absorbedPower(BeamPacket input, Direction incomingDirection, List<OutputBeam> outputs);
    }

    @FunctionalInterface
    public interface InteractionEffect {
        void apply(BeamPacket input, Direction incomingDirection, OpticalResult result);
    }

    public static final class Builder {
        private final List<OpticalScatteringRule> rules = new ArrayList<>();
        private AbsorptionModel absorptionModel = residualAbsorption();
        private InteractionEffect interactionEffect = NO_EFFECT;

        public Builder addRule(Direction incomingDirection, Direction outgoingDirection, BeamTransform transform) {
            rules.add(new OpticalScatteringRule(incomingDirection, outgoingDirection, transform));
            return this;
        }

        public Builder absorptionModel(AbsorptionModel absorptionModel) {
            this.absorptionModel = Objects.requireNonNull(absorptionModel, "absorptionModel");
            return this;
        }

        public Builder interactionEffect(InteractionEffect interactionEffect) {
            this.interactionEffect = Objects.requireNonNull(interactionEffect, "interactionEffect");
            return this;
        }

        public CompiledOpticalNetwork build() {
            return new CompiledOpticalNetwork(rules, absorptionModel, interactionEffect);
        }
    }
}
