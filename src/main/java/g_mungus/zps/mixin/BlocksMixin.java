package g_mungus.zps.mixin;

import g_mungus.zps.block.ZPSBrushableBlock;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BrushableBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Swaps vanilla's two {@link BrushableBlock} instances (suspicious sand and suspicious gravel) for
 * {@link ZPSBrushableBlock}, which survives falling and piston movement with its buried loot.
 *
 * <p>This class must not gain static state — it is merged into {@code Blocks}, whose static
 * initialiser is running while the handler below executes. See {@link ZPSBrushableBlock}.
 */
@Mixin(Blocks.class)
public class BlocksMixin {

    /**
     * {@code @Redirect} rather than {@code @WrapOperation}: the original constructor is never
     * called, so there is no reason to have MixinExtras synthesise an {@code Operation} inside a
     * static initialiser.
     *
     * <p>{@code require}/{@code allow} of exactly 2 turns a Mojang refactor — or a third vanilla
     * brushable block — into a loud boot failure rather than a silent half-patch.
     */
    @Redirect(
            method = "<clinit>",
            at = @At(
                    value = "NEW",
                    target = "(Lnet/minecraft/world/level/block/Block;"
                            + "Lnet/minecraft/world/level/block/state/BlockBehaviour$Properties;"
                            + "Lnet/minecraft/sounds/SoundEvent;"
                            + "Lnet/minecraft/sounds/SoundEvent;)"
                            + "Lnet/minecraft/world/level/block/BrushableBlock;"
            ),
            require = 2,
            allow = 2
    )
    private static BrushableBlock zps$replaceBrushableBlocks(
            Block turnsInto,
            BlockBehaviour.Properties properties,
            SoundEvent brushSound,
            SoundEvent brushCompletedSound
    ) {
        return new ZPSBrushableBlock(turnsInto, properties, brushSound, brushCompletedSound);
    }
}
