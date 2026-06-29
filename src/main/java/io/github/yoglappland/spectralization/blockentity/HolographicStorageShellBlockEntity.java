package io.github.yoglappland.spectralization.blockentity;

import io.github.yoglappland.spectralization.block.HolographicStorageShellBlock;
import io.github.yoglappland.spectralization.block.HolographicStorageMultiblock;
import io.github.yoglappland.spectralization.heat.PhotothermalAbsorberProfile;
import io.github.yoglappland.spectralization.heat.PhotothermalCouplingResult;
import io.github.yoglappland.spectralization.heat.PhotothermalReadoutSample;
import io.github.yoglappland.spectralization.heat.PhotothermalReceiver;
import io.github.yoglappland.spectralization.optics.metasurface.MetamaterialTemplateData;
import io.github.yoglappland.spectralization.registry.SpectralBlockEntities;
import io.github.yoglappland.spectralization.storage.HolographicStorageEntry;
import io.github.yoglappland.spectralization.storage.PhotoinducedReactionRecipe;
import io.github.yoglappland.spectralization.tag.SpectralItemTags;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

public class HolographicStorageShellBlockEntity extends BlockEntity implements PhotothermalReceiver {
    public static final int CAPACITY = 4096;

    private static final long SAMPLE_HOLD_TICKS = 1L;
    private static final double LIGHT_EPSILON = 1.0E-9;
    private static final int CHANNEL_ENGRAVING_TICKS = 40;
    private static final int CHANNEL_RED_FLASH_TICKS = 20;
    private static final int CHANNEL_GLOW_NONE = 0;
    private static final int CHANNEL_GLOW_GREEN = 1;
    private static final int CHANNEL_GLOW_RED = 2;
    private static final String STORAGE_TAG = "Storage";
    private static final String STACK_STORAGE_TAG = "spectralization_holographic_storage_shell";
    private static final String TEMPLATE_TAG = "Template";
    private static final String COUNT_TAG = "Count";
    private static final String EMPTY_TAG = "Empty";
    private static final String REACTION_PROGRESS_TAG = "PhotoinducedProgress";
    private static final String OPTICAL_POWER_TAG = "PhotoinducedOpticalPower";
    private static final String COHERENT_POWER_TAG = "PhotoinducedCoherentPower";
    private static final String PHOTOINDUCED_ACTIVE_TAG = "PhotoinducedActive";
    private static final String CHANNEL_ENGRAVING_PROGRESS_TAG = "ChannelEngravingProgress";
    private static final String CHANNEL_GLOW_TAG = "ChannelGlow";
    private static final String CHANNEL_RED_FLASH_TAG = "ChannelRedFlash";

    private ItemStack template = ItemStack.EMPTY;
    private int count;
    private int reactionProgress;
    private int channelEngravingProgress;
    private int channelGlow;
    private int channelRedFlashTicks;
    private boolean photoinducedActive;
    private long lastReceivedGameTime = Long.MIN_VALUE;
    private long lastReceivedSampleStep = Long.MIN_VALUE;
    private long lastObservedSampleStep = Long.MIN_VALUE;
    private double receivedPowerThisStep = 0.0;
    private double receivedCoherentPowerThisStep = 0.0;
    private double committedPower = 0.0;
    private double committedCoherentPower = 0.0;
    private boolean receivedReliableThisStep = false;
    private final IItemHandler itemHandler = new ShellItemHandler();

    public HolographicStorageShellBlockEntity(BlockPos pos, BlockState blockState) {
        super(SpectralBlockEntities.HOLOGRAPHIC_STORAGE_SHELL.get(), pos, blockState);
    }

    public static void tick(Level level, BlockPos pos, HolographicStorageShellBlockEntity shell) {
        if (level.isClientSide) {
            return;
        }

        shell.tickOpticalSample(level);
        shell.tickPhotoinducedReaction(level);
        shell.tickChannelEngraving(level);
    }

    public boolean isEmpty() {
        return template.isEmpty() || count <= 0;
    }

    public boolean hasStoredItem() {
        return !isEmpty();
    }

