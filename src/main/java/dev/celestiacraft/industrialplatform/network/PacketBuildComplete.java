package dev.celestiacraft.industrialplatform.network;

import dev.celestiacraft.industrialplatform.client.PreviewRenderer;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Server -> client: the build animation for this builder has finished; turn off
 * the active (GUI/hover) hologram so it doesn't linger over the built platform.
 * The builder's persistent "show preview" toggle is untouched.
 */
public class PacketBuildComplete implements IMessage {

    @Override
    public void toBytes(ByteBuf buf) {
    }

    @Override
    public void fromBytes(ByteBuf buf) {
    }

    public static class Handler implements IMessageHandler<PacketBuildComplete, IMessage> {

        @Override
        public IMessage onMessage(PacketBuildComplete message, MessageContext ctx) {
            net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    PreviewRenderer.active = false;
                }
            });
            return null;
        }
    }
}
