package dev.celestiacraft.industrialplatform.tile;

import dev.celestiacraft.industrialplatform.config.PlatformConfig;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.nbt.NBTTagCompound;

public class TilePlatformBuilder extends TileEntity {

    private PlatformConfig config = PlatformConfig.defaults();
    private boolean configured;

    public PlatformConfig getConfig() {
        return config;
    }

    public void setConfig(PlatformConfig config) {
        this.config = config;
        this.configured = true;
        markDirty();
    }

    public boolean isConfigured() {
        return configured;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setTag("Config", config.toNBT());
        compound.setBoolean("Configured", configured);
        return compound;
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        if (compound.hasKey("Config")) {
            config = PlatformConfig.fromNBT(compound.getCompoundTag("Config"));
        }
        configured = compound.getBoolean("Configured");
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        return writeToNBT(new NBTTagCompound());
    }

    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 0, getUpdateTag());
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        readFromNBT(pkt.getNbtCompound());
    }
}