    public ItemStack templateStack() {
        return isEmpty() ? ItemStack.EMPTY : template.copyWithCount(1);
    }

    public ItemStack getStackForDisplay() {
        return templateStack();
    }

    public int storedCount() {
        return isEmpty() ? 0 : count;
    }

    public HolographicStorageEntry entry() {
        return new HolographicStorageEntry(templateStack(), storedCount());
    }

    public boolean canAccept(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        return isEmpty() || ItemStack.isSameItemSameComponents(template, stack);
    }

    public int insert(ItemStack stack, int maxAmount, boolean simulate) {
        if (stack.isEmpty() || maxAmount <= 0 || !canAccept(stack)) {
            return 0;
        }

        int inserted = Math.min(Math.min(stack.getCount(), maxAmount), CAPACITY - storedCount());
        if (inserted <= 0) {
            return 0;
        }

        if (!simulate) {
            if (isEmpty()) {
                template = stack.copyWithCount(1);
            }
            count += inserted;
            setChangedAndSync();
        }

        return inserted;
    }

    public ItemStack extract(int maxAmount, boolean simulate) {
        if (isEmpty() || maxAmount <= 0) {
            return ItemStack.EMPTY;
        }

        int extractedCount = Math.min(Math.min(maxAmount, template.getMaxStackSize()), count);
        if (extractedCount <= 0) {
            return ItemStack.EMPTY;
        }

        ItemStack extracted = template.copyWithCount(extractedCount);
        if (!simulate) {
            count -= extractedCount;
            if (count <= 0) {
                clearStorage();
            }
            setChangedAndSync();
        }

        return extracted;
    }

    public IItemHandler getItems(@Nullable net.minecraft.core.Direction side) {
        return itemHandler;
    }

    @Override
    public PhotothermalAbsorberProfile photothermalAbsorberProfile() {
        return PhotothermalAbsorberProfile.DEFAULT_MACHINE_FACE;
    }

    @Override
    public PhotothermalCouplingResult photothermalCoupling() {
        return PhotothermalCouplingResult.zero();
    }

    @Override
    public void receivePhotothermalSample(PhotothermalReadoutSample sample) {
        if (level == null || level.isClientSide) {
            return;
        }

        lastReceivedGameTime = level.getGameTime();

        if (lastReceivedSampleStep == sample.step()) {
            receivedPowerThisStep += sample.power();
            receivedCoherentPowerThisStep += sample.coherentPower();
            receivedReliableThisStep &= sample.reliable();
        } else {
            lastReceivedSampleStep = sample.step();
            receivedPowerThisStep = sample.power();
            receivedCoherentPowerThisStep = sample.coherentPower();
            receivedReliableThisStep = sample.reliable();
        }
    }

    public boolean isLitForPhotoinducedOutput() {
        if (committedPower > LIGHT_EPSILON) {
            return true;
        }

        return level != null
                && level.getGameTime() - lastReceivedGameTime <= SAMPLE_HOLD_TICKS
                && receivedPowerThisStep > LIGHT_EPSILON;
    }

    public boolean isPhotoinducedActive() {
        return photoinducedActive;
    }

    public int channelGlow() {
        return channelGlow;
    }

    public boolean hasChannelGlow() {
        return channelGlow != CHANNEL_GLOW_NONE;
    }

    public boolean hasGreenChannelGlow() {
        return channelGlow == CHANNEL_GLOW_GREEN;
    }

    public boolean hasRedChannelGlow() {
        return channelGlow == CHANNEL_GLOW_RED;
    }

    public void loadFromStack(ItemStack stack, HolderLookup.Provider registries) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag root = data.copyTag();
        if (!root.contains(STACK_STORAGE_TAG, Tag.TAG_COMPOUND)) {
            clearStorage();
            clearRuntimeOpticalState();
            return;
        }

