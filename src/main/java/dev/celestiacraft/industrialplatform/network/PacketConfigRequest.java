package dev.celestiacraft.industrialplatform.network;

import dev.celestiacraft.industrialplatform.IndustrialPlatform;
import dev.celestiacraft.industrialplatform.tile.TilePlatformBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Client -> server: "tell me the stored configuration of this builder".
 * Optimization cores in heavy packs can make the client-side tile entity
 * unreliable; this lets the hologram/hover UI ask the authoritative server
 * instead of depending on client TE reads.
 */
public class PacketConfigRequest implements IMessage {

    private BlockPos pos;

    public PacketConfigRequest() {
    }

    public PacketConfigRequest(BlockPos pos) {
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

    public static class Handler implements IMessageHandler<PacketConfigRequest, IMessage> {

        @Override
        public IMessage onMessage(PacketConfigRequest message, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (message.pos == null || player.getDistanceSq(message.pos) > 64.0D * 64.0D) {
                    return;
                }
                TileEntity tile = player.getServerWorld().getTileEntity(message.pos);
                if (tile instanceof TilePlatformBuilder) {
                    IndustrialPlatform.NETWORK.sendTo(
                            new PacketConfigSync(message.pos, ((TilePlatformBuilder) tile).getConfig()), player);
                }
            });
            return null;
        }
    }
}
