package g_mungus.zps.blockentity.light_pipe;

import g_mungus.zps.ZPSMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

public interface RadioBlockEntity {
    void cycleFrequencies();
    int getRadioFrequency();
    void updateAntennaStrength(LevelAccessor level);

    boolean hasEnoughAntennas();

    List<Integer> FREQUENCIES = List.of(5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55, 60, 65, 70, 75, 80);
    TagKey<Block> ANTENNAE = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(ZPSMod.MOD_ID, "antennae"));

    default int getRequiredAntennaStrength() {
        return (int)((getRadioFrequency() / 20.0) + 1.5);
    }

    void setFrequencyIndex(int frequencyIndex);

    @Mod.EventBusSubscriber
    class Subscriber {
        @SubscribeEvent
        static void onNeighborUpdate(BlockEvent.NeighborNotifyEvent event) {
            var level = event.getLevel();
            BlockPos pos = event.getPos().below();

            while (isValidAntennaBlock(level.getBlockState(pos))) {
                pos = pos.below();
            }

            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof RadioBlockEntity radioBlockEntity) {
                radioBlockEntity.updateAntennaStrength(level);
            }
        }
    }

    static boolean isValidAntennaBlock(BlockState state) {
        if (state.hasProperty(BlockStateProperties.AXIS)
                && !state.getValue(BlockStateProperties.AXIS).equals(Direction.Axis.Y)
        ) {
            return false;
        } else {
            return state.is(ANTENNAE);
        }
    }
}
