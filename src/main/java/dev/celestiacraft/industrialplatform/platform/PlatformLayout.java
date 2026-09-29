package dev.celestiacraft.industrialplatform.platform;

import dev.celestiacraft.industrialplatform.config.BlockRef;
import dev.celestiacraft.industrialplatform.config.ChannelMode;
import dev.celestiacraft.industrialplatform.config.PlatformConfig;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pure layout math — the single source of truth shared by the hologram preview
 * and the server-side generator, so what you see is exactly what gets built.
 *
 * The footprint is a {@code countX x countZ} grid of platform cells (each cell
 * is {@code (intervalX + 2) x (intervalZ + 2)}), joined by {@code linkWidth}
 * wide connecting strips. The anchor is the CENTER of the chunk the builder
 * sits in, so a rebuild or expansion always grows around the same fixed
 * chunk center — never drifts.
 */
public final class PlatformLayout {

    private final PlatformConfig cfg;
    public final int cellX;
    public final int cellZ;
    public final int link;
    public final int gap;
    public final int countX;
    public final int countZ;
    public final int sizeX;
    public final int sizeZ;
    /** Cell-grid (not raw) coordinates of the builder anchor — the chunk center. */
    public final int centerX;
    public final int centerZ;

    public PlatformLayout(PlatformConfig cfg) {
        this.cfg = cfg;
        this.cellX = cfg.intervalX + 2;
        this.cellZ = cfg.intervalZ + 2;
        this.link = cfg.linkWidth;
        this.countX = cfg.countX;
        this.countZ = cfg.countZ;
        // link strips: linkWidth of pure road plus a 1-wide border rim each side;
        // linkWidth 0 = no strip at all (cells merge seamlessly)
        this.gap = link > 0 ? link + 2 : 0;
        this.sizeX = countX * cellX + (countX - 1) * gap;
        this.sizeZ = countZ * cellZ + (countZ - 1) * gap;
        // anchor is always the CENTER CELL's center — expansion grows outward
        // around the builder, never shifts it
        int ci = (countX - 1) / 2;
        int cj = (countZ - 1) / 2;
        this.centerX = ci * (cellX + gap) + (cellX - 1) / 2;
        this.centerZ = cj * (cellZ + gap) + (cellZ - 1) / 2;
    }

    /** @param layer 0 = bottom layer, {@code cfg.layers - 1} = top surface. */
    public PlatformRole roleAt(int layer, int x, int z) {
        int periodX = cellX + gap;
        int periodZ = cellZ + gap;
        int gi = x / periodX;
        int gj = z / periodZ;
        int px = x % periodX;
        int pz = z % periodZ;
        boolean inCellX = px < cellX;
        boolean inCellZ = pz < cellZ;
        boolean top = layer >= cfg.layers - 1;

        if (inCellX && inCellZ) {
            return cellRole(layer, px, pz, cellX, cellZ, gi, gj, top);
        }

        // connecting strip: the road (linkWidth of link/fill) with a 1-wide
        // border rim on each side — the border does NOT count into linkWidth.
        // the facing rings of the cells are suppressed (see cellRole).
        // at crossings the interior of either strip wins, so roads read as one
        // continuous surface with only a neat corner frame
        boolean stripX = !inCellX;
        boolean stripZ = !inCellZ;
        if (stripX || stripZ) {
            boolean interior = stripX && px > cellX && px < periodX - 1
                    || stripZ && pz > cellZ && pz < periodZ - 1;
            if (!top) {
                return interior ? PlatformRole.FILL : PlatformRole.BORDER;
            }
            if (interior) {
                BlockRef road = cfg.get(PlatformRole.LINK);
                return road.isAir() ? PlatformRole.FILL : PlatformRole.LINK;
            }
            if (!cfg.get(PlatformRole.BORDER).isAir()) {
                return PlatformRole.BORDER;
            }
            BlockRef road = cfg.get(PlatformRole.LINK);
            return road.isAir() ? PlatformRole.FILL : PlatformRole.LINK;
        }
        return PlatformRole.FILL;
    }

    private PlatformRole cellRole(int layer, int lx, int lz, int sx, int sz, int gi, int gj, boolean top) {
        // ring only on the outer boundary of the whole platform — interior edges
        // facing a link strip are bordered by the strip itself (1 wide)
        boolean ring = lx == 0 && gi == 0 || lx == sx - 1 && gi == countX - 1
                || lz == 0 && gj == 0 || lz == sz - 1 && gj == countZ - 1;

        // simplified scheme: edges all use the border material, interior the fill
        if (!top) {
            return ring ? PlatformRole.BORDER : PlatformRole.FILL;
        }
        if (ring) {
            return PlatformRole.BORDER;
        }

        int cx = (sx - 1) / 2;
        int cz = (sz - 1) / 2;
        if (isChannel(lx, lz, cx, cz) && !cfg.get(PlatformRole.CHANNEL_LINE).isAir()) {
            return PlatformRole.CHANNEL_LINE;
        }

        return PlatformRole.FILL;
    }

    /** Whether (lx, lz) belongs to the cell's center group. Even-sized cells center
     * on 4 points (2 on a mixed odd/even axis). Kept OUT of roleAt so the cell's
     * material being "fill" can't suppress the center block. */
    public boolean isCenterCell(int lx, int lz, int sx, int sz) {
        if (sx < 3 || sz < 3) {
            return false;
        }
        int cxMin = sx % 2 == 0 ? sx / 2 - 1 : (sx - 1) / 2;
        int cxMax = sx % 2 == 0 ? sx / 2 : cxMin;
        int czMin = sz % 2 == 0 ? sz / 2 - 1 : (sz - 1) / 2;
        int czMax = sz % 2 == 0 ? sz / 2 : czMin;
        return lx >= cxMin && lx <= cxMax && lz >= czMin && lz <= czMax;
    }

    /**
     * STRAIGHT runs the strip over the full depth; HORSESHOE runs the strip on the
     * north half and wraps around the center as two prongs on the south half.
     */
    private boolean isChannel(int lx, int lz, int cx, int cz) {
        if (cfg.channelWidth <= 0 || cfg.channelMode == ChannelMode.NONE) {
            return false;
        }
        int half = (cfg.channelWidth - 1) / 2;
        int dx = Math.abs(lx - cx);
        if (dx > half) {
            return false;
        }
        if (cfg.channelMode == ChannelMode.STRAIGHT) {
            return true;
        }
        return lz <= cz || dx == half;
    }

    /** Exact per-block material bill for this configuration. */
    public Map<BlockRef, Integer> requiredMaterials() {
        Map<BlockRef, Integer> counts = new LinkedHashMap<BlockRef, Integer>();
        for (int layer = 0; layer < cfg.layers; layer++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    BlockRef ref = cfg.get(roleAt(layer, x, z));
                    if (!ref.isAir()) {
                        Integer old = counts.get(ref);
                        counts.put(ref, old == null ? 1 : old + 1);
                    }
                }
            }
        }
        return counts;
    }
}
