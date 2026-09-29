package dev.celestiacraft.industrialplatform.config;

import com.google.gson.JsonObject;
import dev.celestiacraft.industrialplatform.IndustrialPlatform;
import dev.celestiacraft.industrialplatform.platform.PlatformRole;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.MathHelper;

/**
 * Full builder configuration: the nine block roles, footprint size, layer count,
 * start height, channel shape and the center marker. Stored per builder block in NBT,
 * shipped to the server as JSON when building.
 */
public final class PlatformConfig {

    public BlockRef border = BlockRef.AIR;
    public BlockRef fill = BlockRef.AIR;
    public BlockRef center = BlockRef.AIR;
    public BlockRef boundary = BlockRef.AIR;
    public BlockRef link = BlockRef.AIR;
    public BlockRef body = BlockRef.AIR;
    public BlockRef edge = BlockRef.AIR;
    public BlockRef channelLine = BlockRef.AIR;
    public BlockRef centerMark = BlockRef.AIR;

    /** Vertical layer count of the platform body (top layer carries the surface pattern). */
    public int layers = 1;
    /** Free interior width between the two border columns (X), per platform cell. */
    public int intervalX = 14;
    /** Free interior depth between the two border rows (Z), per platform cell. */
    public int intervalZ = 14;
    /** Platform cells along X — 2x2 etc. merges into one seamless mega platform. */
    public int countX = 1;
    /** Platform cells along Z. */
    public int countZ = 1;
    /** Width of the connecting strips between cells. */
    public int linkWidth = 3;
    /** Width of the channel strip cut into the surface, 0 = no channel. */
    public int channelWidth = 0;

    /** Horizontal offset from the chunk center (platform anchor = chunk center + offset). */
    public int offsetX = 0;
    public int offsetZ = 0;
    /** Vertical offset from the builder's own Y. */
    public int offsetY = 0;

    public ChannelMode channelMode = ChannelMode.NONE;

    /** Replace the very center block with the center marker block. */
    public boolean centerMarkOn = false;

    /** true = replace existing non-matching blocks inside the footprint (charged);
     *  false = only fill air / fluids / replaceable blocks, keep everything else. */
    public boolean replaceExisting = true;

    /** Keep the in-world hologram preview visible even with the GUI closed. */
    public boolean previewOn = false;

    public String blueprintName = "";

    public static PlatformConfig defaults() {
        PlatformConfig c = new PlatformConfig();
        c.border = orAir(BlockRef.parse("minecraft:stonebrick"));
        c.fill = orAir(BlockRef.parse("minecraft:stone"));
        c.center = orAir(BlockRef.parse("minecraft:iron_block"));
        c.boundary = orAir(BlockRef.parse("minecraft:concrete:4"));
        c.link = BlockRef.AIR; // air = strips use the fill material
        c.body = orAir(BlockRef.parse("minecraft:cobblestone"));
        c.edge = orAir(BlockRef.parse("minecraft:stonebrick"));
        c.channelLine = orAir(BlockRef.parse("minecraft:glowstone"));
        c.centerMark = orAir(BlockRef.parse("minecraft:gold_block"));
        return c;
    }

    private static BlockRef orAir(BlockRef ref) {
        return ref == null ? BlockRef.AIR : ref;
    }

    public BlockRef get(PlatformRole role) {
        BlockRef ref;
        switch (role) {
            case BORDER: ref = border; break;
            case FILL: ref = fill; break;
            case CENTER: ref = center; break;
            case BOUNDARY: ref = boundary; break;
            case LINK: ref = link; break;
            case BODY: ref = body; break;
            case EDGE: ref = edge; break;
            case CHANNEL_LINE: ref = channelLine; break;
            case CENTER_MARK: ref = centerMark; break;
            default: ref = BlockRef.AIR; break;
        }
        return ref == null ? BlockRef.AIR : ref;
    }

    public void set(PlatformRole role, BlockRef ref) {
        ref = ref == null ? BlockRef.AIR : ref;
        switch (role) {
            case BORDER: border = ref; break;
            case FILL: fill = ref; break;
            case CENTER: center = ref; break;
            case BOUNDARY: boundary = ref; break;
            case LINK: link = ref; break;
            case BODY: body = ref; break;
            case EDGE: edge = ref; break;
            case CHANNEL_LINE: channelLine = ref; break;
            case CENTER_MARK: centerMark = ref; break;
            default: break;
        }
    }

    public void clamp() {
        layers = MathHelper.clamp(layers, 1, 128);
        intervalX = MathHelper.clamp(intervalX, 0, 126);
        intervalZ = MathHelper.clamp(intervalZ, 0, 126);
        countX = MathHelper.clamp(countX, 1, 8);
        countZ = MathHelper.clamp(countZ, 1, 8);
        linkWidth = MathHelper.clamp(linkWidth, 0, 31);
        channelWidth = MathHelper.clamp(channelWidth, 0, 31);
        offsetX = MathHelper.clamp(offsetX, -64, 64);
        offsetZ = MathHelper.clamp(offsetZ, -64, 64);
        offsetY = MathHelper.clamp(offsetY, -64, 64);
        if (channelMode == null) {
            channelMode = ChannelMode.NONE;
        }
    }

    public PlatformConfig copy() {
        return fromNBT(toNBT());
    }

    // ------------------------------------------------------------------ NBT

