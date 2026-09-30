package dev.celestiacraft.industrialplatform.platform;

import dev.celestiacraft.industrialplatform.block.PlatformBuilderBlock;
import dev.celestiacraft.industrialplatform.config.BlockRef;
import dev.celestiacraft.industrialplatform.config.PlatformConfig;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Server-side generation planning, driven by the same {@link PlatformLayout} as
 * the hologram preview. A cell that already holds exactly the right block is
 * left untouched and not charged; anything else inside the footprint is replaced
 * — except bedrock, tile entities (chests etc.) and other platform builders,
 * which are always protected. The returned plan is ordered for the build
 * animation: bottom-up, rippling outward from the builder.
 */
public final class PlatformGenerator {

    /** One block to place, at its exact final position. */
    public static final class Placement {
        public final BlockPos pos;
        public final IBlockState state;

        Placement(BlockPos pos, IBlockState state) {
            this.pos = pos;
            this.state = state;
        }
    }

    /** Everything to place, ordered bottom-up and center-out for the build animation. */
    public static final class BuildPlan {
        public final List<Placement> placements;
        public final int matched;
        public final int skipped;

        BuildPlan(List<Placement> placements, int matched, int skipped) {
            this.placements = placements;
            this.matched = matched;
            this.skipped = skipped;
        }
    }

    private PlatformGenerator() {
    }

    /** Chunk center the layout is anchored on, in cell-grid coordinates. */
    public static int anchorCoord(BlockPos anchor, int axisCoord, int center) {
        // anchor must land on the fixed CENTER of the builder's chunk so a rebuild
        // or expansion always grows around the same point, never drifts
        int chunk = anchor.getX() >> 4;
        if (axisCoord == anchor.getZ()) {
            chunk = anchor.getZ() >> 4;
        }
        return chunk * 16 + 8;
    }

    public static BuildPlan plan(World world, BlockPos anchor, PlatformConfig cfg) {
        cfg.clamp();
        PlatformLayout layout = new PlatformLayout(cfg);

        int anchorX = (anchor.getX() >> 4) * 16 + 8 + cfg.offsetX;
        int anchorZ = (anchor.getZ() >> 4) * 16 + 8 + cfg.offsetZ;
        int x0 = anchorX - layout.centerX;
        int z0 = anchorZ - layout.centerZ;
        int y0 = Math.max(0, Math.min(255, anchor.getY() + cfg.offsetY));

        List<Placement> placements = new ArrayList<Placement>();
        int matched = 0;
        int skipped = 0;

        for (int layer = 0; layer < cfg.layers; layer++) {
            int y = y0 + layer;
            if (y > 255) {
                break;
            }
            for (int z = 0; z < layout.sizeZ; z++) {
                for (int x = 0; x < layout.sizeX; x++) {
                    BlockRef ref = layer == cfg.layers - 1
                            ? layout.surfaceRefAt(x, z)
                            : cfg.get(layout.roleAt(layer, x, z));
                    if (ref.isAir()) {
                        continue;
                    }
                    BlockPos pos = new BlockPos(x0 + x, y, z0 + z);
                    IBlockState target = ref.block.getStateFromMeta(ref.meta);
                    if (world.getBlockState(pos) == target) {
                        matched++; // already exactly what we need — free
                        continue;
                    }
                    if (!cfg.replaceExisting && !isSoftReplaceable(world.getBlockState(pos))) {
                        skipped++; // keep mode: existing solid block stays
                        continue;
                    }
                    if (!canReplace(world, pos)) {
                        skipped++;
                        continue;
                    }
                    placements.add(new Placement(pos, target));
                }
            }
        }

        // auto-torch option: standing torches on a light-based grid, one block
        // above the surface, only into air / replaceable blocks
        if (cfg.autoTorches) {
            int torchY = y0 + cfg.layers;
            if (torchY <= 255) {
                IBlockState torch = Blocks.TORCH.getDefaultState();
                for (int[] spot : layout.torchSpots()) {
                    BlockPos pos = new BlockPos(x0 + spot[0], torchY, z0 + spot[1]);
                    if (world.getBlockState(pos).getBlock() == Blocks.TORCH) {
                        matched++;
                    } else if (isSoftReplaceable(world.getBlockState(pos))) {
                        placements.add(new Placement(pos, torch));
                    } else {
                        skipped++;
                    }
                }
            }
        }

        // animation order: bottom-up, and on each layer a ripple from the chunk center
        final int cx = anchorX;
        final int cz = anchorZ;
        placements.sort(new Comparator<Placement>() {
            @Override
            public int compare(Placement a, Placement b) {
                int layerDelta = Integer.compare(a.pos.getY(), b.pos.getY());
                if (layerDelta != 0) {
                    return layerDelta;
                }
                int da = dist2(a.pos, cx, cz);
                int db = dist2(b.pos, cx, cz);
                if (da != db) {
                    return Integer.compare(da, db);
                }
                return a.pos.compareTo(b.pos);
            }
        });

        return new BuildPlan(placements, matched, skipped);
    }

    private static int dist2(BlockPos pos, int cx, int cz) {
        int dx = pos.getX() - cx;
        int dz = pos.getZ() - cz;
        return dx * dx + dz * dz;
    }

    /** Hard protections: never replace bedrock, tile entities or platform builders. */
    public static boolean canReplace(World world, BlockPos pos) {
        IBlockState current = world.getBlockState(pos);
        Block block = current.getBlock();
        if (block.hasTileEntity(current)) {
            return false; // chests, machines... never eat tile entities
        }
        if (block == Blocks.BEDROCK || block instanceof PlatformBuilderBlock) {
            return false;
        }
        return true;
    }

    /** Air, fluids and vanilla-replaceable blocks (grass, snow...) — always free to fill. */
    public static boolean isSoftReplaceable(IBlockState state) {
        Block block = state.getBlock();
        if (block == net.minecraft.init.Blocks.AIR) {
            return true;
        }
        net.minecraft.block.material.Material material = state.getMaterial();
        return material == net.minecraft.block.material.Material.WATER
                || material == net.minecraft.block.material.Material.LAVA
                || material.isReplaceable();
    }
}
