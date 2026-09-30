package dev.celestiacraft.industrialplatform.network;

import dev.celestiacraft.industrialplatform.client.ClientConfigCache;
import dev.celestiacraft.industrialplatform.client.PreviewRenderer;
import dev.celestiacraft.industrialplatform.client.gui.GuiPlatformConfig;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Server -> client: the build animation for the builder at {@code pos} has
 * finished; close every form of the hologram for it — the GUI/hover hologram,
 * the persistent "show preview" toggle (also stored server-side) and the open
 * config screen's local copy, so nothing lingers over the built platform.
 */
public class PacketBuildComplete implements IMessage {

    private BlockPos pos;

    public PacketBuildComplete() {
    }

    public PacketBuildComplete(BlockPos pos) {
        this.pos = pos;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(pos == null ? 0L : pos.toLong());
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        pos = BlockPos.fromLong(buf.readLong());
    }

    public static class Handler implements IMessageHandler<PacketBuildComplete, IMessage> {

        @Override
        public IMessage onMessage(final PacketBuildComplete message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    if (message.pos == null) {
                        PreviewRenderer.active = false;
                        return;
                    }
                    // static hologram state for this builder dies with the build
                    if (message.pos.equals(PreviewRenderer.pos)) {
                        PreviewRenderer.active = false;
                        PreviewRenderer.cfg = null;
                        PreviewRenderer.pos = null;
                    }
                    // cached copy loses its persistent-preview flag
                    dev.celestiacraft.industrialplatform.config.PlatformConfig cached =
                            ClientConfigCache.cached(message.pos);
                    if (cached != null && cached.previewOn) {
                        cached.previewOn = false;
                        ClientConfigCache.remember(message.pos, cached);
                    }
                    // the open config screen's local copy follows suit
                    if (Minecraft.getMinecraft().currentScreen instanceof GuiPlatformConfig) {
                        ((GuiPlatformConfig) Minecraft.getMinecraft().currentScreen)
                                .onBuildCompleted(message.pos);
                    }
                }
            });
            return null;
        }
    }
}
