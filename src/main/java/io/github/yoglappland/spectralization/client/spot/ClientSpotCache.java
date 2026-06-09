package io.github.yoglappland.spectralization.client.spot;

import io.github.yoglappland.spectralization.optics.SpotRecord;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ClientSpotCache {
    private static final int SPOT_TTL_TICKS = 60;
    private static final long SPOT_TTL_MILLIS = 4_000L;
    private static final Map<SpotKey, ClientSpot> SPOTS = new HashMap<>();

    public static void accept(SpotRecord spot) {
        if (Minecraft.getInstance().level == null) {
            return;
        }

        SpotKey key = new SpotKey(spot.pos(), spot.face());
        if (!spot.visible()) {
            SPOTS.remove(key);
            return;
        }

        long expiresAt = Minecraft.getInstance().level.getGameTime() + SPOT_TTL_TICKS;
        long expiresAtMillis = System.currentTimeMillis() + SPOT_TTL_MILLIS;
        SPOTS.put(key, new ClientSpot(spot, expiresAt, expiresAtMillis));
    }

    public static Collection<SpotRecord> activeSpots() {
        if (Minecraft.getInstance().level == null) {
            SPOTS.clear();
            return List.of();
        }

        long gameTime = Minecraft.getInstance().level.getGameTime();
        long nowMillis = System.currentTimeMillis();
        Iterator<ClientSpot> iterator = SPOTS.values().iterator();

        while (iterator.hasNext()) {
            ClientSpot spot = iterator.next();

            if (spot.expiresAtGameTime < gameTime || spot.expiresAtMillis < nowMillis) {
                iterator.remove();
            }
        }

        List<SpotRecord> activeSpots = new ArrayList<>(SPOTS.size());

        for (ClientSpot spot : SPOTS.values()) {
            activeSpots.add(spot.spot);
        }

        return activeSpots;
    }

    private record SpotKey(BlockPos pos, Direction face) {
    }

    private record ClientSpot(SpotRecord spot, long expiresAtGameTime, long expiresAtMillis) {
    }

    private ClientSpotCache() {
    }
}
