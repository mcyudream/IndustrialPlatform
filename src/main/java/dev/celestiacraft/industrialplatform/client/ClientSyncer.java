package dev.celestiacraft.industrialplatform.client;

import dev.celestiacraft.industrialplatform.IndustrialPlatform;
import dev.celestiacraft.industrialplatform.network.PacketConfigRequest;
import dev.celestiacraft.industrialplatform.network.PacketMaterialRequest;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side helper that asks the server for a builder's configuration when
 * neither the tile entity nor the local cache can answer. One request per
 * position per session; the reply lands in {@link ClientConfigCache}.
 *
 * Also carries the throttled "combined material verdict" requests for the
 * hover card ({@link #requestMaterialStatus}); those repeat while hovering
 * because container and AE amounts change over time.
 */
public final class ClientSyncer {

    private static final long MATERIAL_ASK_INTERVAL_MS = 1500;
    private static final Map<BlockPos, Long> MATERIAL_ASKED = new ConcurrentHashMap<BlockPos, Long>();

    private ClientSyncer() {
    }

    public static void maybeRequest(BlockPos pos) {
        if (pos == null || !ClientConfigCache.markRequested(pos)) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null) {
            return;
        }
        IndustrialPlatform.NETWORK.sendToServer(new PacketConfigRequest(pos));
    }

    /** At most one combined-source material request per position per interval. */
    public static void requestMaterialStatus(BlockPos pos) {
        if (pos == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = MATERIAL_ASKED.get(pos);
        if (last != null && now - last < MATERIAL_ASK_INTERVAL_MS) {
            return;
        }
        MATERIAL_ASKED.put(pos, now);
        if (MATERIAL_ASKED.size() > 256) {
            MATERIAL_ASKED.clear();
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) {
            return;
        }
        IndustrialPlatform.NETWORK.sendToServer(new PacketMaterialRequest(pos));
    }

    /** Server -> client reply landing point; reschedules onto the main thread. */
    public static void acceptSync(final BlockPos pos, final String json) {
        IndustrialPlatform.LOGGER.info("[IP] server sync landed for {}", pos);
        Minecraft.getMinecraft().addScheduledTask(() -> ClientConfigCache.acceptSync(pos, json));
    }

    /** Server -> client material verdict landing point. */
    public static void acceptMaterialStatus(final BlockPos pos, final String payload) {
        Minecraft.getMinecraft().addScheduledTask(() -> MaterialStatusCache.accept(pos, payload));
    }
}
