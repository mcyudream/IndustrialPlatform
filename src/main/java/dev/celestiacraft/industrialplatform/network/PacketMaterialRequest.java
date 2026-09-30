package dev.celestiacraft.industrialplatform.network;

import dev.celestiacraft.industrialplatform.IndustrialPlatform;
import dev.celestiacraft.industrialplatform.tile.TilePlatformBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Client -> server: "what is REALLY missing for this builder once every source
 * is combined (inventory + adjacent containers + AE network)?" The hover card
 * cannot know this client-side — container contents and ME storage are not
 * synced to the client — so it shows the server's answer instead of an
 * inventory-only guess. The server replies with {@link PacketMaterialStatus}.
 */
public class PacketMaterialRequest implements IMessage {

    private BlockPos pos;

    public PacketMaterialRequest() {
    }

    public PacketMaterialRequest(BlockPos pos) {
        this.pos = pos;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(pos.toLong());
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        pos = BlockPos.fromLong(buf.readLong());
    }

    public static class Handler implements IMessageHandler<PacketMaterialRequest, IMessage> {

        @Override
        public IMessage onMessage(PacketMaterialRequest message, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (message.pos == null || player.getDistanceSq(message.pos) > 64.0D * 64.0D) {
                    return;
                }
                WorldServer world = player.getServerWorld();
                TileEntity tile = world.getTileEntity(message.pos);
                if (!(tile instanceof TilePlatformBuilder)) {
                    return;
                }
                dev.celestiacraft.industrialplatform.config.PlatformConfig config =
                        ((TilePlatformBuilder) tile).getConfig();
                if (config == null) {
                    return;
                }
                java.util.Map<dev.celestiacraft.industrialplatform.config.BlockRef, Integer> bill =
                        dev.celestiacraft.industrialplatform.platform.MaterialScanner
                                .required(world, message.pos, config);
                java.util.Map<dev.celestiacraft.industrialplatform.config.BlockRef, Integer> missing =
                        player.capabilities.isCreativeMode
                                ? java.util.Collections.emptyMap()
                                : dev.celestiacraft.industrialplatform.platform.MaterialBroker
                                        .missing(world, message.pos, player, bill);
                IndustrialPlatform.NETWORK.sendTo(new PacketMaterialStatus(message.pos, missing), player);
            });
            return null;
        }
    }
}
