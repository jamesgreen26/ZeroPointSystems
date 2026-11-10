package g_mungus.zps.mixin;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Reaches the private {@code Player#turtleHelmetTick}, for gametests to drive a fake player's helmet. */
@Mixin(Player.class)
public interface PlayerInvoker {

    @Invoker("turtleHelmetTick")
    void zps$turtleHelmetTick();
}
