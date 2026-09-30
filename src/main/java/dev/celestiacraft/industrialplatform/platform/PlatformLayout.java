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
        // link strips are pure road — each cell carries its own 1-wide border
        // ring, so an extra strip rim would make the border read as 2 blocks
        // linkWidth 0 = no strip at all (cells merge seamlessly)
        this.gap = link > 0 ? link : 0;
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
            return cellRole(layer, x, z, px, pz, cellX, cellZ, top);
        }

        // connecting strip: pure road of linkWidth — the cells on both sides
        // carry their own border rings, no rim needed here
        if (!inCellX || !inCellZ) {
            BlockRef road = cfg.get(PlatformRole.LINK);
            return road.isAir() ? PlatformRole.FILL : PlatformRole.LINK;
        }
        return PlatformRole.FILL;
    }

    private PlatformRole cellRole(int layer, int absX, int absZ, int lx, int lz, int sx, int sz, boolean top) {
        // every cell is a complete framed platform ("每格是完整平台"): the ring is
        // the cell's own boundary. A ring only on the whole platform's outer edge
        // made interior cells one block wider than boundary cells (14x14 vs 14x13).
        boolean ring = lx == 0 || lx == sx - 1 || lz == 0 || lz == sz - 1;

        // ring alternates border / border2 by absolute position parity, so the
        // two colours swap every block along an edge — easy to count positions
        if (ring) {
            boolean alt = ((absX + absZ) & 1) == 1;
            return alt && !cfg.get(PlatformRole.BORDER2).isAir()
                    ? PlatformRole.BORDER2 : PlatformRole.BORDER;
        }
        // interior uses the fill material
        if (!top) {
            return PlatformRole.FILL;
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

    /**
     * Final material at (x, z) on the TOP surface, including the center overlay
     * that the generator places independently of the role pass. This is the
     * single source of truth for the hologram, the isometric GUI preview and
     * the raw material bill, so they all show exactly what gets built.
     */
    public BlockRef surfaceRefAt(int x, int z) {
        int periodX = cellX + gap;
        int periodZ = cellZ + gap;
        int px = x % periodX;
        int pz = z % periodZ;
        if (px >= 0 && px < cellX && pz >= 0 && pz < cellZ) {
            BlockRef center = cfg.get(PlatformRole.CENTER);
            if (!center.isAir() && isCenterCell(px, pz, cellX, cellZ)) {
                return center;
            }
            BlockRef corner = cfg.get(PlatformRole.CORNER);
            if (!corner.isAir() && isCorner(x, z)) {
                return corner;
            }
        }
        return cfg.get(roleAt(cfg.layers - 1, x, z));
    }

    /**
     * Torch grid for the auto-torch option: a regular footprint-wide grid
     * (spacing 12, centred on the platform) of standing torch spots at
     * surfaceY + 1. Spots already covered by a light-emitting corner marker
     * are skipped. Layout-relative coordinates.
     */
    public java.util.List<int[]> torchSpots() {
        java.util.List<int[]> spots = new java.util.ArrayList<int[]>();
        BlockRef corner = cfg.get(PlatformRole.CORNER);
        int cornerLight = 0;
        try {
            if (!corner.isAir()) {
                cornerLight = corner.block.getStateFromMeta(corner.meta).getLightValue();
            }
        } catch (Throwable ignored) {
        }
        java.util.List<int[]> lights = new java.util.ArrayList<int[]>();
        if (cornerLight > 0) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    if (isCorner(x, z)) {
                        lights.add(new int[]{x, z});
                    }
                }
            }
        }
        int spacing = 12;
        for (int z = centerZ % spacing; z < sizeZ; z += spacing) {
            for (int x = centerX % spacing; x < sizeX; x += spacing) {
                boolean lit = false;
                for (int[] light : lights) {
                    int dx = x - light[0];
                    int dz = z - light[1];
                    if (dx * dx + dz * dz <= 12 * 12) {
                        lit = true;
                        break;
                    }
                }
                if (!lit) {
                    spots.add(new int[]{x, z});
                }
            }
        }
        return spots;
    }

    /**
     * Whether (x, z) is one of the four corners of any cell's EFFECTIVE area
     * (the interior inside the border ring). The corner marker REPLACES the
     * surface block there — nothing is placed on top.
     */
    public boolean isCorner(int x, int z) {
        int periodX = cellX + gap;
        int periodZ = cellZ + gap;
        int px = x % periodX;
        int pz = z % periodZ;
        return px >= 0 && px < cellX && pz >= 0 && pz < cellZ
                && (px == 1 || px == cellX - 2) && (pz == 1 || pz == cellZ - 2);
    }

    /** Exact per-block material bill for this configuration. */
    public Map<BlockRef, Integer> requiredMaterials() {
        Map<BlockRef, Integer> counts = new LinkedHashMap<BlockRef, Integer>();
        for (int layer = 0; layer < cfg.layers; layer++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    BlockRef ref = layer == cfg.layers - 1
                            ? surfaceRefAt(x, z)
                            : cfg.get(roleAt(layer, x, z));
                    if (!ref.isAir()) {
                        Integer old = counts.get(ref);
                        counts.put(ref, old == null ? 1 : old + 1);
                    }
                }
            }
        }
        if (cfg.autoTorches) {
            BlockRef torch = new BlockRef(net.minecraft.init.Blocks.TORCH, 0);
            int torchCount = torchSpots().size();
            if (torchCount > 0) {
                counts.put(torch, torchCount);
            }
        }
        return counts;
    }
}
