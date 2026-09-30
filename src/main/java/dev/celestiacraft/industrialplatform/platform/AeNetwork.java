package dev.celestiacraft.industrialplatform.platform;

import dev.celestiacraft.industrialplatform.IndustrialPlatform;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Iterator;

/**
 * AE2 ME network access via reflection — no compile-time dependency, never a
 * required mod. Two API generations are supported, modern first because target
 * packs ship AE2 forks that backport it (e.g. AppliedEnergistics2-Supergiant,
 * root package {@code ae2}, whose {@code IGridNode} names the accessor
 * {@code grid()} instead of {@code getGrid()}):
 *
 * <pre>
 * modern:  tile implements ae2.api.networking.IInWorldGridNodeHost
 *              node.grid() → IGrid.getStorageService() → IStorageService.getInventory()
 *              → MEStorage: getAvailableStacks() (KeyCounter.get(AEKey)) / extract(AEKey, long, Actionable, IActionSource)
 * classic:  tile implements appeng.api.networking.IGridHost
 *              node.getGrid() → IGrid.getCache(IStorageGrid) → storage list / extractItems
 * </pre>
 *
 * The ME part does not have to touch the builder: any grid-connected part
 * within {@link #SCAN_RADIUS} blocks is accepted (nearest first).
 *
 * Every step is cached; any failure logs once and disables AE support for the
 * session (soft dependency — the mod works fully without AE2).
 */
final class AeNetwork {

    /** Chebyshev radius around the builder searched for an ME part. */
    private static final int SCAN_RADIUS = 4;

    private static boolean broken;
    private static boolean loggedOk;
    private static boolean modern;
    private static String modernFailReason;

    // shared
    private static Class<?> nodeHostClass;
    private static Method nodeGetGrid;
    private static Constructor<?> playerSourceCtor;
    private static Object actionableModulate;

    // modern (ae2.*)
    private static Method hostGetNode;
    private static Method gridGetStorageService;
    private static Method storageGetInventory;
    private static Method storageGetStacks;
    private static Method keyCounterGet;
    private static Method storageExtract;
    private static Method keyFactory; // AEItemKey.of(ItemStack)

    // classic (appeng.*)
    private static Object partInternal;
    private static Method classicGetNode;
    private static Method gridGetCache;
    private static Method classicGetList;
    private static Method classicGetInventory;
    private static Method classicIterator;
    private static Method classicStackItem;
    private static Method classicGetSize;
    private static Method classicExtract;
    private static Method classicCreateStack;
    private static Method classicSetSize;

    private AeNetwork() {
    }

