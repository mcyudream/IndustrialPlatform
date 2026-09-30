package dev.celestiacraft.industrialplatform.platform;

import dev.celestiacraft.industrialplatform.config.BlockRef;
import dev.celestiacraft.industrialplatform.config.PlatformConfig;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Checks the player's inventory against the material bill and consumes it.
 * Creative players neither need nor lose materials.
 */
public final class MaterialScanner {

    private MaterialScanner() {
    }

    public static Map<BlockRef, Integer> required(PlatformConfig cfg) {
        return new PlatformLayout(cfg).requiredMaterials();
    }

    /**
     * World-aware bill: every cell that already holds exactly the target block is
     * reusable and left out of the bill, so rebuilding never double-charges. With
     * {@code replaceExisting} off, solid non-matching blocks are kept and also
     * left out (only air / fluids / replaceable cells get filled).
     */
    public static Map<BlockRef, Integer> required(World world, BlockPos anchor, PlatformConfig cfg) {
        PlatformLayout layout = new PlatformLayout(cfg);
        int anchorX = (anchor.getX() >> 4) * 16 + 8 + cfg.offsetX;
        int anchorZ = (anchor.getZ() >> 4) * 16 + 8 + cfg.offsetZ;
        int x0 = anchorX - layout.centerX;
        int z0 = anchorZ - layout.centerZ;
        int y0 = Math.max(0, Math.min(255, anchor.getY() + cfg.offsetY));
        Map<BlockRef, Integer> counts = new LinkedHashMap<BlockRef, Integer>();
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
                    IBlockState current = world.getBlockState(new BlockPos(x0 + x, y, z0 + z));
                    if (current.getBlock() == ref.block
                            && current.getBlock().getMetaFromState(current) == ref.meta) {
                        continue; // already the right block — reusable, no cost
                    }
                    if (!cfg.replaceExisting && !PlatformGenerator.isSoftReplaceable(current)) {
                        continue; // keep mode: existing solid blocks stay, no cost
                    }
                    Integer old = counts.get(ref);
                    counts.put(ref, old == null ? 1 : old + 1);
                }
            }
        }
        // auto-torch option billed like the generator places them
        if (cfg.autoTorches) {
            BlockRef torch = new BlockRef(net.minecraft.init.Blocks.TORCH, 0);
            int torchY = y0 + cfg.layers;
            if (torchY <= 255) {
                for (int[] spot : layout.torchSpots()) {
                    BlockPos pos = new BlockPos(x0 + spot[0], torchY, z0 + spot[1]);
                    IBlockState current = world.getBlockState(pos);
                    if (current.getBlock() == net.minecraft.init.Blocks.TORCH) {
                        continue;
                    }
                    Integer old = counts.get(torch);
                    counts.put(torch, old == null ? 1 : old + 1);
                }
            }
        }
        return counts;
    }

    /** @return role -> how many blocks the player still has to gather. */
    public static Map<BlockRef, Integer> missing(EntityPlayer player, PlatformConfig cfg) {
        return missing(player, required(cfg));
    }

    public static Map<BlockRef, Integer> missing(EntityPlayer player, Map<BlockRef, Integer> bill) {
        Map<BlockRef, Integer> missing = new LinkedHashMap<BlockRef, Integer>();
        if (player.capabilities.isCreativeMode) {
            return missing;
        }
        for (Map.Entry<BlockRef, Integer> entry : bill.entrySet()) {
            int have = countOf(player, entry.getKey());
            if (have < entry.getValue()) {
                missing.put(entry.getKey(), entry.getValue() - have);
            }
        }
        return missing;
    }

    public static int countOf(EntityPlayer player, BlockRef ref) {
        if (ref.isAir()) {
            return Integer.MAX_VALUE;
        }
        Item item = Item.getItemFromBlock(ref.block);
        int count = 0;
        for (ItemStack stack : player.inventory.mainInventory) {
            if (matches(stack, item, ref.meta)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /** Removes the full bill from the inventory; call only after {@link #missing} was empty. */
    public static Map<BlockRef, Integer> consume(EntityPlayer player, PlatformConfig cfg) {
        return consume(player, required(cfg));
    }

    public static Map<BlockRef, Integer> consume(EntityPlayer player, Map<BlockRef, Integer> bill) {
        Map<BlockRef, Integer> consumed = new LinkedHashMap<BlockRef, Integer>();
        if (player.capabilities.isCreativeMode) {
            return consumed;
        }
        for (Map.Entry<BlockRef, Integer> entry : bill.entrySet()) {
            take(player, entry.getKey(), entry.getValue());
            consumed.put(entry.getKey(), entry.getValue());
        }
        player.inventory.markDirty();
        return consumed;
    }

    private static void take(EntityPlayer player, BlockRef ref, int amount) {
        Item item = Item.getItemFromBlock(ref.block);
        NonNullList<ItemStack> inv = player.inventory.mainInventory;
        for (int i = 0; i < inv.size() && amount > 0; i++) {
            ItemStack stack = inv.get(i);
            if (matches(stack, item, ref.meta)) {
                int take = Math.min(amount, stack.getCount());
                stack.shrink(take);
                amount -= take;
                if (stack.getCount() <= 0) {
                    inv.set(i, ItemStack.EMPTY);
                }
            }
        }
    }

    private static boolean matches(ItemStack stack, Item item, int meta) {
        return !stack.isEmpty() && stack.getItem() == item && stack.getItemDamage() == meta;
    }

    public static String formatMissing(Map<BlockRef, Integer> missing) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<BlockRef, Integer> entry : missing.entrySet()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(entry.getKey().displayName()).append(" x").append(entry.getValue());
        }
        return sb.toString();
    }
}
