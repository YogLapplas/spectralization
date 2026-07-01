package io.github.yoglappland.spectralization.blockentity;

import io.github.yoglappland.spectralization.energy.SpectralEnergyStorage;
import io.github.yoglappland.spectralization.diagnostics.SpectralDiagnostics;
import io.github.yoglappland.spectralization.optics.cache.OpticalDirtyKind;
import io.github.yoglappland.spectralization.optics.cache.OpticalTraceCache;
import io.github.yoglappland.spectralization.registry.SpectralBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

public class FiberLaserBlockEntity extends BlockEntity {
    public static final int DATA_ENERGY_PER_TICK = 0;
    public static final int DATA_MAX_ENERGY_PER_TICK = 1;
    public static final int DATA_GAIN_X1000 = 2;
    public static final int DATA_PUMP_PERCENT = 3;
    public static final int DATA_ACTIVE = 4;
    public static final int DATA_ENERGY_STORED = 5;
    public static final int DATA_ENERGY_CAPACITY = 6;
    public static final int DATA_ACTUAL_ENERGY_PER_TICK = 7;
    public static final int DATA_COUNT = 8;

    public static final int MAX_ENERGY_PER_TICK = 32000;
    public static final int DEFAULT_ENERGY_PER_TICK = 4000;
    public static final int ENERGY_CAPACITY = 320000;
    public static final int MAX_ENERGY_INPUT = 32000;
    public static final double GAIN_MATERIAL_WEIGHT = 4.0D;

    private static final String ENERGY_PER_TICK_TAG = "EnergyPerTick";
    private static final String ENERGY_TAG = "Energy";