        readStorage(root.getCompound(STACK_STORAGE_TAG), registries);
        clearRuntimeOpticalState();
        setChanged();
    }

    public void saveToStack(ItemStack stack, HolderLookup.Provider registries) {
        if (isEmpty()) {
            clearSavedStorage(stack);
            return;
        }

        stack.set(DataComponents.MAX_STACK_SIZE, 1);
        stack.remove(DataComponents.BLOCK_ENTITY_DATA);
        CustomData.update(DataComponents.CUSTOM_DATA, stack, root -> {
            CompoundTag storageTag = new CompoundTag();
            writeStorage(storageTag, registries);
            root.put(STACK_STORAGE_TAG, storageTag);
        });
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.getBoolean(EMPTY_TAG)) {
            clearStorage();
        } else if (tag.contains(STORAGE_TAG, Tag.TAG_COMPOUND)) {
            readStorage(tag.getCompound(STORAGE_TAG), registries);
        } else {
            clearStorage();
        }

        if (isEmpty()) {
            clearRuntimeOpticalState();
        } else {
            reactionProgress = Math.max(0, tag.getInt(REACTION_PROGRESS_TAG));
            committedPower = Math.max(0.0, tag.getDouble(OPTICAL_POWER_TAG));
            committedCoherentPower = Math.max(0.0, tag.getDouble(COHERENT_POWER_TAG));
            photoinducedActive = tag.getBoolean(PHOTOINDUCED_ACTIVE_TAG);
        }
        readChannelRuntimeState(tag);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);

        if (isEmpty()) {
            tag.putBoolean(EMPTY_TAG, true);
            tag.remove(STORAGE_TAG);
            tag.remove(REACTION_PROGRESS_TAG);
            tag.remove(OPTICAL_POWER_TAG);
            tag.remove(COHERENT_POWER_TAG);
            tag.remove(PHOTOINDUCED_ACTIVE_TAG);
            writeChannelRuntimeState(tag);
            return;
        }

        tag.putInt(REACTION_PROGRESS_TAG, reactionProgress);
        tag.putDouble(OPTICAL_POWER_TAG, committedPower);
        tag.putDouble(COHERENT_POWER_TAG, committedCoherentPower);
        tag.putBoolean(PHOTOINDUCED_ACTIVE_TAG, photoinducedActive);
        writeChannelRuntimeState(tag);
        tag.putBoolean(EMPTY_TAG, false);
        CompoundTag storageTag = new CompoundTag();
        writeStorage(storageTag, registries);
        tag.put(STORAGE_TAG, storageTag);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    private void readStorage(CompoundTag tag, HolderLookup.Provider registries) {
        ItemStack parsedTemplate = ItemStack.parseOptional(registries, tag.getCompound(TEMPLATE_TAG));
        int parsedCount = tag.getInt(COUNT_TAG);

        if (parsedTemplate.isEmpty() || parsedCount <= 0) {
            clearStorage();
            return;
        }

        template = parsedTemplate.copyWithCount(1);
        count = Math.min(parsedCount, CAPACITY);
    }

    private void writeStorage(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put(TEMPLATE_TAG, template.copyWithCount(1).saveOptional(registries));
        tag.putInt(COUNT_TAG, storedCount());
    }

    public static boolean hasSavedStorage(ItemStack stack) {
        return storageTagFromStack(stack).isPresent();
    }

    public static void clearSavedStorage(ItemStack stack) {
        stack.remove(DataComponents.CUSTOM_DATA);
        stack.remove(DataComponents.BLOCK_ENTITY_DATA);
        stack.set(DataComponents.MAX_STACK_SIZE, stack.getItem().getDefaultMaxStackSize());
    }

    public static Optional<HolographicStorageEntry> entryFromStack(
            ItemStack stack,
            HolderLookup.Provider registries
    ) {
        Optional<CompoundTag> maybeStorageTag = storageTagFromStack(stack);
        if (maybeStorageTag.isEmpty()) {
            return Optional.empty();
        }

        CompoundTag storageTag = maybeStorageTag.get();
        ItemStack template = ItemStack.parseOptional(registries, storageTag.getCompound(TEMPLATE_TAG));
        int count = storageTag.getInt(COUNT_TAG);

        if (template.isEmpty() || count <= 0) {
            return Optional.empty();
        }

        return Optional.of(new HolographicStorageEntry(template.copyWithCount(1), Math.min(count, CAPACITY)));
    }

    private static Optional<CompoundTag> storageTagFromStack(ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }

        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag root = data.copyTag();
        if (!root.contains(STACK_STORAGE_TAG, Tag.TAG_COMPOUND)) {
            return Optional.empty();
        }

        CompoundTag storageTag = root.getCompound(STACK_STORAGE_TAG);
        if (storageTag.getInt(COUNT_TAG) <= 0 || !storageTag.contains(TEMPLATE_TAG, Tag.TAG_COMPOUND)) {
            return Optional.empty();
        }

        return Optional.of(storageTag);
    }

    private void clearStorage() {
        template = ItemStack.EMPTY;
        count = 0;
        resetReactionProgress();
        setPhotoinducedActive(false);
    }

    private void tickOpticalSample(Level level) {
        if (level.getGameTime() - lastReceivedGameTime > SAMPLE_HOLD_TICKS) {
            commitOpticalSample(0.0, 0.0);
            return;
        }

        if (lastReceivedSampleStep == lastObservedSampleStep) {
            return;
        }

        lastObservedSampleStep = lastReceivedSampleStep;

        if (receivedReliableThisStep) {
            commitOpticalSample(receivedPowerThisStep, receivedCoherentPowerThisStep);
        } else {
            commitOpticalSample(0.0, 0.0);
        }
    }

    private void commitOpticalSample(double power, double coherentPower) {
        double safePower = Double.isFinite(power) ? Math.max(0.0, power) : 0.0;
        double safeCoherentPower = Double.isFinite(coherentPower) ? Math.max(0.0, coherentPower) : 0.0;

        if (Math.abs(committedPower - safePower) > 1.0E-6
                || Math.abs(committedCoherentPower - safeCoherentPower) > 1.0E-6) {
            committedPower = safePower;
            committedCoherentPower = safeCoherentPower;
            setChanged();
        }
    }

    private void tickPhotoinducedReaction(Level level) {
        if (isStableShell() || isEmpty()) {
            setPhotoinducedActive(false);
            resetReactionProgress();
            return;
        }

        Optional<PhotoinducedReactionRecipe> maybeRecipe = PhotoinducedReactionRecipe.find(templateStack());
        if (maybeRecipe.isEmpty()) {
            setPhotoinducedActive(false);
            resetReactionProgress();
            return;
        }

        PhotoinducedReactionRecipe recipe = maybeRecipe.get();
        List<PhotoinducedOutputCandidate> candidates = outputCandidates(recipe.weightedResults(templateStack()));

        if (committedCoherentPower + LIGHT_EPSILON < recipe.requiredCoherentPower() || candidates.isEmpty()) {
            setPhotoinducedActive(false);
            resetReactionProgress();
            return;
        }

        setPhotoinducedActive(true);
        reactionProgress++;
        if (reactionProgress < recipe.processTicks()) {
            setChanged();
            return;
        }

        completePhotoinducedReaction(level, recipe);
        reactionProgress = 0;
        setChanged();
    }

    private void clearRuntimeOpticalState() {
        reactionProgress = 0;
        photoinducedActive = false;
        lastReceivedGameTime = Long.MIN_VALUE;
        lastReceivedSampleStep = Long.MIN_VALUE;
        lastObservedSampleStep = Long.MIN_VALUE;
        receivedPowerThisStep = 0.0;
        receivedCoherentPowerThisStep = 0.0;
        committedPower = 0.0;
        committedCoherentPower = 0.0;
        receivedReliableThisStep = false;
        channelEngravingProgress = 0;
        channelGlow = CHANNEL_GLOW_NONE;
        channelRedFlashTicks = 0;
    }

    private void completePhotoinducedReaction(Level level, PhotoinducedReactionRecipe recipe) {
        List<PhotoinducedOutputCandidate> candidates = outputCandidates(recipe.weightedResults(templateStack()));
        if (candidates.isEmpty() || extract(1, true).isEmpty()) {
            return;
        }

        PhotoinducedOutputCandidate candidate = chooseWeightedCandidate(candidates, level.random);
        if (candidate.result().isEmpty()) {
            extract(1, false);
            return;
        }

        if (candidate.shell() == null || candidate.shell().insert(candidate.result(), 1, false) <= 0) {
            return;
        }

        extract(1, false);
    }

    private List<PhotoinducedOutputCandidate> outputCandidates(List<PhotoinducedReactionRecipe.WeightedResult> results) {
        if (level == null || results.isEmpty()) {
            return List.of();
        }

        List<PhotoinducedOutputCandidate> candidates = new ArrayList<>();
        for (PhotoinducedReactionRecipe.WeightedResult result : results) {
            if (result.stack().isEmpty()) {
                candidates.add(new PhotoinducedOutputCandidate(null, ItemStack.EMPTY, result.weight()));
                continue;
            }

            ItemStack stack = result.stack();
            List<HolographicStorageShellBlockEntity> shells = validOutputShells(stack);
            if (shells.isEmpty()) {
                continue;
            }

            int baseWeight = result.weight() / shells.size();
            int remainder = result.weight() % shells.size();
            for (int index = 0; index < shells.size(); index++) {
                int weight = baseWeight + (index < remainder ? 1 : 0);
                if (weight > 0) {
                    candidates.add(new PhotoinducedOutputCandidate(shells.get(index), stack.copyWithCount(1), weight));
                }
            }
        }

        return candidates;
    }

    private List<HolographicStorageShellBlockEntity> validOutputShells(ItemStack result) {
        if (level == null || result.isEmpty()) {
            return List.of();
        }

        List<HolographicStorageShellBlockEntity> shells = new ArrayList<>();
        for (Direction direction : Direction.values()) {
            BlockPos outputPos = worldPosition.relative(direction);

            if (level.getBlockEntity(outputPos) instanceof HolographicStorageShellBlockEntity shell
                    && shell != this
                    && !shell.isLitForPhotoinducedOutput()
                    && shell.insert(result, 1, true) > 0) {
                shells.add(shell);
            }
        }
        return shells;
    }

    private static PhotoinducedOutputCandidate chooseWeightedCandidate(
            List<PhotoinducedOutputCandidate> candidates,
            net.minecraft.util.RandomSource random
    ) {
        int totalWeight = 0;
        for (PhotoinducedOutputCandidate candidate : candidates) {
            totalWeight += Math.max(0, candidate.weight());
        }

        if (totalWeight <= 0) {
            return candidates.getFirst();
        }

        int roll = random.nextInt(totalWeight);
        for (PhotoinducedOutputCandidate candidate : candidates) {
            roll -= Math.max(0, candidate.weight());
            if (roll < 0) {
                return candidate;
            }
        }

        return candidates.getLast();
    }

    private void tickChannelEngraving(Level level) {
        if (channelRedFlashTicks > 0) {
            channelRedFlashTicks--;
        }

        if (!isStableShell()) {
            resetChannelEngravingState(false);
            return;
        }

        if (isEmpty()) {
            showRedFlashOrReset();
            return;
        }

        ItemStack stack = templateStack();
        if (!isMetamaterialTemplate(stack)) {
            resetChannelEngravingState(true);
            return;
        }

        Optional<ChannelProgrammingTarget> maybeTarget = channelProgrammingTarget(level);
        if (maybeTarget.isEmpty()) {
            resetChannelEngravingState(true);
            return;
        }

        MetamaterialTemplateData data = MetamaterialTemplateData.fromStack(stack);
        if (maybeTarget.get().core().hasRegisteredChannel(data.vector().channelIndex())) {
            setChannelGlow(CHANNEL_GLOW_RED);
            resetChannelEngravingProgress();
            pushRegisteredChannelCard(level, stack);
            return;
        }

        setChannelGlow(CHANNEL_GLOW_GREEN);
        if (committedCoherentPower <= LIGHT_EPSILON) {
            resetChannelEngravingProgress();
            return;
        }

        channelEngravingProgress++;
        if (channelEngravingProgress < CHANNEL_ENGRAVING_TICKS) {
            setChanged();
            return;
        }

        completeChannelEngraving(level, maybeTarget.get(), data, stack);
    }

    private void completeChannelEngraving(
            Level level,
            ChannelProgrammingTarget target,
            MetamaterialTemplateData data,
            ItemStack stack
    ) {
        target.core().registerChannel(data.vector().channelIndex());
        if (pushToAdjacentContainer(level, stack.copyWithCount(1), false).isEmpty()) {
            extract(1, false);
        }
        channelEngravingProgress = 0;
        channelRedFlashTicks = CHANNEL_RED_FLASH_TICKS;
        setChannelGlow(CHANNEL_GLOW_RED);
        setChangedAndSync();
    }

    private void pushRegisteredChannelCard(Level level, ItemStack stack) {
        if (!canPushToAdjacentContainer(level, stack)) {
            return;
        }

        if (!pushToAdjacentContainer(level, stack.copyWithCount(1), false).isEmpty()) {
            return;
        }

        extract(1, false);
        channelRedFlashTicks = CHANNEL_RED_FLASH_TICKS;
        setChannelGlow(CHANNEL_GLOW_RED);
        setChangedAndSync();
    }

    private Optional<ChannelProgrammingTarget> channelProgrammingTarget(Level level) {
        Optional<BlockPos> maybeCorePos = HolographicStorageMultiblock.findCoreForMember(level, worldPosition);
        if (maybeCorePos.isEmpty()) {
            return Optional.empty();
        }

        BlockPos corePos = maybeCorePos.get();
        HolographicStorageMultiblock.StructureReport report = HolographicStorageMultiblock.scan(level, corePos);
        if (report.error()
                || !report.positions().contains(worldPosition.immutable())
                || !hasCoreAlignedStoragePath(report, corePos)) {
            return Optional.empty();
        }

        if (!(level.getBlockEntity(corePos) instanceof HolographicStorageMainCoreBlockEntity core)) {
            return Optional.empty();
        }

        return Optional.of(new ChannelProgrammingTarget(core));
    }

    private boolean hasCoreAlignedStoragePath(
            HolographicStorageMultiblock.StructureReport report,
            BlockPos corePos
    ) {
        int stepX = Integer.compare(corePos.getX(), worldPosition.getX());
        int stepY = Integer.compare(corePos.getY(), worldPosition.getY());
        int stepZ = Integer.compare(corePos.getZ(), worldPosition.getZ());
        int axisCount = (stepX == 0 ? 0 : 1) + (stepY == 0 ? 0 : 1) + (stepZ == 0 ? 0 : 1);
        if (axisCount != 1) {
            return false;
        }

        BlockPos cursor = worldPosition.offset(stepX, stepY, stepZ);
        while (!cursor.equals(corePos)) {
            if (!report.positions().contains(cursor.immutable())) {
                return false;
            }
            cursor = cursor.offset(stepX, stepY, stepZ);
        }

        return true;
    }

    private boolean canPushToAdjacentContainer(Level level, ItemStack stack) {
        return pushToAdjacentContainer(level, stack, true).isEmpty();
    }

    private ItemStack pushToAdjacentContainer(Level level, ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack remaining = stack.copy();
        for (Direction direction : Direction.values()) {
            BlockPos targetPos = worldPosition.relative(direction);
            if (level.getBlockEntity(targetPos) instanceof HolographicStorageShellBlockEntity) {
                continue;
            }

            IItemHandler handler = level.getCapability(
                    Capabilities.ItemHandler.BLOCK,
                    targetPos,
                    direction.getOpposite()
            );
            if (handler == null) {
                continue;
            }

            remaining = insertIntoHandler(handler, remaining, simulate);
            if (remaining.isEmpty()) {
                return ItemStack.EMPTY;
            }
        }

        return remaining;
    }

    private static ItemStack insertIntoHandler(IItemHandler handler, ItemStack stack, boolean simulate) {
        ItemStack remaining = stack.copy();
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            remaining = handler.insertItem(slot, remaining, simulate);
            if (remaining.isEmpty()) {
                return ItemStack.EMPTY;
            }
        }
        return remaining;
    }

    private void showRedFlashOrReset() {
        resetChannelEngravingProgress();
        if (channelRedFlashTicks > 0) {
            setChannelGlow(CHANNEL_GLOW_RED);
        } else {
            setChannelGlow(CHANNEL_GLOW_NONE);
        }
    }

    private void resetChannelEngravingState(boolean keepRedFlash) {
        resetChannelEngravingProgress();
        if (keepRedFlash && channelRedFlashTicks > 0) {
            setChannelGlow(CHANNEL_GLOW_RED);
        } else {
            channelRedFlashTicks = 0;
            setChannelGlow(CHANNEL_GLOW_NONE);
        }
    }

    private void resetChannelEngravingProgress() {
        if (channelEngravingProgress != 0) {
            channelEngravingProgress = 0;
            setChanged();
        }
    }

    private void setChannelGlow(int glow) {
        int safeGlow = Math.max(CHANNEL_GLOW_NONE, Math.min(CHANNEL_GLOW_RED, glow));
        if (channelGlow == safeGlow) {
            return;
        }

        channelGlow = safeGlow;
        setChangedAndSync();
    }

    private void readChannelRuntimeState(CompoundTag tag) {
        channelEngravingProgress = Math.max(0, tag.getInt(CHANNEL_ENGRAVING_PROGRESS_TAG));
        channelGlow = Math.max(CHANNEL_GLOW_NONE, Math.min(CHANNEL_GLOW_RED, tag.getInt(CHANNEL_GLOW_TAG)));
        channelRedFlashTicks = Math.max(0, tag.getInt(CHANNEL_RED_FLASH_TAG));
    }

    private void writeChannelRuntimeState(CompoundTag tag) {
        if (channelEngravingProgress > 0) {
            tag.putInt(CHANNEL_ENGRAVING_PROGRESS_TAG, channelEngravingProgress);
        } else {
            tag.remove(CHANNEL_ENGRAVING_PROGRESS_TAG);
        }

        if (channelGlow != CHANNEL_GLOW_NONE || channelRedFlashTicks > 0) {
            tag.putInt(CHANNEL_GLOW_TAG, channelGlow);
            tag.putInt(CHANNEL_RED_FLASH_TAG, channelRedFlashTicks);
        } else {
            tag.remove(CHANNEL_GLOW_TAG);
            tag.remove(CHANNEL_RED_FLASH_TAG);
        }
    }

    private boolean isStableShell() {
        return getBlockState().getBlock() instanceof HolographicStorageShellBlock shell && shell.stable();
    }

    private static boolean isMetamaterialTemplate(ItemStack stack) {
        return stack.is(SpectralItemTags.STANDARD_METAMATERIAL_TEMPLATE)
                || stack.is(SpectralItemTags.CUSTOM_METAMATERIAL_TEMPLATE);
    }

    private void resetReactionProgress() {
        if (reactionProgress != 0) {
            reactionProgress = 0;
            setChanged();
        }
    }

    private void setPhotoinducedActive(boolean active) {
        if (photoinducedActive == active) {
            return;
        }

        photoinducedActive = active;
        setChangedAndSync();
    }

    private void setChangedAndSync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS);
        }
    }

    private record PhotoinducedOutputCandidate(
            @Nullable
            HolographicStorageShellBlockEntity shell,
            ItemStack result,
            int weight
    ) {
    }

    private record ChannelProgrammingTarget(HolographicStorageMainCoreBlockEntity core) {
    }

    private final class ShellItemHandler implements IItemHandler {
        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            if (slot != 0 || isEmpty()) {
                return ItemStack.EMPTY;
            }

            return template.copyWithCount(Math.min(count, template.getMaxStackSize()));
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot != 0 || stack.isEmpty()) {
                return stack;
            }

            int inserted = insert(stack, stack.getCount(), simulate);
            if (inserted <= 0) {
                return stack;
            }

            ItemStack remainder = stack.copy();
            remainder.shrink(inserted);
            return remainder;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot != 0) {
                return ItemStack.EMPTY;
            }

            return extract(amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot == 0 ? CAPACITY : 0;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == 0 && canAccept(stack);
        }
    }
}
