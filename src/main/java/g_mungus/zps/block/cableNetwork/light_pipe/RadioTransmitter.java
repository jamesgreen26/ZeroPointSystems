package g_mungus.zps.block.cableNetwork.light_pipe;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.blockentity.light_pipe.RadioTransmitterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

@Mod.EventBusSubscriber
public class RadioTransmitter extends AbstractRadioBlock {

    public static final TagKey<Block> ANTENNAE = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(ZPSMod.MOD_ID, "antennae"));

    public RadioTransmitter(Properties arg) {
        super(arg);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof RadioTransmitterBlockEntity transmitter) {
                transmitter.clearTransmission();
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @SubscribeEvent
    public static void onNeighborUpdate(BlockEvent.NeighborNotifyEvent event) {
        var level = event.getLevel();
        BlockPos pos = event.getPos().below();

        while (isValidAntennaBlock(level.getBlockState(pos))) {
            pos = pos.below();
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof RadioTransmitterBlockEntity transmitter) {
            transmitter.updateAntennaStrength(level);
        }
    }

    public static boolean isValidAntennaBlock(BlockState state) {
        if (state.hasProperty(BlockStateProperties.AXIS)
                && !state.getValue(BlockStateProperties.AXIS).equals(Direction.Axis.Y)
        ) {
            return false;
        } else {
            return state.is(ANTENNAE);
        }
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos arg, BlockState arg2) {
        return new RadioTransmitterBlockEntity(arg, arg2);
    }
}

