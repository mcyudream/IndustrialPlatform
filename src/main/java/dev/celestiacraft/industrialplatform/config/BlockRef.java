package dev.celestiacraft.industrialplatform.config;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * A block + metadata reference, serialized as {@code modid:block[:meta]}.
 * {@link #AIR} marks "no block" for optional roles (boundaries, channel, center marker).
 */
public final class BlockRef {

    public static final BlockRef AIR = new BlockRef(null, 0);

    @Nullable
    public final Block block;
    public final int meta;

    public BlockRef(@Nullable Block block, int meta) {
        this.block = block;
        this.meta = meta;
    }

    public boolean isAir() {
        return block == null || block == Blocks.AIR;
    }

    public ItemStack toStack(int count) {
        return isAir() ? ItemStack.EMPTY : new ItemStack(block, count, meta);
    }

    public String displayName() {
        ItemStack stack = toStack(1);
        return stack.isEmpty() ? "minecraft:air" : stack.getDisplayName();
    }

    public String serializedName() {
        if (isAir()) {
            return "minecraft:air";
        }
        ResourceLocation name = Block.REGISTRY.getNameForObject(block);
        String value = name == null ? "minecraft:air" : name.toString();
        return meta == 0 ? value : value + ":" + meta;
    }

    @Nullable
    public static BlockRef parse(@Nullable String s) {
        if (s == null) {
            return null;
        }
        s = s.trim();
        if (s.isEmpty() || s.equalsIgnoreCase("air") || s.equalsIgnoreCase("minecraft:air")) {
            return AIR;
        }
        String name = s;
        int meta = 0;
        String[] parts = s.split(":");
        if (parts.length == 3) {
            name = parts[0] + ":" + parts[1];
            try {
                meta = Integer.parseInt(parts[2]);
            } catch (NumberFormatException e) {
                return null;
            }
        } else if (parts.length == 1) {
            name = "minecraft:" + s;
        }
        Block block = Block.getBlockFromName(name);
        if (block == null) {
            return null;
        }
        return new BlockRef(block, Math.max(0, Math.min(15, meta)));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BlockRef)) {
            return false;
        }
        BlockRef other = (BlockRef) o;
        return meta == other.meta && Objects.equals(block, other.block);
    }

    @Override
    public int hashCode() {
        return Objects.hash(block, meta);
    }
}
