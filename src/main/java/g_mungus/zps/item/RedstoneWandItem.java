package g_mungus.zps.item;

import g_mungus.zps.commands.content.executors.SetRedstoneCommand;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

public class RedstoneWandItem extends Item {
    public RedstoneWandItem(Properties arg) {
        super(arg);
    }

    @Override
    public boolean isFoil(@NotNull ItemStack stack) {
        return true;
    }

    @Override
    public @NotNull InteractionResult useOn(UseOnContext arg) {
        Level level = arg.getLevel();
        BlockPos blockpos = arg.getClickedPos();

        if (level instanceof ServerLevel serverLevel) {
            int existing = SetRedstoneCommand.getRedstonePowerAt(serverLevel, blockpos);
            SetRedstoneCommand.setRedstone(serverLevel, blockpos, existing == 0 ? 15 : 0);
        } else {
            Vec3 pos = blockpos.getCenter();
            level.addParticle(DustParticleOptions.REDSTONE, pos.x, pos.y + 0.6, pos.z, 0, 0, 0);
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
