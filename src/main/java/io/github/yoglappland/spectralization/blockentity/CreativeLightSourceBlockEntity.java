package io.github.yoglappland.spectralization.blockentity;

import io.github.yoglappland.spectralization.optics.BeamEnvelope;
import io.github.yoglappland.spectralization.optics.BeamModel;
import io.github.yoglappland.spectralization.optics.BeamPacket;
import io.github.yoglappland.spectralization.optics.CoherenceKind;
import io.github.yoglappland.spectralization.optics.EnvironmentLightSpectra;
import io.github.yoglappland.spectralization.optics.FrequencyKey;
import io.github.yoglappland.spectralization.optics.OpticalSource;
import io.github.yoglappland.spectralization.optics.OutputBeam;
import io.github.yoglappland.spectralization.optics.PlaneWaveComponent;
import io.github.yoglappland.spectralization.optics.SpectralRegion;
import io.github.yoglappland.spectralization.optics.cache.OpticalDirtyKind;
import io.github.yoglappland.spectralization.optics.cache.OpticalTraceCache;
import io.github.yoglappland.spectralization.registry.SpectralBlockEntities;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class CreativeLightSourceBlockEntity extends BlockEntity {
    public static final int MAX_SPECTRUM_BINS = SpectralRegion.VISIBLE.defaultBins();
    public static final int MAX_SPECTRUM_WEIGHT = 1000;
    public static final int DATA_COUNT = 10 + MAX_SPECTRUM_BINS;
    public static final int DATA_REGION = 0;
    public static final int DATA_BIN = 1;
    public static final int DATA_POWER = 2;
    public static final int DATA_COHERENCE = 3;
    public static final int DATA_BEAM_MODEL = 4;
    public static final int DATA_RADIUS_MILLI = 5;
    public static final int DATA_DIVERGENCE_MILLI = 6;
    public static final int DATA_FOCUS_DISTANCE_MILLI = 7;
    public static final int DATA_MODE_M = 8;
    public static final int DATA_MODE_N = 9;
    public static final int DATA_SPECTRUM_START = 10;

    private SpectralRegion region = FrequencyKey.DEBUG_VISIBLE.region();
    private int bin = FrequencyKey.DEBUG_VISIBLE.bin();
    private int power = 100;
    private CoherenceKind coherence = CoherenceKind.COHERENT;
    private BeamModel beamModel = BeamModel.COLLIMATED;
    private int radiusMilli = 250;
    private int divergenceMilli = 0;
    private int focusDistanceMilli = 0;
    private int modeM = 0;
    private int modeN = 0;
    private final int[] spectrumWeights = defaultVisibleWhiteSpectrumWeights();

    public CreativeLightSourceBlockEntity(BlockPos pos, BlockState blockState) {
        super(SpectralBlockEntities.CREATIVE_LIGHT_SOURCE.get(), pos, blockState);
    }

    @Override
    public void onLoad() {
        super.onLoad();

        if (this.level == null || this.level.isClientSide) {
            return;
        }

        if (this.getBlockState().getBlock() instanceof OpticalSource opticalSource) {
            for (OutputBeam outputBeam : opticalSource.getOutputBeams(this.getBlockState(), this.level, this.worldPosition)) {
                OpticalTraceCache.requestOrApply(this.level, this.worldPosition, outputBeam);
            }
        }
    }

    public static void tick(Level level, BlockPos pos, BlockState state, CreativeLightSourceBlockEntity source) {
        if (level.isClientSide || !(state.getBlock() instanceof OpticalSource opticalSource)) {
            return;
        }

        for (OutputBeam outputBeam : opticalSource.getOutputBeams(state, level, pos)) {
            OpticalTraceCache.requestOrApply(level, pos, outputBeam);
        }
    }

    public OutputBeam createOutputBeam(Direction direction) {
        BeamEnvelope envelope = new BeamEnvelope(
                beamModel,
                radiusMilli / 1000.0,
                divergenceMilli / 1000.0,
                focusDistanceMilli / 1000.0,
                modeM,
                modeN
        );
        List<PlaneWaveComponent> components = spectrumComponents(direction);

        return new OutputBeam(direction, new BeamPacket(components, envelope));
    }

    public ContainerData createDataAccess() {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return getData(index);
            }

            @Override
            public void set(int index, int value) {
                setData(index, value);
            }

            @Override
            public int getCount() {
                return DATA_COUNT;
            }
        };
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);

        if (tag.contains("Region")) {
            setData(DATA_REGION, tag.getInt("Region"));
        }

        if (tag.contains("Bin")) {
            setData(DATA_BIN, tag.getInt("Bin"));
        }

        if (tag.contains("Power")) {
            setData(DATA_POWER, tag.getInt("Power"));
        }

        if (tag.contains("Coherence")) {
            setData(DATA_COHERENCE, tag.getInt("Coherence"));
        }

        if (tag.contains("BeamModel")) {
            setData(DATA_BEAM_MODEL, tag.getInt("BeamModel"));
        }

        if (tag.contains("RadiusMilli")) {
            setData(DATA_RADIUS_MILLI, tag.getInt("RadiusMilli"));
        }

        if (tag.contains("DivergenceMilli")) {
            setData(DATA_DIVERGENCE_MILLI, tag.getInt("DivergenceMilli"));
        }

        if (tag.contains("FocusDistanceMilli")) {
            setData(DATA_FOCUS_DISTANCE_MILLI, tag.getInt("FocusDistanceMilli"));
        }

        if (tag.contains("ModeM")) {
            setData(DATA_MODE_M, tag.getInt("ModeM"));
        }

        if (tag.contains("ModeN")) {
            setData(DATA_MODE_N, tag.getInt("ModeN"));
        }

        if (tag.contains("SpectrumWeights")) {
            int[] savedWeights = tag.getIntArray("SpectrumWeights");
            int length = Math.min(savedWeights.length, spectrumWeights.length);

            for (int index = 0; index < length; index++) {
                spectrumWeights[index] = Mth.clamp(savedWeights[index], 0, MAX_SPECTRUM_WEIGHT);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Region", region.ordinal());
        tag.putInt("Bin", bin);
        tag.putInt("Power", power);
        tag.putInt("Coherence", coherence.ordinal());
        tag.putInt("BeamModel", beamModel.ordinal());
        tag.putInt("RadiusMilli", radiusMilli);
        tag.putInt("DivergenceMilli", divergenceMilli);
        tag.putInt("FocusDistanceMilli", focusDistanceMilli);
        tag.putInt("ModeM", modeM);
        tag.putInt("ModeN", modeN);
        tag.putIntArray("SpectrumWeights", spectrumWeights);
    }

    private int getData(int index) {
        if (index >= DATA_SPECTRUM_START && index < DATA_COUNT) {
            return spectrumWeights[index - DATA_SPECTRUM_START];
        }

        return switch (index) {
            case DATA_REGION -> region.ordinal();
            case DATA_BIN -> bin;
            case DATA_POWER -> power;
            case DATA_COHERENCE -> coherence.ordinal();
            case DATA_BEAM_MODEL -> beamModel.ordinal();
            case DATA_RADIUS_MILLI -> radiusMilli;
            case DATA_DIVERGENCE_MILLI -> divergenceMilli;
            case DATA_FOCUS_DISTANCE_MILLI -> focusDistanceMilli;
            case DATA_MODE_M -> modeM;
            case DATA_MODE_N -> modeN;
            default -> 0;
        };
    }

    private void setData(int index, int value) {
        if (index >= DATA_SPECTRUM_START && index < DATA_COUNT) {
            int binIndex = index - DATA_SPECTRUM_START;
            int clamped = Mth.clamp(value, 0, MAX_SPECTRUM_WEIGHT);

            if (spectrumWeights[binIndex] != clamped) {
                spectrumWeights[binIndex] = clamped;
                markSourceChanged();
            }

            return;
        }

        switch (index) {
            case DATA_REGION -> {
                SpectralRegion[] regions = SpectralRegion.values();
                region = regions[Mth.clamp(value, 0, regions.length - 1)];
                bin = Mth.clamp(bin, 0, region.defaultBins() - 1);
            }
            case DATA_BIN -> bin = Mth.clamp(value, 0, region.defaultBins() - 1);
            case DATA_POWER -> power = Mth.clamp(value, 0, 1_000_000);
            case DATA_COHERENCE -> {
                CoherenceKind[] kinds = CoherenceKind.values();
                coherence = kinds[Mth.clamp(value, 0, kinds.length - 1)];
            }
            case DATA_BEAM_MODEL -> {
                BeamModel[] models = BeamModel.values();
                beamModel = models[Mth.clamp(value, 0, models.length - 1)];
            }
            case DATA_RADIUS_MILLI -> radiusMilli = Mth.clamp(value, 0, 10_000);
            case DATA_DIVERGENCE_MILLI -> divergenceMilli = Mth.clamp(value, 0, 10_000);
            case DATA_FOCUS_DISTANCE_MILLI -> focusDistanceMilli = Mth.clamp(value, 0, 1_000_000);
            case DATA_MODE_M -> modeM = Mth.clamp(value, 0, 16);
            case DATA_MODE_N -> modeN = Mth.clamp(value, 0, 16);
            default -> {
            }
        }

        markSourceChanged();
    }

    private void markSourceChanged() {
        setChanged();

        if (this.level != null && !this.level.isClientSide) {
            OpticalTraceCache.markChanged(this.level, this.worldPosition, OpticalDirtyKind.SOURCE);
        }
    }

    private List<PlaneWaveComponent> spectrumComponents(Direction direction) {
        int bins = Math.min(region.defaultBins(), spectrumWeights.length);
        List<SpectrumBin> activeBins = new ArrayList<>();

        for (int index = 0; index < bins; index++) {
            int weight = spectrumWeights[index];

            if (weight > 0) {
                activeBins.add(new SpectrumBin(index, weight));
            }
        }

        if (activeBins.isEmpty()) {
            return List.of(new PlaneWaveComponent(new FrequencyKey(region, bin), power, direction, coherence));
        }

        List<SpectrumBin> emittedBins = representativeSpectrumBins(activeBins);
        double emittedWeightTotal = emittedBins.stream()
                .mapToInt(SpectrumBin::weight)
                .sum();

        if (emittedWeightTotal <= 0.0) {
            return List.of(new PlaneWaveComponent(new FrequencyKey(region, bin), power, direction, coherence));
        }

        return emittedBins.stream()
                .map(spectrumBin -> new PlaneWaveComponent(
                        new FrequencyKey(region, spectrumBin.bin()),
                        power * spectrumBin.weight() / emittedWeightTotal,
                        direction,
                        coherence
                ))
                .toList();
    }

    private record SpectrumBin(int bin, int weight) {
    }

    private static List<SpectrumBin> representativeSpectrumBins(List<SpectrumBin> activeBins) {
        if (activeBins.size() <= BeamPacket.MAX_COMPONENTS) {
            return List.copyOf(activeBins);
        }

        int totalWeight = activeBins.stream()
                .mapToInt(SpectrumBin::weight)
                .sum();

        if (totalWeight <= 0) {
            return List.of();
        }

        List<SpectrumBin> emittedBins = new ArrayList<>();
        double bucketSize = totalWeight / (double) BeamPacket.MAX_COMPONENTS;
        int activeIndex = 0;
        double activeStart = 0.0;
        double activeEnd = activeBins.getFirst().weight();

        for (int bucket = 0; bucket < BeamPacket.MAX_COMPONENTS; bucket++) {
            double bucketStart = bucket * bucketSize;
            double bucketEnd = bucket == BeamPacket.MAX_COMPONENTS - 1
                    ? totalWeight
                    : (bucket + 1) * bucketSize;

            while (activeIndex < activeBins.size() - 1 && activeEnd <= bucketStart) {
                activeStart = activeEnd;
                activeIndex++;
                activeEnd += activeBins.get(activeIndex).weight();
            }

            double weightedBin = 0.0;
            double bucketWeight = 0.0;
            int scanIndex = activeIndex;
            double scanStart = activeStart;
            double scanEnd = activeEnd;

            while (scanIndex < activeBins.size() && scanStart < bucketEnd) {
                SpectrumBin spectrumBin = activeBins.get(scanIndex);
                double overlap = Math.max(0.0, Math.min(scanEnd, bucketEnd) - Math.max(scanStart, bucketStart));

                if (overlap > 0.0) {
                    weightedBin += spectrumBin.bin() * overlap;
                    bucketWeight += overlap;
                }

                scanIndex++;

                if (scanIndex < activeBins.size()) {
                    scanStart = scanEnd;
                    scanEnd += activeBins.get(scanIndex).weight();
                }
            }

            if (bucketWeight > 0.0) {
                int bin = Mth.clamp((int) Math.round(weightedBin / bucketWeight), 0, Integer.MAX_VALUE);
                int weight = Math.max(1, (int) Math.round(bucketWeight));
                emittedBins.add(new SpectrumBin(bin, weight));
            }
        }

        return mergeDuplicateBins(emittedBins);
    }

    private static List<SpectrumBin> mergeDuplicateBins(List<SpectrumBin> bins) {
        if (bins.isEmpty()) {
            return List.of();
        }

        List<SpectrumBin> merged = new ArrayList<>();

        for (SpectrumBin bin : bins) {
            if (!merged.isEmpty() && merged.getLast().bin() == bin.bin()) {
                SpectrumBin previous = merged.removeLast();
                merged.add(new SpectrumBin(previous.bin(), previous.weight() + bin.weight()));
            } else {
                merged.add(bin);
            }
        }

        return List.copyOf(merged);
    }

    private static int[] defaultVisibleWhiteSpectrumWeights() {
        int[] weights = new int[MAX_SPECTRUM_BINS];
        Map<FrequencyKey, Double> spectrum = EnvironmentLightSpectra.creativeVisibleWhiteProfile().normalizedSpectrum();
        double maxWeight = 0.0;

        for (Map.Entry<FrequencyKey, Double> entry : spectrum.entrySet()) {
            if (entry.getKey().region() == SpectralRegion.VISIBLE) {
                maxWeight = Math.max(maxWeight, entry.getValue());
            }
        }

        if (maxWeight <= 0.0) {
            return weights;
        }

        for (Map.Entry<FrequencyKey, Double> entry : spectrum.entrySet()) {
            FrequencyKey frequency = entry.getKey();

            if (frequency.region() != SpectralRegion.VISIBLE) {
                continue;
            }

            int bin = frequency.bin();

            if (bin >= 0 && bin < weights.length) {
                weights[bin] = Mth.clamp(
                        (int) Math.round(entry.getValue() / maxWeight * MAX_SPECTRUM_WEIGHT),
                        1,
                        MAX_SPECTRUM_WEIGHT
                );
            }
        }

        return weights;
    }
}
