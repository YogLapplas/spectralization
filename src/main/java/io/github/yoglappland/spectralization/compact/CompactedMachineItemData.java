package io.github.yoglappland.spectralization.compact;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.optics.BeamEnvelope;
import io.github.yoglappland.spectralization.optics.BeamModel;
import io.github.yoglappland.spectralization.optics.BeamPacket;
import io.github.yoglappland.spectralization.optics.CoherenceKind;
import io.github.yoglappland.spectralization.optics.FrequencyKey;
import io.github.yoglappland.spectralization.optics.OutputBeam;
import io.github.yoglappland.spectralization.optics.PlaneWaveComponent;
import io.github.yoglappland.spectralization.optics.SpectralRegion;
import io.github.yoglappland.spectralization.optics.pump.OpticalPumpSources;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class CompactedMachineItemData {
    private static final String ROOT_KEY = "spectralization_compacted_machine";
    private static final String VERSION_KEY = "version";
    private static final String SIZE_X_KEY = "size_x";
    private static final String SIZE_Y_KEY = "size_y";
    private static final String SIZE_Z_KEY = "size_z";
    private static final String IO_FACES_KEY = "io_faces";
    private static final String IO_PORTS_KEY = "io_ports";
    private static final String TRANSFERS_KEY = "transfers";
    private static final String SOURCES_KEY = "sources";
    private static final String BLOCKS_KEY = "blocks";
    private static final String FACING_KEY = "facing";
    private static final String FACE_COLORS_KEY = "face_colors";
    private static final String FACE_KEY = "face";
    private static final String COLOR_KEY = "color";
    private static final String X_KEY = "x";
    private static final String Y_KEY = "y";
    private static final String Z_KEY = "z";
    private static final String BLOCK_KEY = "block";
    private static final String STATE_KEY = "state";
    private static final String FROM_KEY = "from";
    private static final String TO_KEY = "to";
    private static final String GAIN_KEY = "gain";
    private static final String DIRECTION_KEY = "direction";
    private static final String BEAM_KEY = "beam";
    private static final String COMPONENTS_KEY = "components";
    private static final String ENVELOPE_KEY = "envelope";
    private static final String REGION_KEY = "region";
    private static final String BIN_KEY = "bin";
    private static final String POWER_KEY = "power";
    private static final String COHERENCE_KEY = "coherence";
    private static final String MODEL_KEY = "model";
    private static final String RADIUS_KEY = "radius";
    private static final String WAIST_RADIUS_KEY = "waist_radius";
    private static final String DIVERGENCE_KEY = "divergence";
    private static final String FOCUS_DISTANCE_KEY = "focus_distance";
    private static final String BEAM_QUALITY_KEY = "beam_quality";
    private static final String APERTURE_FILL_KEY = "aperture_fill";
    private static final String SCATTER_KEY = "scatter";
    private static final String MODE_M_KEY = "mode_m";
    private static final String MODE_N_KEY = "mode_n";
    private static final int VERSION = 1;

    public static ItemStack createStack(ServerLevel level, BlockPos frameMin, BlockPos frameMax, BlockPos workMin, BlockPos workMax) {
        ItemStack stack = new ItemStack(Spectralization.COMPACTED_MACHINE.get());
        CompoundTag data = new CompoundTag();
        ListTag blocks = new ListTag();
        List<BoundaryPort> ports = boundaryPorts(level, frameMin, frameMax);
        List<Transfer> transfers = CompactMachineOpticalTransferCompiler.compile(level, frameMin, frameMax, ports);
        List<OutputBeam> sourceOutputs = CompactMachineOpticalTransferCompiler.compileSources(
                level,
                frameMin,
                frameMax,
                workMin,
                workMax,
                ports
        );

        data.putInt(VERSION_KEY, VERSION);
        data.putInt(SIZE_X_KEY, workMax.getX() - workMin.getX() + 1);
        data.putInt(SIZE_Y_KEY, workMax.getY() - workMin.getY() + 1);
        data.putInt(SIZE_Z_KEY, workMax.getZ() - workMin.getZ() + 1);
        data.put(IO_FACES_KEY, writeIoFaces(ports));
        data.put(IO_PORTS_KEY, writeIoPorts(level, ports, workMin));
        data.put(TRANSFERS_KEY, writeTransfers(transfers));
        data.put(SOURCES_KEY, writeSources(sourceOutputs));
        ensureVisualDefaults(data);

        for (BlockPos pos : BlockPos.betweenClosed(workMin, workMax)) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }

            CompoundTag blockTag = new CompoundTag();
            blockTag.putInt(X_KEY, pos.getX() - workMin.getX());
            blockTag.putInt(Y_KEY, pos.getY() - workMin.getY());
            blockTag.putInt(Z_KEY, pos.getZ() - workMin.getZ());
            blockTag.putString(BLOCK_KEY, BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
            blockTag.put(STATE_KEY, NbtUtils.writeBlockState(state));
            blocks.add(blockTag);
        }

        data.put(BLOCKS_KEY, blocks);
        putRoot(stack, data);
        RubyPayloadStats rubyStats = rubyPayloadStats(level, workMin, workMax);
        if (rubyStats.rubyBlocks() > 0) {
            Spectralization.LOGGER.info(
                    "Compacted machine ruby payload in {}: ruby block(s) {}, pumped ruby block(s) {}, max pump rate {}",
                    level.dimension().location(),
                    rubyStats.rubyBlocks(),
                    rubyStats.pumpedRubyBlocks(),
                    rubyStats.maxPumpRate()
            );
        }
        Spectralization.LOGGER.info(
                "Compacted machine optical transfer compiled in {}: {} io port(s), {} transfer(s), {} source output(s)",
                level.dimension().location(),
                ports.size(),
                transfers.size(),
                sourceOutputs.size()
        );
        return stack;
    }

    public static void describeTo(Player player, ItemStack stack) {
        CompoundTag data = root(stack);
        if (!hasFunctionalData(data)) {
            player.displayClientMessage(Component.translatable("screen.spectralization.compact_machine_core.output_empty_data"), false);
            return;
        }

        ListTag blocks = data.getList(BLOCKS_KEY, Tag.TAG_COMPOUND);
        player.displayClientMessage(Component.translatable(
                "screen.spectralization.compact_machine_core.output_summary",
                data.getInt(SIZE_X_KEY),
                data.getInt(SIZE_Y_KEY),
                data.getInt(SIZE_Z_KEY),
                blocks.size()
        ), false);

        for (int index = 0; index < blocks.size(); index++) {
            CompoundTag blockTag = blocks.getCompound(index);
            Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(blockTag.getString(BLOCK_KEY)));
            player.displayClientMessage(Component.translatable(
                    "screen.spectralization.compact_machine_core.output_block_entry",
                    Component.translatable(block.getDescriptionId()),
                    blockTag.getInt(X_KEY),
                    blockTag.getInt(Y_KEY),
                    blockTag.getInt(Z_KEY)
            ), false);
        }
    }

    public static boolean hasData(ItemStack stack) {
        return hasFunctionalData(root(stack));
    }

    public static CompoundTag copyRoot(ItemStack stack) {
        CompoundTag data = root(stack).copy();
        ensureVisualDefaults(data);
        return data;
    }

    public static void putRoot(ItemStack stack, CompoundTag data) {
        CompoundTag copy = data.copy();
        ensureVisualDefaults(copy);
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.put(ROOT_KEY, copy));
    }

    public static ItemStack createStackFromRoot(CompoundTag data) {
        ItemStack stack = new ItemStack(Spectralization.COMPACTED_MACHINE.get());
        putRoot(stack, data);
        return stack;
    }

    public static int sizeX(CompoundTag data) {
        return Math.max(0, data.getInt(SIZE_X_KEY));
    }

    public static int sizeY(CompoundTag data) {
        return Math.max(0, data.getInt(SIZE_Y_KEY));
    }

    public static int sizeZ(CompoundTag data) {
        return Math.max(0, data.getInt(SIZE_Z_KEY));
    }

    public static int blockCount(CompoundTag data) {
        return data.getList(BLOCKS_KEY, Tag.TAG_COMPOUND).size();
    }

    public static int blockTypeCount(CompoundTag data) {
        ListTag blocks = data.getList(BLOCKS_KEY, Tag.TAG_COMPOUND);
        Set<String> blockIds = new java.util.HashSet<>();

        for (int index = 0; index < blocks.size(); index++) {
            String blockId = blocks.getCompound(index).getString(BLOCK_KEY);
            if (!blockId.isBlank()) {
                blockIds.add(blockId);
            }
        }

        return blockIds.size();
    }

    public static List<BlockEntry> blockEntries(CompoundTag data, HolderLookup.Provider registries) {
        ListTag blocks = data.getList(BLOCKS_KEY, Tag.TAG_COMPOUND);
        List<BlockEntry> entries = new ArrayList<>();
        HolderGetter<Block> blockGetter = registries == null ? null : registries.lookupOrThrow(Registries.BLOCK);

        for (int index = 0; index < blocks.size(); index++) {
            CompoundTag blockTag = blocks.getCompound(index);
            BlockState state = readBlockState(blockTag, blockGetter);

            if (state.isAir()) {
                continue;
            }

            entries.add(new BlockEntry(
                    blockTag.getInt(X_KEY),
                    blockTag.getInt(Y_KEY),
                    blockTag.getInt(Z_KEY),
                    state
            ));
        }

        return List.copyOf(entries);
    }

    public static int transferCount(CompoundTag data) {
        return data.getList(TRANSFERS_KEY, Tag.TAG_COMPOUND).size();
    }

    public static int sourceCount(CompoundTag data) {
        return data.getList(SOURCES_KEY, Tag.TAG_COMPOUND).size();
    }

    public static Direction facing(CompoundTag data) {
        Direction facing = Direction.byName(data.getString(FACING_KEY));
        return CompactedMachineTransform.horizontal(facing);
    }

    public static void setFacing(CompoundTag data, Direction facing) {
        data.putString(FACING_KEY, CompactedMachineTransform.horizontal(facing).getName());
    }

    public static CompactedMachineFaceColor faceColor(CompoundTag data, Direction face) {
        ListTag colors = data.getList(FACE_COLORS_KEY, Tag.TAG_COMPOUND);
        CompactedMachineFaceColor fallback = defaultFaceColor(face);

        for (int index = 0; index < colors.size(); index++) {
            CompoundTag colorTag = colors.getCompound(index);
            Direction taggedFace = Direction.byName(colorTag.getString(FACE_KEY));
            if (taggedFace == face) {
                return CompactedMachineFaceColor.byName(colorTag.getString(COLOR_KEY), fallback);
            }
        }

        return fallback;
    }

    public static void setFaceColor(CompoundTag data, Direction face, CompactedMachineFaceColor color) {
        ListTag oldColors = data.getList(FACE_COLORS_KEY, Tag.TAG_COMPOUND);
        ListTag newColors = new ListTag();
        boolean written = false;

        for (int index = 0; index < oldColors.size(); index++) {
            CompoundTag oldColorTag = oldColors.getCompound(index);
            Direction taggedFace = Direction.byName(oldColorTag.getString(FACE_KEY));
            if (taggedFace == null) {
                continue;
            }

            CompoundTag newColorTag = oldColorTag.copy();
            if (taggedFace == face) {
                newColorTag.putString(COLOR_KEY, color.serializedName());
                written = true;
            }
            newColors.add(newColorTag);
        }

        if (!written) {
            CompoundTag colorTag = new CompoundTag();
            colorTag.putString(FACE_KEY, face.getName());
            colorTag.putString(COLOR_KEY, color.serializedName());
            newColors.add(colorTag);
        }

        data.put(FACE_COLORS_KEY, newColors);
    }

    public static Set<Direction> ioFaces(CompoundTag data) {
        ListTag faces = data.getList(IO_FACES_KEY, Tag.TAG_STRING);
        EnumSet<Direction> directions = EnumSet.noneOf(Direction.class);

        for (int index = 0; index < faces.size(); index++) {
            Direction direction = Direction.byName(faces.getString(index));
            if (direction != null) {
                directions.add(direction);
            }
        }

        return directions.isEmpty() ? Set.of() : Set.copyOf(directions);
    }

    public static List<IoPortEntry> ioPortEntries(CompoundTag data, HolderLookup.Provider registries) {
        ListTag portTags = data.getList(IO_PORTS_KEY, Tag.TAG_COMPOUND);
        List<IoPortEntry> entries = new ArrayList<>();
        HolderGetter<Block> blockGetter = registries == null ? null : registries.lookupOrThrow(Registries.BLOCK);

        for (int index = 0; index < portTags.size(); index++) {
            CompoundTag portTag = portTags.getCompound(index);
            Direction face = Direction.byName(portTag.getString(FACE_KEY));
            if (face == null) {
                continue;
            }

            entries.add(new IoPortEntry(
                    face,
                    portTag.getInt(X_KEY),
                    portTag.getInt(Y_KEY),
                    portTag.getInt(Z_KEY),
                    readBlockState(portTag, blockGetter)
            ));
        }

        if (!entries.isEmpty()) {
            return List.copyOf(entries);
        }

        return fallbackIoPortEntries(data);
    }

    public static List<Transfer> transfers(CompoundTag data) {
        ListTag transferTags = data.getList(TRANSFERS_KEY, Tag.TAG_COMPOUND);
        List<Transfer> transfers = new ArrayList<>();

        for (int index = 0; index < transferTags.size(); index++) {
            CompoundTag transferTag = transferTags.getCompound(index);
            Direction from = Direction.byName(transferTag.getString(FROM_KEY));
            Direction to = Direction.byName(transferTag.getString(TO_KEY));
            double gain = transferTag.getDouble(GAIN_KEY);

            if (from != null && to != null && Double.isFinite(gain) && gain > 0.0D) {
                transfers.add(new Transfer(from, to, gain));
            }
        }

        return List.copyOf(transfers);
    }

    public static List<OutputBeam> sources(CompoundTag data) {
        ListTag sourceTags = data.getList(SOURCES_KEY, Tag.TAG_COMPOUND);
        List<OutputBeam> sources = new ArrayList<>();

        for (int index = 0; index < sourceTags.size(); index++) {
            CompoundTag sourceTag = sourceTags.getCompound(index);
            Direction direction = Direction.byName(sourceTag.getString(DIRECTION_KEY));

            if (direction == null) {
                continue;
            }

            BeamPacket beam = readBeam(sourceTag.getCompound(BEAM_KEY), direction);

            if (!beam.isEmpty()) {
                sources.add(new OutputBeam(direction, beam.withDirection(direction)));
            }
        }

        return List.copyOf(sources);
    }

    private static CompoundTag root(ItemStack stack) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = data.copyTag();
        return tag.getCompound(ROOT_KEY);
    }

    private static boolean hasFunctionalData(CompoundTag data) {
        return data.contains(VERSION_KEY, Tag.TAG_INT)
                || data.contains(BLOCKS_KEY, Tag.TAG_LIST)
                || data.contains(TRANSFERS_KEY, Tag.TAG_LIST)
                || data.contains(SOURCES_KEY, Tag.TAG_LIST);
    }

    private static RubyPayloadStats rubyPayloadStats(ServerLevel level, BlockPos workMin, BlockPos workMax) {
        int rubyBlocks = 0;
        int pumpedRubyBlocks = 0;
        int maxPumpRate = 0;

        for (BlockPos pos : BlockPos.betweenClosed(workMin, workMax)) {
            BlockPos immutablePos = pos.immutable();
            BlockState state = level.getBlockState(immutablePos);
            if (!state.is(Spectralization.RUBY_BLOCK.get())) {
                continue;
            }

            rubyBlocks++;
            int pumpRate = OpticalPumpSources.adjacentPumpRate(level, immutablePos);
            if (pumpRate > 0) {
                pumpedRubyBlocks++;
            }
            maxPumpRate = Math.max(maxPumpRate, pumpRate);
        }

        return new RubyPayloadStats(rubyBlocks, pumpedRubyBlocks, maxPumpRate);
    }

    private static void ensureVisualDefaults(CompoundTag data) {
        Direction facing = Direction.byName(data.getString(FACING_KEY));
        if (facing == null || facing.getAxis().isVertical()) {
            setFacing(data, Direction.NORTH);
        }

        for (Direction face : Direction.values()) {
            setFaceColor(data, face, faceColor(data, face));
        }
    }

    private static CompactedMachineFaceColor defaultFaceColor(Direction face) {
        return switch (face) {
            case NORTH -> CompactedMachineFaceColor.BLUE;
            case SOUTH -> CompactedMachineFaceColor.GREEN;
            case EAST -> CompactedMachineFaceColor.CYAN;
            case WEST -> CompactedMachineFaceColor.PURPLE;
            case UP -> CompactedMachineFaceColor.YELLOW;
            case DOWN -> CompactedMachineFaceColor.RED;
        };
    }

    private static List<BoundaryPort> boundaryPorts(ServerLevel level, BlockPos frameMin, BlockPos frameMax) {
        List<BoundaryPort> ports = new ArrayList<>();

        for (BlockPos pos : BlockPos.betweenClosed(frameMin, frameMax)) {
            BlockState state = level.getBlockState(pos);
            if (!state.is(Spectralization.COMPACT_MACHINE_LIGHT_IO_PORT.get())) {
                continue;
            }

            Direction face = singleSurfaceFace(pos, frameMin, frameMax);
            if (face != null) {
                ports.add(new BoundaryPort(face, pos.immutable()));
            }
        }

        return List.copyOf(ports);
    }

    private static ListTag writeIoFaces(List<BoundaryPort> ports) {
        EnumSet<Direction> faces = EnumSet.noneOf(Direction.class);

        for (BoundaryPort port : ports) {
            faces.add(port.face());
        }

        ListTag tag = new ListTag();
        for (Direction face : faces) {
            tag.add(StringTag.valueOf(face.getName()));
        }

        return tag;
    }

    private static ListTag writeIoPorts(ServerLevel level, List<BoundaryPort> ports, BlockPos workMin) {
        ListTag portTags = new ListTag();

        for (BoundaryPort port : ports) {
            BlockState state = level.getBlockState(port.pos());
            CompoundTag portTag = new CompoundTag();
            portTag.putString(FACE_KEY, port.face().getName());
            portTag.putInt(X_KEY, port.pos().getX() - workMin.getX());
            portTag.putInt(Y_KEY, port.pos().getY() - workMin.getY());
            portTag.putInt(Z_KEY, port.pos().getZ() - workMin.getZ());
            portTag.putString(BLOCK_KEY, BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
            portTag.put(STATE_KEY, NbtUtils.writeBlockState(state));
            portTags.add(portTag);
        }

        return portTags;
    }

    private static ListTag writeTransfers(List<Transfer> transfers) {
        ListTag transferTags = new ListTag();

        for (Transfer transfer : transfers) {
            CompoundTag transferTag = new CompoundTag();
            transferTag.putString(FROM_KEY, transfer.fromFace().getName());
            transferTag.putString(TO_KEY, transfer.toFace().getName());
            transferTag.putDouble(GAIN_KEY, transfer.gain());
            transferTags.add(transferTag);
        }

        return transferTags;
    }

    private static ListTag writeSources(List<OutputBeam> sources) {
        ListTag sourceTags = new ListTag();

        for (OutputBeam source : sources) {
            if (source.beam().isEmpty()) {
                continue;
            }

            CompoundTag sourceTag = new CompoundTag();
            sourceTag.putString(DIRECTION_KEY, source.outgoingDirection().getName());
            sourceTag.put(BEAM_KEY, writeBeam(source.beam().withDirection(source.outgoingDirection())));
            sourceTags.add(sourceTag);
        }

        return sourceTags;
    }

    private static CompoundTag writeBeam(BeamPacket beam) {
        CompoundTag beamTag = new CompoundTag();
        ListTag componentTags = new ListTag();

        for (PlaneWaveComponent component : beam.components()) {
            if (component.power() <= 0.0D) {
                continue;
            }

            CompoundTag componentTag = new CompoundTag();
            componentTag.putString(REGION_KEY, component.frequency().region().name());
            componentTag.putInt(BIN_KEY, component.frequency().bin());
            componentTag.putDouble(POWER_KEY, component.power());
            componentTag.putString(DIRECTION_KEY, component.direction().getName());
            componentTag.putString(COHERENCE_KEY, component.coherence().name());
            componentTags.add(componentTag);
        }

        beamTag.put(COMPONENTS_KEY, componentTags);
        beamTag.put(ENVELOPE_KEY, writeEnvelope(beam.envelope()));
        return beamTag;
    }

    private static CompoundTag writeEnvelope(BeamEnvelope envelope) {
        CompoundTag envelopeTag = new CompoundTag();
        envelopeTag.putString(MODEL_KEY, envelope.model().name());
        envelopeTag.putDouble(RADIUS_KEY, envelope.radius());
        envelopeTag.putDouble(WAIST_RADIUS_KEY, envelope.waistRadius());
        envelopeTag.putDouble(DIVERGENCE_KEY, envelope.divergence());
        envelopeTag.putDouble(FOCUS_DISTANCE_KEY, envelope.focusDistance());
        envelopeTag.putDouble(BEAM_QUALITY_KEY, envelope.beamQuality());
        envelopeTag.putDouble(APERTURE_FILL_KEY, envelope.apertureFill());
        envelopeTag.putDouble(SCATTER_KEY, envelope.scatter());
        envelopeTag.putInt(MODE_M_KEY, envelope.modeM());
        envelopeTag.putInt(MODE_N_KEY, envelope.modeN());
        return envelopeTag;
    }

    private static BeamPacket readBeam(CompoundTag beamTag, Direction fallbackDirection) {
        ListTag componentTags = beamTag.getList(COMPONENTS_KEY, Tag.TAG_COMPOUND);
        List<PlaneWaveComponent> components = new ArrayList<>();

        for (int index = 0; index < componentTags.size(); index++) {
            CompoundTag componentTag = componentTags.getCompound(index);
            SpectralRegion region = enumValue(
                    SpectralRegion.class,
                    componentTag.getString(REGION_KEY),
                    SpectralRegion.VISIBLE
            );
            int bin = Math.max(0, componentTag.getInt(BIN_KEY));
            double power = componentTag.getDouble(POWER_KEY);
            Direction direction = Direction.byName(componentTag.getString(DIRECTION_KEY));
            CoherenceKind coherence = enumValue(
                    CoherenceKind.class,
                    componentTag.getString(COHERENCE_KEY),
                    CoherenceKind.INCOHERENT
            );

            if (direction == null) {
                direction = fallbackDirection;
            }

            if (!Double.isFinite(power) || power <= 0.0D) {
                continue;
            }

            components.add(new PlaneWaveComponent(new FrequencyKey(region, bin), power, direction, coherence));
        }

        List<PlaneWaveComponent> normalized = normalizeComponents(components);
        if (normalized.isEmpty()) {
            return BeamPacket.empty(BeamEnvelope.DEFAULT_COLLIMATED);
        }

        return new BeamPacket(normalized, readEnvelope(beamTag.getCompound(ENVELOPE_KEY)));
    }

    private static List<IoPortEntry> fallbackIoPortEntries(CompoundTag data) {
        Set<Direction> faces = ioFaces(data);
        if (faces.isEmpty()) {
            return List.of();
        }

        int sizeX = sizeX(data);
        int sizeY = sizeY(data);
        int sizeZ = sizeZ(data);
        List<IoPortEntry> entries = new ArrayList<>();
        BlockState fallbackState = Spectralization.COMPACT_MACHINE_LIGHT_IO_PORT.get().defaultBlockState();

        for (Direction face : faces) {
            entries.add(new IoPortEntry(
                    face,
                    fallbackX(face, sizeX),
                    fallbackY(face, sizeY),
                    fallbackZ(face, sizeZ),
                    fallbackState
            ));
        }

        return List.copyOf(entries);
    }

    private static int fallbackX(Direction face, int sizeX) {
        return switch (face) {
            case WEST -> -1;
            case EAST -> Math.max(0, sizeX);
            default -> Math.max(0, sizeX - 1) / 2;
        };
    }

    private static int fallbackY(Direction face, int sizeY) {
        return switch (face) {
            case DOWN -> -1;
            case UP -> Math.max(0, sizeY);
            default -> Math.max(0, sizeY - 1) / 2;
        };
    }

    private static int fallbackZ(Direction face, int sizeZ) {
        return switch (face) {
            case NORTH -> -1;
            case SOUTH -> Math.max(0, sizeZ);
            default -> Math.max(0, sizeZ - 1) / 2;
        };
    }

    private static BlockState readBlockState(CompoundTag blockTag, HolderGetter<Block> blockGetter) {
        if (blockGetter != null && blockTag.contains(STATE_KEY, Tag.TAG_COMPOUND)) {
            try {
                return NbtUtils.readBlockState(blockGetter, blockTag.getCompound(STATE_KEY));
            } catch (RuntimeException exception) {
                Spectralization.LOGGER.warn(
                        "Could not read compacted machine block state {}, falling back to block id",
                        blockTag.getCompound(STATE_KEY),
                        exception
                );
            }
        }

        String blockId = blockTag.getString(BLOCK_KEY);
        if (blockId.isBlank()) {
            return Blocks.AIR.defaultBlockState();
        }

        try {
            return BuiltInRegistries.BLOCK.get(ResourceLocation.parse(blockId)).defaultBlockState();
        } catch (RuntimeException exception) {
            Spectralization.LOGGER.warn("Could not read compacted machine block id {}", blockId, exception);
            return Blocks.AIR.defaultBlockState();
        }
    }

    private static BeamEnvelope readEnvelope(CompoundTag envelopeTag) {
        if (envelopeTag.isEmpty()) {
            return BeamEnvelope.DEFAULT_COLLIMATED;
        }

        BeamModel model = enumValue(
                BeamModel.class,
                envelopeTag.getString(MODEL_KEY),
                BeamEnvelope.DEFAULT_COLLIMATED.model()
        );

        try {
            return new BeamEnvelope(
                    model,
                    doubleOr(envelopeTag, RADIUS_KEY, BeamEnvelope.DEFAULT_COLLIMATED.radius()),
                    doubleOr(envelopeTag, WAIST_RADIUS_KEY, BeamEnvelope.DEFAULT_COLLIMATED.waistRadius()),
                    doubleOr(envelopeTag, DIVERGENCE_KEY, BeamEnvelope.DEFAULT_COLLIMATED.divergence()),
                    doubleOr(envelopeTag, FOCUS_DISTANCE_KEY, BeamEnvelope.DEFAULT_COLLIMATED.focusDistance()),
                    doubleOr(envelopeTag, BEAM_QUALITY_KEY, BeamEnvelope.DEFAULT_COLLIMATED.beamQuality()),
                    doubleOr(envelopeTag, APERTURE_FILL_KEY, BeamEnvelope.DEFAULT_COLLIMATED.apertureFill()),
                    doubleOr(envelopeTag, SCATTER_KEY, BeamEnvelope.DEFAULT_COLLIMATED.scatter()),
                    intOr(envelopeTag, MODE_M_KEY, BeamEnvelope.DEFAULT_COLLIMATED.modeM()),
                    intOr(envelopeTag, MODE_N_KEY, BeamEnvelope.DEFAULT_COLLIMATED.modeN())
            );
        } catch (IllegalArgumentException ignored) {
            return BeamEnvelope.DEFAULT_COLLIMATED;
        }
    }

    private static double doubleOr(CompoundTag tag, String key, double fallback) {
        return tag.contains(key, Tag.TAG_DOUBLE) ? tag.getDouble(key) : fallback;
    }

    private static int intOr(CompoundTag tag, String key, int fallback) {
        return tag.contains(key, Tag.TAG_INT) ? tag.getInt(key) : fallback;
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String name, T fallback) {
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static List<PlaneWaveComponent> normalizeComponents(Collection<PlaneWaveComponent> rawComponents) {
        Map<ComponentKey, Double> powerByKey = new LinkedHashMap<>();

        for (PlaneWaveComponent component : rawComponents) {
            if (component.power() <= 0.0D) {
                continue;
            }

            ComponentKey key = new ComponentKey(component.frequency(), component.direction(), component.coherence());
            powerByKey.merge(key, component.power(), Double::sum);
        }

        return powerByKey.entrySet().stream()
                .map(entry -> entry.getKey().component(entry.getValue()))
                .sorted(Comparator.comparingDouble(PlaneWaveComponent::power).reversed())
                .limit(BeamPacket.MAX_COMPONENTS)
                .toList();
    }

    private static Direction singleSurfaceFace(BlockPos pos, BlockPos frameMin, BlockPos frameMax) {
        Direction face = null;
        int faceCount = 0;

        if (pos.getX() == frameMin.getX()) {
            face = Direction.WEST;
            faceCount++;
        }
        if (pos.getX() == frameMax.getX()) {
            face = Direction.EAST;
            faceCount++;
        }
        if (pos.getY() == frameMin.getY()) {
            face = Direction.DOWN;
            faceCount++;
        }
        if (pos.getY() == frameMax.getY()) {
            face = Direction.UP;
            faceCount++;
        }
        if (pos.getZ() == frameMin.getZ()) {
            face = Direction.NORTH;
            faceCount++;
        }
        if (pos.getZ() == frameMax.getZ()) {
            face = Direction.SOUTH;
            faceCount++;
        }

        return faceCount == 1 ? face : null;
    }

    public record BoundaryPort(Direction face, BlockPos pos) {
        public BoundaryPort {
            Objects.requireNonNull(face, "face");
            Objects.requireNonNull(pos, "pos");
            pos = pos.immutable();
        }
    }

    public record Transfer(Direction fromFace, Direction toFace, double gain) {
        public Transfer {
            Objects.requireNonNull(fromFace, "fromFace");
            Objects.requireNonNull(toFace, "toFace");

            if (!Double.isFinite(gain) || gain <= 0.0D) {
                throw new IllegalArgumentException("Compact machine optical transfer gain must be positive and finite");
            }
        }
    }

    public record BlockEntry(int x, int y, int z, BlockState state) {
        public BlockEntry {
            Objects.requireNonNull(state, "state");
        }

        public BlockPos pos() {
            return new BlockPos(x, y, z);
        }
    }

    public record IoPortEntry(Direction face, int x, int y, int z, BlockState state) {
        public IoPortEntry {
            Objects.requireNonNull(face, "face");
            Objects.requireNonNull(state, "state");
        }

        public BlockPos pos() {
            return new BlockPos(x, y, z);
        }
    }

    private record ComponentKey(FrequencyKey frequency, Direction direction, CoherenceKind coherence) {
        private PlaneWaveComponent component(double power) {
            return new PlaneWaveComponent(frequency, power, direction, coherence);
        }
    }

    private record RubyPayloadStats(int rubyBlocks, int pumpedRubyBlocks, int maxPumpRate) {
    }

    private CompactedMachineItemData() {
    }
}
