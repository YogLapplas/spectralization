package io.github.yoglappland.spectralization.client.beam;

import io.github.yoglappland.spectralization.network.BeamPathOverlayPayload;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

public final class ClientBeamPathCache {
    private static final long HOLD_TICKS = 40L;
    private static final Map<SegmentKey, ClientSegment> SEGMENTS = new HashMap<>();

    public static void accept(BeamPathOverlayPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.level == null) {
            return;
        }

        long expiresAt = minecraft.level.getGameTime() + HOLD_TICKS;

        for (BeamPathOverlayPayload.Segment segment : payload.segments()) {
            SEGMENTS.put(new SegmentKey(segment.from(), segment.to()), new ClientSegment(segment, expiresAt));
        }
    }

    public static Collection<BeamPathOverlayPayload.Segment> activeSegments() {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.level == null) {
            SEGMENTS.clear();
            return List.of();
        }

        long gameTime = minecraft.level.getGameTime();
        Iterator<ClientSegment> iterator = SEGMENTS.values().iterator();

        while (iterator.hasNext()) {
            if (iterator.next().expiresAt < gameTime) {
                iterator.remove();
            }
        }

        List<BeamPathOverlayPayload.Segment> active = new ArrayList<>(SEGMENTS.size());

        for (ClientSegment segment : SEGMENTS.values()) {
            active.add(segment.segment);
        }

        return active;
    }

    private record SegmentKey(BlockPos from, BlockPos to) {
        private SegmentKey {
            from = from.immutable();
            to = to.immutable();
        }
    }

    private record ClientSegment(BeamPathOverlayPayload.Segment segment, long expiresAt) {
    }

    private ClientBeamPathCache() {
    }
}
