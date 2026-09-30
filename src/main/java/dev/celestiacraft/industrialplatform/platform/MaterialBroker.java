package dev.celestiacraft.industrialplatform.platform;

import dev.celestiacraft.industrialplatform.config.BlockRef;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-side material sourcing with fallbacks: player inventory first, then
 * item-handler containers adjacent to the builder (chests, drawers, iron
 * chests...), then the AE2 ME network of any adjacent ME part. The player
 * never has to carry the whole bill anymore.
 */
public final class MaterialBroker {

    private MaterialBroker() {
    }

    /** @return refs the combined sources cannot cover, with their shortfalls. */
    public static Map<BlockRef, Integer> missing(World world, BlockPos pos, EntityPlayer player, Map<BlockRef, Integer> bill) {
        Map<BlockRef, Integer> missing = new LinkedHashMap<BlockRef, Integer>();
        if (player.capabilities.isCreativeMode) {
            return missing;
        }
        Object storageGrid = AeNetwork.storage(world, pos);
        List<IItemHandler> containers = adjacentHandlers(world, pos);
        for (Map.Entry<BlockRef, Integer> entry : bill.entrySet()) {
            long have = MaterialScanner.countOf(player, entry.getKey());
            have += countInContainers(containers, entry.getKey());
            have += AeNetwork.count(storageGrid, itemOf(entry.getKey()), entry.getKey().meta);
            if (have < entry.getValue()) {
                missing.put(entry.getKey(), (int) (entry.getValue() - have));
            }
        }
        return missing;
    }

    /** Removes the bill from the sources; player inventory is spent first. */
    public static Map<BlockRef, Integer> consume(World world, BlockPos pos, EntityPlayer player, Map<BlockRef, Integer> bill) {
        Map<BlockRef, Integer> consumed = new LinkedHashMap<BlockRef, Integer>();
        if (player.capabilities.isCreativeMode) {
            return consumed;
        }
        Object storageGrid = AeNetwork.storage(world, pos);
        for (Map.Entry<BlockRef, Integer> entry : bill.entrySet()) {
            int remaining = entry.getValue();
            remaining -= takeFromPlayer(player, entry.getKey(), remaining);
            remaining -= takeFromContainers(world, pos, entry.getKey(), remaining);
            remaining -= AeNetwork.extract(storageGrid, player, itemOf(entry.getKey()), entry.getKey().meta, remaining);
            if (remaining < entry.getValue()) {
                consumed.put(entry.getKey(), entry.getValue() - remaining);
            }
        }
        player.inventory.markDirty();
        return consumed;
    }

    private static Item itemOf(BlockRef ref) {
        return ref.isAir() ? null : Item.getItemFromBlock(ref.block);
    }

    private static List<IItemHandler> adjacentHandlers(World world, BlockPos pos) {
        List<IItemHandler> handlers = new ArrayList<IItemHandler>();
        for (EnumFacing facing : EnumFacing.values()) {
            TileEntity te = world.getTileEntity(pos.offset(facing));
            if (te == null) {
                continue;
            }
            if (te.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, facing.getOpposite())) {
                IItemHandler handler = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, facing.getOpposite());
                if (handler != null) {
                    handlers.add(handler);
                }
            }
        }
        return handlers;
    }

    private static long countInContainers(List<IItemHandler> handlers, BlockRef ref) {
        Item item = itemOf(ref);
        if (item == null) {
            return 0;
        }
        long total = 0;
        for (IItemHandler handler : handlers) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (!stack.isEmpty() && stack.getItem() == item && stack.getItemDamage() == ref.meta) {
                    total += stack.getCount();
                }
            }
        }
        return total;
    }

    private static int takeFromPlayer(EntityPlayer player, BlockRef ref, int amount) {
        Item item = itemOf(ref);
        if (item == null || amount <= 0) {
            return 0;
        }
        int taken = 0;
        List<ItemStack> inv = player.inventory.mainInventory;
        for (int i = 0; i < inv.size() && taken < amount; i++) {
            ItemStack stack = inv.get(i);
            if (stack.isEmpty() || stack.getItem() != item || stack.getItemDamage() != ref.meta) {
                continue;
            }
            int take = Math.min(amount - taken, stack.getCount());
            stack.shrink(take);
            taken += take;
            if (stack.getCount() <= 0) {
                inv.set(i, ItemStack.EMPTY);
            }
        }
        return taken;
    }

    private static int takeFromContainers(World world, BlockPos pos, BlockRef ref, int amount) {
        Item item = itemOf(ref);
        if (item == null || amount <= 0) {
            return 0;
        }
        int taken = 0;
        for (EnumFacing facing : EnumFacing.values()) {
            if (taken >= amount) {
                break;
            }
            TileEntity te = world.getTileEntity(pos.offset(facing));
            if (te == null) {
                continue;
            }
            if (!te.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, facing.getOpposite())) {
                continue;
            }
            IItemHandler handler = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, facing.getOpposite());
            if (handler == null) {
                continue;
            }
            for (int slot = 0; slot < handler.getSlots() && taken < amount; slot++) {
                ItemStack inSlot = handler.getStackInSlot(slot);
                if (inSlot.isEmpty() || inSlot.getItem() != item || inSlot.getItemDamage() != ref.meta) {
                    continue;
                }
                ItemStack extracted = handler.extractItem(slot, amount - taken, false);
                if (!extracted.isEmpty()) {
                    taken += extracted.getCount();
                }
            }
        }
        return taken;
    }
}
