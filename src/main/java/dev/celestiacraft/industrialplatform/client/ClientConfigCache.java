package dev.celestiacraft.industrialplatform.client;

import dev.celestiacraft.industrialplatform.IndustrialPlatform;
import dev.celestiacraft.industrialplatform.config.PlatformConfig;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side mirror of the last configuration seen for each builder position.
 *
 * Heavy modpacks run aggressive core/optimization mods (deferred chunk updates,
 * rewritten TE sync, experimental model pipelines) that can make the client-side
 * TileEntity lookup unreliable. The hover card and the hologram preview used to
 * die silently in that case even though the block itself was fine. Whenever we
 * CAN read a builder TE (or the config GUI closes with a config) we cache it
 * here; readers fall back to the cache when the TE cannot be resolved.
 *
 * Registered via {@code @Mod.EventBusSubscriber} (see {@link ClientModels}).
 */
@Mod.EventBusSubscriber(modid = IndustrialPlatform.MODID, value = Side.CLIENT)
public final class ClientConfigCache {

    /** Safety valve: builders are few; drop everything rather than grow unbounded. */
    private static final int MAX_ENTRIES = 256;
    private static final Map<BlockPos, PlatformConfig> CACHE = new ConcurrentHashMap<BlockPos, PlatformConfig>();
    /** Positions already asked to the server this session (one request per pos). */
    private static final Map<BlockPos, Boolean> REQUESTED = new ConcurrentHashMap<BlockPos, Boolean>();

    public ClientConfigCache() {
    }

    public static void remember(BlockPos pos, PlatformConfig config) {
        if (pos == null || config == null) {
            return;
        }
        if (CACHE.size() >= MAX_ENTRIES) {
            CACHE.clear();
        }
        CACHE.put(pos.toImmutable(), config.copy());
    }

    /** True exactly once per position per session, to throttle server sync requests. */
    public static boolean markRequested(BlockPos pos) {
        if (pos == null) {
            return false;
        }
        return REQUESTED.putIfAbsent(pos.toImmutable(), Boolean.TRUE) == null;
    }

    /** Server reply landing spot: store the authoritative config. */
    public static void acceptSync(BlockPos pos, String json) {
        PlatformConfig config = dev.celestiacraft.industrialplatform.network.PacketConfigSync.parse(json);
        if (config != null) {
            remember(pos, config);
        }
    }

    /** Cache-only check, for paths that must not read the world. */
    public static boolean known(BlockPos pos) {
        return pos != null && CACHE.containsKey(pos.toImmutable());
    }

    /** Drop a cached builder (its block is gone). */
    public static void forget(BlockPos pos) {
        if (pos != null) {
            CACHE.remove(pos.toImmutable());
        }
    }

    /**
     * Configuration for {@code pos}: live tile entity first (and re-cached),
     * cached copy as fallback. Returns null when neither is available.
     */
    public static PlatformConfig resolve(World world, BlockPos pos) {
        if (world != null && pos != null) {
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof dev.celestiacraft.industrialplatform.tile.TilePlatformBuilder) {
                PlatformConfig live = ((dev.celestiacraft.industrialplatform.tile.TilePlatformBuilder) te).getConfig();
                if (live != null) {
                    remember(pos, live);
                    logOnce(pos, "te-live");
                    return live.copy();
                }
            }
        }
        PlatformConfig cached = cached(pos);
        if (cached != null) {
            logOnce(pos, "cache");
        }
        return cached;
    }

    private static void logOnce(BlockPos pos, String outcome) {
        if (pos == null || LOGGED.contains(pos)) {
            return;
        }
        if (LOGGED.size() < 64) {
            LOGGED.add(pos.toImmutable());
            IndustrialPlatform.LOGGER.info("[IP] config resolve {} -> {}", pos, outcome);
        }
    }

    private static final java.util.Set<BlockPos> LOGGED =
            java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<BlockPos, Boolean>());

    /** Cache-only copy, for paths that already know the TE is unreadable. */
    public static PlatformConfig cached(BlockPos pos) {
        if (pos == null) {
            return null;
        }
        PlatformConfig config = CACHE.get(pos.toImmutable());
        return config != null ? config.copy() : null;
    }

    @SubscribeEvent
    public static void onWorldUnload(WorldEvent.Unload event) {
        if (event.getWorld().isRemote) {
            CACHE.clear();
            REQUESTED.clear();
        }
    }
}
