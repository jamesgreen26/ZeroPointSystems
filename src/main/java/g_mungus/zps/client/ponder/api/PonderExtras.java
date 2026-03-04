package g_mungus.zps.client.ponder.api;

import net.createmod.ponder.api.level.PonderLevel;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

public class PonderExtras {

    public static Selection selectBlocks(SceneBuilder builder, SceneBuildingUtil util, Block... blocks) {
        PonderLevel level = builder.getScene().getWorld();
        List<Selection> positions = new ArrayList<>();

        util.select().everywhere().forEach(blockPos -> {
            if (Arrays.stream(blocks).anyMatch(it -> level.getBlockState(blockPos).is(it))) {
                positions.add(util.select().position(blockPos));
            }
        });

        Selection out = emptySelection(util);
        for (var s : positions) {
            out = out.add(s);
        }
        return out;
    }


    public static Selection emptySelection(SceneBuildingUtil util) {
        Selection s = util.select().position(0, 0, 0);
        return s.substract(s);
    }

    public static void modifyBlockStates(SceneBuilder builder, SceneBuildingUtil util, Function<BlockState, BlockState> mutator) {
        PonderLevel level = builder.getScene().getWorld();

        util.select().everywhere().forEach(blockPos -> {
            BlockState oldState = level.getBlockState(blockPos);
            BlockState newState = mutator.apply(oldState);
            if (!oldState.equals(newState)) {
                builder.world().setBlock(blockPos, newState, false);
            }
        });
    }
}