    public NBTTagCompound toNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        for (PlatformRole role : PlatformRole.values()) {
            tag.setString(role.nbtKey(), get(role).serializedName());
        }
        tag.setInteger("Layers", layers);
        tag.setInteger("IntervalX", intervalX);
        tag.setInteger("IntervalZ", intervalZ);
        tag.setInteger("CountX", countX);
        tag.setInteger("CountZ", countZ);
        tag.setInteger("LinkWidth", linkWidth);
        tag.setInteger("ChannelWidth", channelWidth);
        tag.setInteger("OffsetX", offsetX);
        tag.setInteger("OffsetZ", offsetZ);
        tag.setInteger("OffsetY", offsetY);
        tag.setString("ChannelMode", channelMode.name());
        tag.setBoolean("CenterMarkOn", centerMarkOn);
        tag.setBoolean("ReplaceExisting", replaceExisting);
        tag.setBoolean("PreviewOn", previewOn);
        tag.setString("BlueprintName", blueprintName == null ? "" : blueprintName);
        return tag;
    }

    public static PlatformConfig fromNBT(NBTTagCompound tag) {
        PlatformConfig c = defaults();
        for (PlatformRole role : PlatformRole.values()) {
            if (tag.hasKey(role.nbtKey())) {
                BlockRef ref = BlockRef.parse(tag.getString(role.nbtKey()));
                if (ref != null) {
                    c.set(role, ref);
                }
            }
        }
        c.layers = tag.getInteger("Layers");
        c.intervalX = tag.getInteger("IntervalX");
        c.intervalZ = tag.getInteger("IntervalZ");
        c.countX = tag.getInteger("CountX");
        c.countZ = tag.getInteger("CountZ");
        c.linkWidth = tag.getInteger("LinkWidth");
        c.channelWidth = tag.getInteger("ChannelWidth");
        c.offsetX = tag.getInteger("OffsetX");
        c.offsetZ = tag.getInteger("OffsetZ");
        c.offsetY = tag.getInteger("OffsetY");
        c.channelMode = ChannelMode.parse(tag.getString("ChannelMode"));
        c.centerMarkOn = tag.getBoolean("CenterMarkOn");
        c.replaceExisting = tag.getBoolean("ReplaceExisting");
        c.previewOn = tag.getBoolean("PreviewOn");
        c.blueprintName = tag.getString("BlueprintName");
        c.clamp();
        return c;
    }

    // ------------------------------------------------------------------ JSON

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        for (PlatformRole role : PlatformRole.values()) {
            o.addProperty(role.nbtKey(), get(role).serializedName());
        }
        o.addProperty("layers", layers);
        o.addProperty("intervalX", intervalX);
        o.addProperty("intervalZ", intervalZ);
        o.addProperty("countX", countX);
        o.addProperty("countZ", countZ);
        o.addProperty("linkWidth", linkWidth);
        o.addProperty("channelWidth", channelWidth);
        o.addProperty("offsetX", offsetX);
        o.addProperty("offsetZ", offsetZ);
        o.addProperty("offsetY", offsetY);
        o.addProperty("channelMode", channelMode.name());
        o.addProperty("centerMarkOn", centerMarkOn);
        o.addProperty("replaceExisting", replaceExisting);
        o.addProperty("previewOn", previewOn);
        o.addProperty("blueprintName", blueprintName == null ? "" : blueprintName);
        return o;
    }

    public static PlatformConfig fromJson(JsonObject o) {
        PlatformConfig c = defaults();
        for (PlatformRole role : PlatformRole.values()) {
            if (o.has(role.nbtKey()) && o.get(role.nbtKey()).isJsonPrimitive()) {
                BlockRef ref = BlockRef.parse(o.get(role.nbtKey()).getAsString());
                if (ref != null) {
                    c.set(role, ref);
                } else {
                    IndustrialPlatform.LOGGER.warn("Unknown block '{}' for role {}", o.get(role.nbtKey()).getAsString(), role);
                }
            }
        }
        c.layers = getInt(o, "layers", c.layers);
        c.intervalX = getInt(o, "intervalX", c.intervalX);
        c.intervalZ = getInt(o, "intervalZ", c.intervalZ);
        c.countX = getInt(o, "countX", c.countX);
        c.countZ = getInt(o, "countZ", c.countZ);
        c.linkWidth = getInt(o, "linkWidth", c.linkWidth);
        c.channelWidth = getInt(o, "channelWidth", c.channelWidth);
        c.offsetX = getInt(o, "offsetX", c.offsetX);
        c.offsetZ = getInt(o, "offsetZ", c.offsetZ);
        c.offsetY = getInt(o, "offsetY", c.offsetY);
        if (o.has("channelMode") && o.get("channelMode").isJsonPrimitive()) {
            c.channelMode = ChannelMode.parse(o.get("channelMode").getAsString());
        }
        c.centerMarkOn = getBoolean(o, "centerMarkOn", c.centerMarkOn);
        c.replaceExisting = getBoolean(o, "replaceExisting", c.replaceExisting);
        c.previewOn = getBoolean(o, "previewOn", c.previewOn);
        if (o.has("blueprintName") && o.get("blueprintName").isJsonPrimitive()) {
            c.blueprintName = o.get("blueprintName").getAsString();
        }
        c.clamp();
        return c;
    }

    static int getInt(JsonObject o, String key, int def) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsInt() : def;
    }

    static boolean getBoolean(JsonObject o, String key, boolean def) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsBoolean() : def;
    }
}
