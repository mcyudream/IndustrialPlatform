package dev.celestiacraft.industrialplatform.proxy;

import dev.celestiacraft.industrialplatform.blueprint.BlueprintLibrary;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class ClientProxy extends CommonProxy {

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        // Load local blueprints so the builder GUI can offer them in singleplayer.
        BlueprintLibrary.load(Minecraft.getMinecraft().gameDir);
    }
}
