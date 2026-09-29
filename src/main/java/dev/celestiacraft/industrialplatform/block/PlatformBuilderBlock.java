package dev.celestiacraft.industrialplatform.block;

import dev.celestiacraft.industrialplatform.IndustrialPlatform;
import dev.celestiacraft.industrialplatform.client.PreviewRenderer;
import dev.celestiacraft.industrialplatform.client.gui.GuiPlatformConfig;
import dev.celestiacraft.industrialplatform.tile.TilePlatformBuilder;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class PlatformBuilderBlock extends BlockContainer {

    public PlatformBuilderBlock() {
        super(Material.ROCK);
        setRegistryName(IndustrialPlatform.MODID, "platform_builder");
        setTranslationKey(IndustrialPlatform.MODID + ".platform_builder");
        setHardness(3.0F);
        setResistance(10.0F);
        setSoundType(SoundType.STONE);
        setCreativeTab(IndustrialPlatform.CREATIVE_TAB);
    }

    @Override
    public TileEntity createNewTileEntity(World worldIn, int meta) {
        return new TilePlatformBuilder();
    }

    // BlockContainer defaults to INVISIBLE rendering (vanilla containers use custom
    // renderers); we are a plain JSON-model cube and must opt back into model rendering.
    @Override
    public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.MODEL;
    }

    @Override
    public void breakBlock(World worldIn, BlockPos pos, IBlockState state) {
        super.breakBlock(worldIn, pos, state);
        if (worldIn.isRemote && pos.equals(PreviewRenderer.pos)) {
            PreviewRenderer.active = false;
            PreviewRenderer.cfg = null;
            PreviewRenderer.pos = null;
        }
    }

    @Override
    public boolean onBlockActivated(World worldIn, BlockPos pos, IBlockState state, EntityPlayer playerIn,
                                    EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (worldIn.isRemote) {
            GuiPlatformConfig.open(pos);
        }
        return true;
    }
}
