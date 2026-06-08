package io.github.yoglappland.spectralization.blockentity;

import io.github.yoglappland.spectralization.optics.BeamEnvelope;
import io.github.yoglappland.spectralization.optics.BeamModel;
import io.github.yoglappland.spectralization.optics.BeamPacket;
import io.github.yoglappland.spectralization.optics.CoherenceKind;
import io.github.yoglappland.spectralization.optics.FrequencyKey;
import io.github.yoglappland.spectralization.optics.OpticalSource;
import io.github.yoglappland.spectralization.optics.OutputBeam;
import io.github.yoglappland.spectralization.optics.PlaneWaveComponent;
import io.github.yoglappland.spectralization.optics.SpectralRegion;
import io.github.yoglappland.spectralization.optics.cache.OpticalDirtyKind;
import io.github.yoglappland.spectralization.optics.cache.OpticalTraceCache;
import io.github.yoglappland.spectralization.registry.SpectralBlockEntities;
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
    public static final int DATA_COUNT = 10;
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

    public CreativeLightSourceBlockEntity(BlockPos pos, BlockState blockState) {
        super(SpectralBlockEntities.CREATIVE_LIGHT_SOURCE.get(), pos, blockState);
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
        FrequencyKey frequency = new FrequencyKey(region, bin);
        PlaneWaveComponent component = new PlaneWaveComponent(frequency, power, direction, coherence);
        BeamEnvelope envelope = new BeamEnvelope(
                beamModel,
                radiusMilli / 1000.0,
                divergenceMilli / 1000.0,
                focusDistanceMilli / 1000.0,
                modeM,
                modeN
        );

        return new OutputBeam(direction, BeamPacket.single(component, envelope));
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
    }

    private int getData(int index) {
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

        setChanged();

        if (this.level != null && !this.level.isClientSide) {
            OpticalTraceCache.markChanged(this.level, this.worldPosition, OpticalDirtyKind.SOURCE);
        }
    }
}
