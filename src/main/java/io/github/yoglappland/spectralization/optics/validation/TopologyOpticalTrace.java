package io.github.yoglappland.spectralization.optics.validation;

import io.github.yoglappland.spectralization.optics.OpticalPort;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.BlockPos;

public final class TopologyOpticalTrace {
    private final Map<OpticalPort, Double> incidentPowerByPort;
    private final Map<BlockPos, Double> affectedAirPowerByPos;
    private final int processedStates;

    public TopologyOpticalTrace(
            Map<OpticalPort, Double> incidentPowerByPort,
            Map<BlockPos, Double> affectedAirPowerByPos,
            int processedStates
    ) {
        this.incidentPowerByPort = Map.copyOf(Objects.requireNonNull(incidentPowerByPort, "incidentPowerByPort"));
        this.affectedAirPowerByPos = Map.copyOf(Objects.requireNonNull(affectedAirPowerByPos, "affectedAirPowerByPos"));
        this.processedStates = processedStates;
    }

    public Map<OpticalPort, Double> incidentPowerByPort() {
        return incidentPowerByPort;
    }

    public Map<BlockPos, Double> affectedAirPowerByPos() {
        return affectedAirPowerByPos;
    }

    public int processedStates() {
        return processedStates;
    }

    static final class Builder {
        private final Map<OpticalPort, Double> incidentPowerByPort = new HashMap<>();
        private final Map<BlockPos, Double> affectedAirPowerByPos = new HashMap<>();
        private int processedStates;

        void addIncidentPower(OpticalPort port, double power) {
            incidentPowerByPort.merge(port, power, Double::sum);
        }

        void addAffectedAirPower(BlockPos pos, double power) {
            affectedAirPowerByPos.merge(pos.immutable(), power, Double::sum);
        }

        void incrementProcessedStates() {
            processedStates++;
        }

        int processedStates() {
            return processedStates;
        }

        TopologyOpticalTrace build() {
            return new TopologyOpticalTrace(incidentPowerByPort, affectedAirPowerByPos, processedStates);
        }
    }
}
