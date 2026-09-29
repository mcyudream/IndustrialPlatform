package dev.celestiacraft.industrialplatform.network;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.celestiacraft.industrialplatform.config.BlockRef;
import dev.celestiacraft.industrialplatform.config.PlatformConfig;
import dev.celestiacraft.industrialplatform.platform.BuildAnimator;
import dev.celestiacraft.industrialplatform.platform.MaterialScanner;
import dev.celestiacraft.industrialplatform.platform.PlatformGenerator;
import dev.celestiacraft.industrialplatform.tile.TilePlatformBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Client -> server: store the builder configuration into the tile entity.
 * With {@code build = true} the materials are consumed from the player's
 * inventory and the platform is generated.
 */
public class PacketConfig implements IMessage {

    private BlockPos pos;
    private String json = "{}";
    private boolean build;

    public PacketConfig() {
    }

    public PacketConfig(BlockPos pos, PlatformConfig config, boolean build) {
        this.pos = pos;
        this.json = config.toJson().toString();
        this.build = build;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(pos.toLong());
        buf.writeBoolean(build);
        ByteBufUtils.writeUTF8String(buf, json);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        pos = BlockPos.fromLong(buf.readLong());
        build = buf.readBoolean();
        json = ByteBufUtils.readUTF8String(buf);
    }

    public static class Handler implements IMessageHandler<PacketConfig, IMessage> {

        @Override
        public IMessage onMessage(PacketConfig message, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> handle(player, message));
            return null;
        }

        private static void handle(EntityPlayerMP player, PacketConfig message) {
            WorldServer world = player.getServerWorld();
            if (player.getDistanceSq(message.pos) > 64.0D * 64.0D) {
                return;
            }
            TileEntity tile = world.getTileEntity(message.pos);
            if (!(tile instanceof TilePlatformBuilder)) {
                return;
            }

            PlatformConfig config = parse(message.json);
            if (config == null) {
                return;
            }
            config.clamp();
            ((TilePlatformBuilder) tile).setConfig(config);
            world.notifyBlockUpdate(message.pos, world.getBlockState(message.pos), world.getBlockState(message.pos), 3);

            if (!message.build) {
                return;
            }

            // world-aware bill: positions already holding the right block cost nothing
            java.util.Map<BlockRef, Integer> bill = MaterialScanner.required(world, message.pos, config);
            if (!player.capabilities.isCreativeMode) {
                java.util.Map<BlockRef, Integer> missing = MaterialScanner.missing(player, bill);
                if (!missing.isEmpty()) {
                    player.sendMessage(new TextComponentTranslation("ip.msg.missing_list",
                            MaterialScanner.formatMissing(missing)));
                    return;
                }
            }

            PlatformGenerator.BuildPlan plan = PlatformGenerator.plan(world, message.pos, config);
            if (!player.capabilities.isCreativeMode) {
                java.util.Map<BlockRef, Integer> consumed = MaterialScanner.consume(player, bill);
                player.sendMessage(new TextComponentTranslation("ip.msg.consumed", consumed.size()));
            }
            BuildAnimator.submit(world, plan, player, config.replaceExisting);
            player.sendMessage(new TextComponentTranslation("ip.msg.build_started"));
            player.inventoryContainer.detectAndSendChanges();
        }

        private static PlatformConfig parse(String json) {
            try {
                JsonElement element = new JsonParser().parse(json);
                if (element.isJsonObject()) {
                    return PlatformConfig.fromJson((JsonObject) element);
                }
            } catch (Exception ignored) {
            }
            return null;
        }
    }
}
