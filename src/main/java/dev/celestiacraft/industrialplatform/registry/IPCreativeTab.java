package dev.celestiacraft.industrialplatform.registry;

import dev.celestiacraft.industrialplatform.IndustrialPlatform;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;

public class IPCreativeTab extends CreativeTabs {

    public IPCreativeTab() {
        super(IndustrialPlatform.MODID);
    }

    @Override
    public ItemStack createIcon() {
        return new ItemStack(IPRegistry.PLATFORM_BUILDER);
    }
}
