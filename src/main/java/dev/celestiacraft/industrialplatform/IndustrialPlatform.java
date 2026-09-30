package dev.celestiacraft.industrialplatform;

import dev.celestiacraft.industrialplatform.blueprint.BlueprintLibrary;
import dev.celestiacraft.industrialplatform.command.CommandBlueprints;
import dev.celestiacraft.industrialplatform.network.PacketConfig;
import dev.celestiacraft.industrialplatform.proxy.CommonProxy;
import dev.celestiacraft.industrialplatform.registry.IPCreativeTab;
import dev.celestiacraft.industrialplatform.tile.TilePlatformBuilder;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.relauncher.Side;
import org.apache.logging.log4j.Logger;

/**
 * Industrial Platform — chunk-independent, fully configurable platform builder for 1.12.2.
 *
 * Border / fill / center blocks, the platform footprint and every material are chosen
 * in the builder GUI; blueprints can be registered from KubeJS-style javascript files.
 */
@Mod(modid = IndustrialPlatform.MODID, name = IndustrialPlatform.NAME, version = IndustrialPlatform.VERSION,
        acceptedMinecraftVersions = "[1.12,1.13)", dependencies = "after:kubejs;after:create")
public class IndustrialPlatform {

    public static final String MODID = "industrial_platform";
    public static final String NAME = "Industrial Platform";
    public static final String VERSION = "2.4.2";

    @SidedProxy(clientSide = "dev.celestiacraft.industrialplatform.proxy.ClientProxy",
            serverSide = "dev.celestiacraft.industrialplatform.proxy.CommonProxy")
    public static CommonProxy proxy;

    public static final CreativeTabs CREATIVE_TAB = new IPCreativeTab();

    public static SimpleNetworkWrapper NETWORK;
    public static Logger LOGGER;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOGGER = event.getModLog();

        NETWORK = NetworkRegistry.INSTANCE.newSimpleChannel(MODID);
        NETWORK.registerMessage(PacketConfig.Handler.class, PacketConfig.class, 0, Side.SERVER);
        NETWORK.registerMessage(dev.celestiacraft.industrialplatform.network.PacketBuildComplete.Handler.class,
                dev.celestiacraft.industrialplatform.network.PacketBuildComplete.class, 1, Side.CLIENT);
        NETWORK.registerMessage(dev.celestiacraft.industrialplatform.network.PacketConfigRequest.Handler.class,
                dev.celestiacraft.industrialplatform.network.PacketConfigRequest.class, 2, Side.SERVER);
        NETWORK.registerMessage(dev.celestiacraft.industrialplatform.network.PacketConfigSync.Handler.class,
                dev.celestiacraft.industrialplatform.network.PacketConfigSync.class, 3, Side.CLIENT);
        NETWORK.registerMessage(dev.celestiacraft.industrialplatform.network.PacketMaterialRequest.Handler.class,
                dev.celestiacraft.industrialplatform.network.PacketMaterialRequest.class, 4, Side.SERVER);
        NETWORK.registerMessage(dev.celestiacraft.industrialplatform.network.PacketMaterialStatus.Handler.class,
                dev.celestiacraft.industrialplatform.network.PacketMaterialStatus.class, 5, Side.CLIENT);

        GameRegistry.registerTileEntity(TilePlatformBuilder.class,
                new ResourceLocation(MODID, "platform_builder"));

        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandBlueprints());
        BlueprintLibrary.load(event.getServer().getDataDirectory(), "server-start");
    }
}