    private int energyPerTick = DEFAULT_ENERGY_PER_TICK;
    private int actualEnergyPerTick = 0;
    private int lastScheduledGainX1000 = 1000;
    private final SpectralEnergyStorage energy = new SpectralEnergyStorage(
            ENERGY_CAPACITY,
            MAX_ENERGY_INPUT,
            0,
            this::onEnergyChanged
    );
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return getData(index);
        }

        @Override
        public void set(int index, int value) {
            if (index == DATA_ENERGY_PER_TICK) {
                setEnergyPerTick(value);
            }
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public FiberLaserBlockEntity(BlockPos pos, BlockState blockState) {
        super(SpectralBlockEntities.FIBER_LASER.get(), pos, blockState);
    }

    public static void tick(Level level, BlockPos pos, FiberLaserBlockEntity laser) {
        if (level.isClientSide) {
            return;
        }

        laser.tickEnergyPump();
    }

    public ContainerData createDataAccess() {
        return data;
    }

    @Nullable
    public IEnergyStorage getEnergyStorage(@Nullable Direction side) {
        return energy;
    }

    public int energyPerTick() {
        return energyPerTick;
    }

    public int pumpPercent() {
        return Math.round(actualEnergyPerTick * 100.0F / MAX_ENERGY_PER_TICK);
    }

    public int gainX1000() {
        return scheduledGainX1000();
    }

    public int targetGainX1000() {
        return gainX1000ForEnergy(energyPerTick);
    }

    public double scheduledCoherentBaseGain() {
        return scheduledGainX1000() / 1000.0D;
    }

    public static double maximumScheduledCoherentBaseGain() {
        return gainX1000ForEnergy(MAX_ENERGY_PER_TICK) / 1000.0D;
    }

    public int energyStored() {
        return energy.getEnergyStored();
    }

    public int energyCapacity() {
        return energy.getMaxEnergyStored();
    }

    public int actualEnergyPerTick() {
        return actualEnergyPerTick;
    }

    public boolean active() {
        return scheduledEnergyPerTick() > 0 && scheduledGainX1000() > 1000;
    }

    private static int gainX1000ForEnergy(int energyPerTick) {
        if (energyPerTick <= 0) {
            return 1000;
        }

        double normalized = Math.max(0.0, Math.min(1.0, energyPerTick / (double) MAX_ENERGY_PER_TICK));
        return 1000 + (int) Math.round(4000.0 * Math.sqrt(normalized));
    }

    public void setEnergyPercent(int percent) {
        int clamped = Mth.clamp(percent, 0, 100);
        setEnergyPerTick(Math.round(MAX_ENERGY_PER_TICK * clamped / 100.0F));
    }

    public void setEnergyPerTick(int value) {
        int clamped = Mth.clamp(value, 0, MAX_ENERGY_PER_TICK);

        if (this.energyPerTick == clamped) {
            return;
        }

        this.energyPerTick = clamped;
        setChanged();
        syncScheduledGain();
    }

    private int getData(int index) {
        return switch (index) {
            case DATA_ENERGY_PER_TICK -> energyPerTick();
            case DATA_MAX_ENERGY_PER_TICK -> MAX_ENERGY_PER_TICK;
            case DATA_GAIN_X1000 -> gainX1000();
            case DATA_PUMP_PERCENT -> pumpPercent();
            case DATA_ACTIVE -> active() ? 1 : 0;
            case DATA_ENERGY_STORED -> energyStored();
            case DATA_ENERGY_CAPACITY -> energyCapacity();
            case DATA_ACTUAL_ENERGY_PER_TICK -> actualEnergyPerTick();
            default -> 0;
        };
    }

    private void tickEnergyPump() {
        int consumed = consumePumpEnergy();

        if (consumed == actualEnergyPerTick) {
            syncScheduledGain();
            return;
        }

        actualEnergyPerTick = consumed;
        syncScheduledGain();
        setChanged();
    }

    private int consumePumpEnergy() {
        if (energyPerTick <= 0) {
            return 0;
        }

        int consumed = Math.min(energyPerTick, energy.getEnergyStored());

        if (consumed > 0) {
            energy.setEnergyStored(energy.getEnergyStored() - consumed);
        }

        return consumed;
    }

    private void onEnergyChanged() {
        setChanged();
        syncScheduledGain();
    }

    private int scheduledEnergyPerTick() {
        if (energyPerTick <= 0) {
            return 0;
        }

        int availablePumpEnergy = Math.max(actualEnergyPerTick, energy.getEnergyStored());
        return Math.min(energyPerTick, availablePumpEnergy);
    }

    private int scheduledGainX1000() {
        return gainX1000ForEnergy(scheduledEnergyPerTick());
    }

    private void syncScheduledGain() {
        int nextGain = scheduledGainX1000();

        if (nextGain == lastScheduledGainX1000) {
            return;
        }

        int previousGain = lastScheduledGainX1000;
        lastScheduledGainX1000 = nextGain;
        logScheduledGainChanged(previousGain, nextGain);
        markOpticalParameterChanged();
    }

    private void logScheduledGainChanged(int previousGain, int nextGain) {
        if (this.level == null || this.level.isClientSide) {
            return;
        }

        SpectralDiagnostics.transition(this.level, SpectralDiagnostics.Subsystem.FIBER, "fiber_laser_gain_changed")
                .pos("pos", this.worldPosition)
                .field("previous_gain_x1000", previousGain)
                .field("scheduled_gain_x1000", nextGain)
                .field("target_energy_per_tick", energyPerTick)
                .field("scheduled_energy_per_tick", scheduledEnergyPerTick())
                .field("actual_energy_per_tick", actualEnergyPerTick)
                .field("energy_stored", energy.getEnergyStored())
                .write();
    }

    private void markOpticalParameterChanged() {
        if (this.level == null) {
            return;
        }

        if (!this.level.isClientSide) {
            OpticalTraceCache.markChanged(this.level, this.worldPosition, OpticalDirtyKind.PARAMETER);
        }

        this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);

        if (tag.contains(ENERGY_PER_TICK_TAG)) {
            this.energyPerTick = Mth.clamp(tag.getInt(ENERGY_PER_TICK_TAG), 0, MAX_ENERGY_PER_TICK);
        }

        if (tag.contains(ENERGY_TAG)) {
            this.energy.setEnergyStored(tag.getInt(ENERGY_TAG));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(ENERGY_PER_TICK_TAG, this.energyPerTick);
        tag.putInt(ENERGY_TAG, this.energy.getEnergyStored());
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        this.saveAdditional(tag, registries);
        return tag;
    }
}
