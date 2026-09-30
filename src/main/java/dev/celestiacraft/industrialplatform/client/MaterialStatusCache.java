package dev.celestiacraft.industrialplatform.client;

import dev.celestiacraft.industrialplatform.config.BlockRef;
import dev.celestiacraft.industrialplatform.network.PacketMaterialStatus;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side cache of the server's material verdict per builder: what is
 * missing once inventory + adjacent containers + the AE network are combined.
 * Entries expire by age (the hover card re-asks while showing), so no
 * world-unload hook is needed.
 */
public final class MaterialStatusCache {

    /** How long a server answer is trusted for display. */
    private static final long FRESH_MS = 5000;
    /** Safety valve: builders are few; drop everything rather than grow unbounded. */
    private static final int MAX_ENTRIES = 256;

    private static final Map<BlockPos, Status> CACHE = new ConcurrentHashMap<BlockPos, Status>();

    private MaterialStatusCache() {
    }

    public static void accept(BlockPos pos, String payload) {
        if (pos == null) {
            return;
        }
        if (CACHE.size() >= MAX_ENTRIES) {
            CACHE.clear();
        }
        CACHE.put(pos.toImmutable(), new Status(System.currentTimeMillis(),
                PacketMaterialStatus.parse(payload)));
    }

    /**
     * The server's combined-source shortfall for this builder, or null when no
     * fresh answer is available (the caller should fall back and re-ask).
     */
    @Nullable
    public static Map<BlockRef, Integer> fresh(BlockPos pos) {
        if (pos == null) {
            return null;
        }
        Status status = CACHE.get(pos);
        if (status == null) {
            return null;
        }
        if (System.currentTimeMillis() - status.stamp > FRESH_MS) {
            CACHE.remove(pos);
            return null;
        }
        return status.missing;
    }

    private static final class Status {
        final long stamp;
        final Map<BlockRef, Integer> missing;

        Status(long stamp, Map<BlockRef, Integer> missing) {
            this.stamp = stamp;
            this.missing = missing;
        }
    }
}
