package g_mungus.zps.mixin;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Set;

/**
 * Opens up {@code BlockEntityType#validBlocks} so a mod block can host a vanilla block entity.
 *
 * <p>Newer versions have an event for this ({@code BlockEntityTypeAddBlocksEvent}); here the set is
 * a private final field baked in at registration, so it has to be replaced outright. A block entity
 * whose type does not list its block is discarded on chunk load, taking its contents with it.
 */
@Mixin(BlockEntityType.class)
public interface BlockEntityTypeAccessor {

    @Accessor("validBlocks")
    Set<Block> zps$getValidBlocks();

    @Mutable
    @Accessor("validBlocks")
    void zps$setValidBlocks(Set<Block> validBlocks);
}
