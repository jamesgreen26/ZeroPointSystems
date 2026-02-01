package g_mungus.zps.block;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public class SwitchPanelBlock extends FaceAttachedHorizontalDirectionalBlock {

    public static final BooleanProperty POWERED_0 = BooleanProperty.create("powered_0");
    public static final BooleanProperty POWERED_1 = BooleanProperty.create("powered_1");
    public static final BooleanProperty POWERED_2 = BooleanProperty.create("powered_2");
    public static final BooleanProperty POWERED_3 = BooleanProperty.create("powered_3");

    public SwitchPanelBlock(Properties arg) {
        super(arg);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(FACE, AttachFace.WALL)
                .setValue(POWERED_0, false)
                .setValue(POWERED_1, false)
                .setValue(POWERED_2, false)
                .setValue(POWERED_3, false)
        );
    }

    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> arg) {
        arg.add(FACE, FACING, POWERED_0, POWERED_1, POWERED_2, POWERED_3);
    }
}
