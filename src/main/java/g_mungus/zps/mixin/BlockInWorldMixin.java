package g_mungus.zps.mixin;

import g_mungus.zps.commands.content.ZPSScriptMappers;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(BlockInWorld.class)
public class BlockInWorldMixin implements ZPSScriptMappers.BlockInWorldMutable {


    @Shadow
    private BlockState state;

    @Shadow
    private boolean cachedEntity;


    @Override
    public void zps$setState(BlockState state) {
        this.state = state;
    }

    @Override
    public void zps$setCachedEntity(boolean b) {
        cachedEntity = b;
    }
}
