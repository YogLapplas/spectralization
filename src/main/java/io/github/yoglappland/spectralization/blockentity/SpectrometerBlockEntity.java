package io.github.yoglappland.spectralization.blockentity;

import io.github.yoglappland.spectralization.optics.FrequencyKey;
import io.github.yoglappland.spectralization.optics.SpectralRegion;
import io.github.yoglappland.spectralization.optics.cache.SpectralReadoutSample;
import io.github.yoglappland.spectralization.registry.SpectralBlockEntities;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class SpectrometerBlockEntity extends BlockEntity {
    public static final int MAX_DISPLAY_BINS = 32;
    public static final int DATA_REGION = 0;
    public static final int DATA_RELIABLE = 1;
    public static final int DATA_TOTAL_POWER = 2;
    public static final int DATA_PEAK_BIN = 3;
    public static final int DATA_REGION_POWER = 4;
    public static final int DATA_SPECTRUM_START = 5;
    public static final int DATA_COUNT = DATA_SPECTRUM_START + MAX_DISPLAY_BINS;
    public static final int POWER_SCALE = 100;

    private static final long SAMPLE_HOLD_TICKS = 1L;
    private static final double EPSILON = 1.0E-6;
    private static final String REGION_TAG = "region";
    private static final String RELIABLE_TAG = "reliable";

    private SpectralRegion selectedRegion = SpectralRegion.VISIBLE;
    private long lastReceivedGameTime = Long.MIN_VALUE;
    private long lastReceivedSampleStep = Long.MIN_VALUE;
    private long lastObservedSampleStep = Long.MIN_VALUE;
    private SpectralReadoutSample receivedSample = SpectralReadoutSample.zero(true, Long.MIN_VALUE);
    private SpectralReadoutSample committedSample = SpectralReadoutSample.zero(true, Long.MIN_VALUE);
    private boolean reliable = true;

    public SpectrometerBlockEntity(BlockPos pos, BlockState blockState) {
        super(SpectralBlockEntities.SPECTROMETER.get(), pos, blockState);
    }

    public static void tick(Level level, BlockPos pos, SpectrometerBlockEntity spectrometer) {
        if (level.isClientSide) {
            return;
        }

        if (spectrometer.tickSample(level)) {
            spectrometer.syncToClient();
        }
    }

    public void receiveSample(SpectralReadoutSample sample) {
        if (this.level == null || this.level.isClientSide) {
            return;
        }

        this.lastReceivedGameTime = this.level.getGameTime();

        if (this.lastReceivedSampleStep == sample.step()) {
            this.receivedSample = combine(this.receivedSample, sample);
        } else {
            this.lastReceivedSampleStep = sample.step();
            this.receivedSample = sample;
        }
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

    public boolean isOutputReliable() {
        return reliable;
    }

    private boolean tickSample(Level level) {
        if (level.getGameTime() - this.lastReceivedGameTime > SAMPLE_HOLD_TICKS) {
            return commit(SpectralReadoutSample.zero(true, level.getGameTime()));
        }

        if (this.lastReceivedSampleStep == this.lastObservedSampleStep) {
            return false;
        }

        this.lastObservedSampleStep = this.lastReceivedSampleStep;

        if (!this.receivedSample.reliable()) {
            return markUnreliable();
        }

        return commit(this.receivedSample);
    }

    private int getData(int index) {
        if (index >= DATA_SPECTRUM_START && index < DATA_COUNT) {
            int bin = index - DATA_SPECTRUM_START;

            if (bin >= selectedRegion.defaultBins()) {
                return 0;
            }

            double power = committedSample.powerByFrequency().getOrDefault(new FrequencyKey(selectedRegion, bin), 0.0);
            return scalePowerForDisplay(power);
        }

        return switch (index) {
            case DATA_REGION -> selectedRegion.ordinal();
            case DATA_RELIABLE -> reliable ? 1 : 0;
            case DATA_TOTAL_POWER -> scalePowerForDisplay(committedSample.totalPower());
            case DATA_PEAK_BIN -> selectedRegionPeakBin();
            case DATA_REGION_POWER -> scalePowerForDisplay(selectedRegionPower());
            default -> 0;
        };
    }

    private void setData(int index, int value) {
        if (index != DATA_REGION) {
            return;
        }

        SpectralRegion[] regions = SpectralRegion.values();
        SpectralRegion nextRegion = regions[Mth.clamp(value, 0, regions.length - 1)];

        if (nextRegion != selectedRegion) {
            selectedRegion = nextRegion;
            setChanged();
        }
    }

    private double selectedRegionPower() {
        double power = 0.0;

        for (int bin = 0; bin < selectedRegion.defaultBins(); bin++) {
            power += committedSample.powerByFrequency().getOrDefault(new FrequencyKey(selectedRegion, bin), 0.0);
        }

        return power;
    }

    private int selectedRegionPeakBin() {
        double maxPower = 0.0;
        int peakBin = 0;

        for (int bin = 0; bin < selectedRegion.defaultBins(); bin++) {
            double power = committedSample.powerByFrequency().getOrDefault(new FrequencyKey(selectedRegion, bin), 0.0);

            if (power > maxPower) {
                maxPower = power;
                peakBin = bin;
            }
        }

        return peakBin;
    }

    private boolean commit(SpectralReadoutSample sample) {
        boolean changed = !this.reliable || !closeEnough(this.committedSample, sample);
        this.committedSample = sample;
        this.reliable = true;

        if (changed) {
            this.setChanged();
        }

        return changed;
    }

    private boolean markUnreliable() {
        if (!this.reliable) {
            return false;
        }

        this.reliable = false;
        this.committedSample = new SpectralReadoutSample(
                committedSample.powerByFrequency(),
                false,
                committedSample.step()
        );
        this.setChanged();
        return true;
    }

    private static SpectralReadoutSample combine(SpectralReadoutSample left, SpectralReadoutSample right) {
        Map<FrequencyKey, Double> powers = new HashMap<>(left.powerByFrequency());

        for (Map.Entry<FrequencyKey, Double> entry : right.powerByFrequency().entrySet()) {
            powers.merge(entry.getKey(), entry.getValue(), Double::sum);
        }

        return new SpectralReadoutSample(
                powers,
                left.reliable() && right.reliable(),
                Math.max(left.step(), right.step())
        );
    }

    private static boolean closeEnough(SpectralReadoutSample left, SpectralReadoutSample right) {
        if (left.reliable() != right.reliable()) {
            return false;
        }

        if (!closeEnough(left.totalPower(), right.totalPower())) {
            return false;
        }

        java.util.Set<FrequencyKey> keys = new java.util.HashSet<>(left.powerByFrequency().keySet());
        keys.addAll(right.powerByFrequency().keySet());

        for (FrequencyKey key : keys) {
            if (!closeEnough(
                    left.powerByFrequency().getOrDefault(key, 0.0),
                    right.powerByFrequency().getOrDefault(key, 0.0)
            )) {
                return false;
            }
        }

        return true;
    }

    private static boolean closeEnough(double left, double right) {
        return Math.abs(left - right) <= Math.max(EPSILON, Math.max(Math.abs(left), Math.abs(right)) * 1.0E-4);
    }

    private static int scalePowerForDisplay(double power) {
        if (!Double.isFinite(power) || power <= 0.0) {
            return 0;
        }

        return Mth.clamp((int) Math.round(power * POWER_SCALE), 0, 1_000_000_000);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        SpectralRegion[] regions = SpectralRegion.values();
        this.selectedRegion = regions[Mth.clamp(tag.getInt(REGION_TAG), 0, regions.length - 1)];
        this.reliable = !tag.contains(RELIABLE_TAG) || tag.getBoolean(RELIABLE_TAG);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(REGION_TAG, selectedRegion.ordinal());
        tag.putBoolean(RELIABLE_TAG, reliable);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putInt(REGION_TAG, selectedRegion.ordinal());
        tag.putBoolean(RELIABLE_TAG, reliable);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private void syncToClient() {
        if (this.level == null) {
            return;
        }

        BlockState state = this.level.getBlockState(this.worldPosition);
        this.level.sendBlockUpdated(this.worldPosition, state, state, 3);
    }
}
