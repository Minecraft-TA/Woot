package ipsis.woot.machines.stamper;

import ipsis.woot.debug.IWootDebug;
import ipsis.woot.util.WootBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.item.ItemUseContext;
import net.minecraft.state.DirectionProperty;
import net.minecraft.state.StateContainer;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nullable;
import java.util.List;

public class BlockStamper extends WootBlock implements IWootDebug {

    public static final DirectionProperty FACING = BlockHorizontal.HORIZONTAL_FACING;

    public static final String BASENAME = "stamper";
    public BlockStamper() {
        super(Block.Properties.create(Material.ROCK), BASENAME);
        setDefaultState(getStateContainer().getBaseState().with(FACING, EnumFacing.NORTH));
    }

    @Nullable
    @Override
    public IBlockState getStateForPlacement(BlockItemUseContext context) {
        return this.getDefaultState().with(FACING, context.getPlacementHorizontalFacing().getOpposite());
    }

    @Override
    protected void fillStateContainer(StateContainer.Builder<Block, IBlockState> builder) {
        builder.add(FACING);
    }

    /**
     * IWootDebug
     */
    @Override
    public List<String> getDebugText(List<String> debug, ItemUseContext itemUseContext) {
        debug.add("====> BlockStamper - " + getStateContainer().getBaseState().get(FACING));
        return debug;
    }
}