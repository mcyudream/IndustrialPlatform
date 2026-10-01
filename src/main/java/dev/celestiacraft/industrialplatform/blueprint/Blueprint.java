package dev.celestiacraft.industrialplatform.blueprint;

import dev.celestiacraft.industrialplatform.config.BlockRef;
import dev.celestiacraft.industrialplatform.config.ChannelMode;
import dev.celestiacraft.industrialplatform.config.PlatformConfig;
import dev.celestiacraft.industrialplatform.platform.PlatformRole;

import java.util.Map;

/**
 * A named preset that partially overrides a {@link PlatformConfig}.
 * Only the fields present in the definition are applied.
 */
public final class Blueprint {

    public final String name;
    private final Map<String, Object> data;

    public Blueprint(String name, Map<String, Object> data) {
        this.name = name == null || name.trim().isEmpty() ? "unnamed" : name.trim();
        this.data = data;
    }

    public void applyTo(PlatformConfig cfg) {
        Object nested = data.get("blocks");
        if (nested instanceof Map) {
            applyBlocks(cfg, (Map<?, ?>) nested);
        }
        applyBlocks(cfg, data);

        cfg.layers = intOf(data.get("layers"), cfg.layers);
        cfg.intervalX = intOf(data.get("intervalX"), cfg.intervalX);
        cfg.intervalZ = intOf(data.get("intervalZ"), cfg.intervalZ);
        cfg.countX = intOf(data.get("countX"), cfg.countX);
        cfg.countZ = intOf(data.get("countZ"), cfg.countZ);
        cfg.linkWidth = intOf(data.get("linkWidth"), cfg.linkWidth);
        cfg.channelWidth = intOf(data.get("channelWidth"), cfg.channelWidth);
        cfg.offsetX = intOf(data.get("offsetX"), cfg.offsetX);
        cfg.offsetZ = intOf(data.get("offsetZ"), cfg.offsetZ);
        cfg.offsetY = intOf(data.get("offsetY"), cfg.offsetY);
        Object mode = data.get("channelMode");
        if (mode != null) {
            cfg.channelMode = ChannelMode.parse(String.valueOf(mode));
        }
        cfg.centerMarkOn = boolOf(data.get("centerMark"), cfg.centerMarkOn);
        cfg.replaceExisting = boolOf(data.get("replaceExisting"), cfg.replaceExisting);
        cfg.autoTorches = boolOf(data.get("autoTorches"), cfg.autoTorches);
        cfg.blueprintName = name;
        cfg.clamp();
    }

    private static void applyBlocks(PlatformConfig cfg, Map<?, ?> map) {
        for (PlatformRole role : PlatformRole.values()) {
            Object value = firstOf(map, role.nbtKey(), aliasOf(role));
            if (value == null) {
                continue;
            }
            BlockRef ref = BlockRef.parse(String.valueOf(value));
            if (ref != null) {
                cfg.set(role, ref);
            }
        }
    }

    private static Object firstOf(Map<?, ?> map, String key, String alias) {
        Object value = map.get(key);
        if (value == null && alias != null) {
            value = map.get(alias);
        }
        return value;
    }

    /** Friendlier key names accepted in blueprint files next to the internal ones. */
    private static String aliasOf(PlatformRole role) {
        switch (role) {
            case CHANNEL_LINE: return "channelLine";
            case CENTER_MARK: return "centerMarkBlock";
            default: return null;
        }
    }

    private static int intOf(Object value, int def) {
        return value instanceof Number ? ((Number) value).intValue() : def;
    }

    private static boolean boolOf(Object value, boolean def) {
        return value instanceof Boolean ? (Boolean) value : def;
    }
}
