package dev.celestiacraft.industrialplatform.platform;

import dev.celestiacraft.industrialplatform.IndustrialPlatform;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

/**
 * Plays build plans as an animation on the server: every world tick a slice of
 * the queue is placed (bottom-up, rippling outward), each block landing with a
 * burst of magic particles. The slice size adapts so even huge platforms finish
 * in about two seconds. When a job finishes, the building player gets the
 * final placed / reused / skipped report.
 */
@Mod.EventBusSubscriber(modid = IndustrialPlatform.MODID)
public final class BuildAnimator {

    private static final List<Job> JOBS = new ArrayList<Job>();

    private BuildAnimator() {
    }

    public static void submit(WorldServer world, PlatformGenerator.BuildPlan plan, EntityPlayerMP player,
                              boolean replaceExisting) {
        submit(world, plan, player, replaceExisting, null);
    }

    public static void submit(WorldServer world, PlatformGenerator.BuildPlan plan, EntityPlayerMP player,
                              boolean replaceExisting, net.minecraft.util.math.BlockPos builderPos) {
        if (!plan.placements.isEmpty()) {
            JOBS.add(new Job(world, plan, player, replaceExisting, builderPos));
        }
    }

    @SubscribeEvent
    public static void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.side != Side.SERVER || event.phase != TickEvent.Phase.END || JOBS.isEmpty()) {
            return;
        }
        Iterator<Job> iterator = JOBS.iterator();
        while (iterator.hasNext()) {
            Job job = iterator.next();
            if (job.world != event.world) {
                continue;
            }
            int budget = Math.max(32, job.total / 40);
            while (budget-- > 0 && !job.queue.isEmpty()) {
                placeNext(job);
            }
            if (job.queue.isEmpty()) {
                iterator.remove();
                if (job.player.connection != null) {
                    job.player.sendMessage(new TextComponentTranslation(
                            "ip.msg.build_success", job.placed, job.matched, job.skipped));
                    dev.celestiacraft.industrialplatform.IndustrialPlatform.NETWORK.sendTo(
                            new dev.celestiacraft.industrialplatform.network.PacketBuildComplete(job.builderPos), job.player);
                }
                // the persistent preview dies with the finished build — store it
                if (job.builderPos != null) {
                    net.minecraft.tileentity.TileEntity te = job.world.getTileEntity(job.builderPos);
                    if (te instanceof dev.celestiacraft.industrialplatform.tile.TilePlatformBuilder) {
                        dev.celestiacraft.industrialplatform.config.PlatformConfig config =
                                ((dev.celestiacraft.industrialplatform.tile.TilePlatformBuilder) te).getConfig();
                        if (config.previewOn) {
                            config.previewOn = false;
                            ((dev.celestiacraft.industrialplatform.tile.TilePlatformBuilder) te).setConfig(config);
                        }
                    }
                }
            }
        }
    }

    private static void placeNext(Job job) {
        PlatformGenerator.Placement placement = job.queue.poll();
        if (!job.world.isBlockLoaded(placement.pos)) {
            job.skipped++;
            return;
        }
        if (job.world.getBlockState(placement.pos) == placement.state) {
            job.matched++;
            return;
        }
        if (!job.replaceExisting && !PlatformGenerator.isSoftReplaceable(job.world.getBlockState(placement.pos))) {
            job.skipped++; // keep mode: existing solid block stays
            return;
        }
        if (!PlatformGenerator.canReplace(job.world, placement.pos)) {
            job.skipped++;
            return;
        }
        // setBlockState flag 2: notify clients, no neighbor updates, no lighting
        // recompute — safe to ripple thousands of blocks per tick without lag
        job.world.setBlockState(placement.pos, placement.state, 2);
        job.placed++;
        job.world.spawnParticle(EnumParticleTypes.CRIT_MAGIC,
                placement.pos.getX() + 0.5D, placement.pos.getY() + 1.05D, placement.pos.getZ() + 0.5D,
                2, 0.18D, 0.18D, 0.18D, 0.01D);
    }

    private static final class Job {
        final WorldServer world;
        final Deque<PlatformGenerator.Placement> queue;
        final EntityPlayerMP player;
        final boolean replaceExisting;
        final net.minecraft.util.math.BlockPos builderPos;
        final int total;
        int placed;
        int matched;
        int skipped;

        Job(WorldServer world, PlatformGenerator.BuildPlan plan, EntityPlayerMP player, boolean replaceExisting,
            net.minecraft.util.math.BlockPos builderPos) {
            this.world = world;
            this.queue = new ArrayDeque<PlatformGenerator.Placement>(plan.placements);
            this.total = plan.placements.size();
            this.matched = plan.matched;
            this.player = player;
            this.replaceExisting = replaceExisting;
            this.builderPos = builderPos;
        }
    }
}
