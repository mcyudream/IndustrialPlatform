package dev.celestiacraft.industrialplatform.network;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.celestiacraft.industrialplatform.config.PlatformConfig;
import io.netty.buffer.ByteBuf;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Server -> client reply to {@link PacketConfigRequest}: the authoritative
 * configuration of one builder, stored into the client-side config cache.
 *
 * The client branch delegates to {@code ClientSyncer} (a client-only class) so
 * this handler class never touches client classes on a dedicated server.
 */
public class PacketConfigSync implements IMessage {

    private BlockPos pos;
    private String json = "{}";

    public PacketConfigSync() {
    }

    public PacketConfigSync(BlockPos pos, PlatformConfig config) {
        this.pos = pos;
        this.json = config == null ? "{}" : config.toJson().toString();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(pos.toLong());
        ByteBufUtils.writeUTF8String(buf, json);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        pos = BlockPos.fromLong(buf.readLong());
        json = ByteBufUtils.readUTF8String(buf);
    }

    public static class Handler implements IMessageHandler<PacketConfigSync, IMessage> {

        @Override
        public IMessage onMessage(PacketConfigSync message, MessageContext ctx) {
            if (ctx.side == Side.CLIENT) {
                final BlockPos pos = message.pos;
                final String json = message.json;
                dev.celestiacraft.industrialplatform.client.ClientSyncer.acceptSync(pos, json);
            }
            return null;
        }
    }

    public static PlatformConfig parse(String json) {
        try {
            JsonElement element = new JsonParser().parse(json);
            if (element.isJsonObject()) {
                PlatformConfig config = PlatformConfig.fromJson((JsonObject) element);
                config.clamp();
                return config;
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
