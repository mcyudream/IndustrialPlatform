package dev.celestiacraft.industrialplatform.network;

import dev.celestiacraft.industrialplatform.config.BlockRef;
import io.netty.buffer.ByteBuf;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Server -> client reply to {@link PacketMaterialRequest}: the shortfall that
 * remains after combining the player inventory, adjacent containers and the AE
 * network — the exact verdict the build click will apply. Payload is
 * {@code "modid:block[:meta]=count,..."}; an empty payload means fully covered.
 *
 * The client branch delegates to {@code ClientSyncer} so this class never
 * touches client classes on a dedicated server.
 */
public class PacketMaterialStatus implements IMessage {

    private BlockPos pos;
    private String payload = "";

    public PacketMaterialStatus() {
    }

    public PacketMaterialStatus(BlockPos pos, Map<BlockRef, Integer> missing) {
        this.pos = pos;
        StringBuilder sb = new StringBuilder();
        if (missing != null) {
            for (Map.Entry<BlockRef, Integer> entry : missing.entrySet()) {
                if (sb.length() > 0) {
                    sb.append(',');
                }
                sb.append(entry.getKey().serializedName()).append('=').append(entry.getValue());
            }
        }
        this.payload = sb.toString();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(pos.toLong());
        ByteBufUtils.writeUTF8String(buf, payload);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        pos = BlockPos.fromLong(buf.readLong());
        payload = ByteBufUtils.readUTF8String(buf);
    }

    public static class Handler implements IMessageHandler<PacketMaterialStatus, IMessage> {

        @Override
        public IMessage onMessage(PacketMaterialStatus message, MessageContext ctx) {
            if (ctx.side == Side.CLIENT) {
                final BlockPos pos = message.pos;
                final String payload = message.payload;
                dev.celestiacraft.industrialplatform.client.ClientSyncer.acceptMaterialStatus(pos, payload);
            }
            return null;
        }
    }

    public static Map<BlockRef, Integer> parse(String payload) {
        Map<BlockRef, Integer> missing = new LinkedHashMap<BlockRef, Integer>();
        if (payload == null || payload.isEmpty()) {
            return missing;
        }
        for (String part : payload.split(",")) {
            int split = part.lastIndexOf('=');
            if (split <= 0) {
                continue;
            }
            BlockRef ref = BlockRef.parse(part.substring(0, split));
            if (ref == null || ref.isAir()) {
                continue;
            }
            try {
                missing.put(ref, Math.max(0, Integer.parseInt(part.substring(split + 1))));
            } catch (NumberFormatException ignored) {
            }
        }
        return missing;
    }
}