    /** A storage handle for the nearest ME part within {@link #SCAN_RADIUS}, or null. */
    static Object storage(World world, BlockPos pos) {
        if (broken || !init()) {
            return null;
        }
        try {
            for (int r = 1; r <= SCAN_RADIUS; r++) {
                for (int dx = -r; dx <= r; dx++) {
                    for (int dy = -r; dy <= r; dy++) {
                        for (int dz = -r; dz <= r; dz++) {
                            if (Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz))) != r) {
                                continue;
                            }
                            Object handle = storageAt(world, pos.add(dx, dy, dz));
                            if (handle != null) {
                                if (!loggedOk) {
                                    loggedOk = true;
                                    IndustrialPlatform.LOGGER.info("[IP] AE2 network detected ({} API) — material sourcing enabled",
                                            modern ? "modern" : "classic");
                                }
                                return handle;
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            disable(t);
        }
        return null;
    }

    /** Node → grid → storage inventory for the TE at one position, or null. */
    private static Object storageAt(World world, BlockPos pos) {
        TileEntity te = world.getTileEntity(pos);
        if (te == null || !nodeHostClass.isInstance(te)) {
            return null;
        }
        try {
            Object node = nodeOf(te);
            if (node == null) {
                return null;
            }
            Object grid = nodeGetGrid.invoke(node);
            return grid == null ? null
                    : modern
                            ? storageGetInventory.invoke(gridGetStorageService.invoke(grid))
                            : gridGetCache.invoke(grid, classicStorageGridClass);
        } catch (Throwable t) {
            // unformed or partially loaded ME part — try the next candidate
            return null;
        }
    }

    /** The host's main node (null side), falling back to per-side nodes for part hosts. */
    private static Object nodeOf(TileEntity te) throws Exception {
        Object node = hostGetNode.invoke(te, new Object[]{null});
        if (node != null) {
            return node;
        }
        for (EnumFacing facing : EnumFacing.values()) {
            node = hostGetNode.invoke(te, new Object[]{facing});
            if (node != null) {
                return node;
            }
        }
        return null;
    }

    /** Available amount of item:meta in the network. */
    static long count(Object handle, Item item, int meta) {
        if (handle == null || broken) {
            return 0;
        }
        try {
            if (modern) {
                Object key = keyFactory.invoke(null, new ItemStack(item, 1, meta));
                Object counter = storageGetStacks.invoke(handle); // KeyCounter
                return key == null ? 0 : (Long) keyCounterGet.invoke(counter, key);
            }
            Object list = classicGetList.invoke(handle);
            if (list == null) {
                return 0;
            }
            long total = 0;
            Iterator<?> it = (Iterator<?>) classicIterator.invoke(list);
            while (it.hasNext()) {
                Object aeStack = it.next();
                if (aeStack == null) {
                    continue;
                }
                ItemStack stack = (ItemStack) classicStackItem.invoke(aeStack);
                if (stack == null || stack.getItem() != item || stack.getItemDamage() != meta) {
                    continue;
                }
                total += (Long) classicGetSize.invoke(aeStack);
            }
            return total;
        } catch (Throwable t) {
            disable(t);
            return 0;
        }
    }

    /** Extract up to {@code amount}; returns how much was actually taken. */
    static int extract(Object handle, EntityPlayer player, Item item, int meta, int amount) {
        if (handle == null || broken || amount <= 0) {
            return 0;
        }
        try {
            Object source = actionSource(player);
            if (source == null) {
                return 0;
            }
            if (modern) {
                Object key = keyFactory.invoke(null, new ItemStack(item, 1, meta));
                if (key == null) {
                    return 0;
                }
                long got = (Long) storageExtract.invoke(handle, key, (long) amount, actionableModulate, source);
                return (int) Math.max(0, Math.min(Integer.MAX_VALUE, got));
            }
            Object helper = classicCreateStack.invoke(null, new ItemStack(item, 1, meta));
            helper = classicSetSize.invoke(helper, (long) amount);
            Object taken = classicExtract.invoke(classicGetInventory.invoke(handle), helper, actionableModulate, source);
            if (taken == null) {
                return 0;
            }
            long got = (Long) classicGetSize.invoke(taken);
            return (int) Math.max(0, Math.min(Integer.MAX_VALUE, got));
        } catch (Throwable t) {
            disable(t);
            return 0;
        }
    }

    // ---------------------------------------------------------------- setup

    private static Class<?> classicStorageGridClass;

    private static boolean init() {
        if (nodeHostClass != null) {
            return true;
        }
        if (initModern()) {
            modern = true;
            return true;
        }
        return initClassic();
    }

    private static boolean initModern() {
        try {
            nodeHostClass = Class.forName("ae2.api.networking.IInWorldGridNodeHost");
            Class<?> gridClass = Class.forName("ae2.api.networking.IGrid");
            Class<?> nodeClass = Class.forName("ae2.api.networking.IGridNode");
            Class<?> storageServiceClass = Class.forName("ae2.api.networking.storage.IStorageService");
            Class<?> meStorageClass = Class.forName("ae2.api.storage.MEStorage");
            Class<?> keyCounterClass = Class.forName("ae2.api.stacks.KeyCounter");
            Class<?> aeItemKeyClass = Class.forName("ae2.api.stacks.AEItemKey");
            Class<?> actionableClass = Class.forName("ae2.api.config.Actionable");

            // node host: getGridNode() or getGridNode(EnumFacing)
            try {
                hostGetNode = nodeHostClass.getMethod("getGridNode");
            } catch (NoSuchMethodException e) {
                hostGetNode = nodeHostClass.getMethod("getGridNode", EnumFacing.class);
            }
            // forks disagree on the name: modern API getGrid(), Supergiant backport grid()
            try {
                nodeGetGrid = nodeClass.getMethod("getGrid");
            } catch (NoSuchMethodException e) {
                nodeGetGrid = nodeClass.getMethod("grid");
            }
            gridGetStorageService = gridClass.getMethod("getStorageService");
            storageGetInventory = storageServiceClass.getMethod("getInventory");
            storageGetStacks = meStorageClass.getMethod("getAvailableStacks");
            Class<?> aeKeyClass = Class.forName("ae2.api.stacks.AEKey");
            Class<?> actionSourceClass = Class.forName("ae2.api.networking.security.IActionSource");
            keyCounterGet = keyCounterClass.getMethod("get", aeKeyClass);
            storageExtract = meStorageClass.getMethod("extract", aeKeyClass, long.class, actionableClass, actionSourceClass);
            keyFactory = aeItemKeyClass.getMethod("of", ItemStack.class);
            actionableModulate = actionableClass.getField("MODULATE").get(null);
            playerSourceCtor = playerSourceCtor("ae2.me.helpers.PlayerSource");
            return playerSourceCtor != null;
        } catch (Throwable t) {
            modernFailReason = t.toString();
            return false;
        }
    }

    private static boolean initClassic() {
        try {
            nodeHostClass = Class.forName("appeng.api.networking.IGridHost");
            Class<?> partLocationClass = Class.forName("appeng.api.util.AEPartLocation");
            classicStorageGridClass = Class.forName("appeng.api.storage.IStorageGrid");
            Class<?> aeStackClass = Class.forName("appeng.api.storage.data.IAEItemStack");
            Class<?> actionableClass = Class.forName("appeng.api.config.Actionable");
            Class<?> gridClass = Class.forName("appeng.api.networking.IGrid");
            Class<?> gridNodeClass = Class.forName("appeng.api.networking.IGridNode");
            Class<?> storageHelper = Class.forName("appeng.api.storage.IStorageHelper");

            partInternal = partLocationClass.getField("INTERNAL").get(null);
            actionableModulate = actionableClass.getField("MODULATE").get(null);
            classicCreateStack = storageHelper.getMethod("createItemStack", ItemStack.class);
            classicSetSize = aeStackClass.getMethod("setStackSize", long.class);
            classicGetNode = nodeHostClass.getMethod("getGridNode", partLocationClass);
            nodeGetGrid = gridNodeClass.getMethod("getGrid");
            gridGetCache = gridClass.getMethod("getCache", Class.class);
            classicGetList = classicStorageGridClass.getMethod("getStorageList");
            try {
                classicGetInventory = classicStorageGridClass.getMethod("getStorageInventory");
            } catch (NoSuchMethodException e) {
                classicGetInventory = classicStorageGridClass.getMethod("getCellInventory");
            }
            classicIterator = Iterable.class.getMethod("iterator");
            classicStackItem = aeStackClass.getMethod("getItemStack");
            classicGetSize = aeStackClass.getMethod("getStackSize");
            Class<?> invClass = Class.forName("appeng.api.storage.IMEInventory");
            Class<?> actionSourceClass = Class.forName("appeng.api.networking.security.IActionSource");
            classicExtract = invClass.getMethod("extractItems", aeStackClass, actionableClass, actionSourceClass);
            playerSourceCtor = playerSourceCtor("appeng.me.helpers.PlayerSource");
            return playerSourceCtor != null;
        } catch (Throwable t) {
            disable(t);
            return false;
        }
    }

    private static Constructor<?> playerSourceCtor(String className) {
        try {
            Class<?> cls = Class.forName(className);
            for (Constructor<?> ctor : cls.getConstructors()) {
                Class<?>[] params = ctor.getParameterTypes();
                if (params.length >= 1 && params[0].isAssignableFrom(net.minecraft.entity.player.EntityPlayer.class)) {
                    return ctor;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Object actionSource(EntityPlayer player) throws Exception {
        Object[] args = new Object[playerSourceCtor.getParameterCount()];
        args[0] = player;
        return playerSourceCtor.newInstance(args);
    }

    private static void disable(Throwable t) {
        if (!broken) {
            broken = true;
            IndustrialPlatform.LOGGER.warn("[IP] AE2 material access unavailable: {}{}",
                    t.toString(),
                    modernFailReason == null ? "" : " (modern API probe failed first: " + modernFailReason + ")");
        }
    }
}
