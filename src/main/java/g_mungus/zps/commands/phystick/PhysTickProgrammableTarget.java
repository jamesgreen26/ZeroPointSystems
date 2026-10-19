package g_mungus.zps.commands.phystick;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.commands.api.ScriptTarget;
import g_mungus.zps.commands.api.TargetApplicability;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/** For blocks that take {@code on_phys_tick}: those whose block entity is a {@link PhysTickProgrammable}. */
public enum PhysTickProgrammableTarget implements TargetApplicability {
    INSTANCE;

    public static final Type<PhysTickProgrammableTarget> TYPE =
            new Type<>(ZPSMod.resource("phys_tick_programmable"), StreamCodec.unit(INSTANCE));

    @Override
    public boolean appliesTo(Level level, ScriptTarget target) {
        return level.getBlockEntity(target.pos()) instanceof PhysTickProgrammable;
    }

    @Override
    public Type<?> type() {
        return TYPE;
    }

    /** ZPS's blocks whose block entity is a {@link PhysTickProgrammable}, found once by making one of each. */
    @Override
    public Set<ResourceLocation> listedBlocks() {
        Set<ResourceLocation> blocks = listed;
        if (blocks == null) {
            Set<ResourceLocation> found = new HashSet<>();
            for (Block block : BuiltInRegistries.BLOCK) {
                ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
                if (id.getNamespace().equals(ZPSMod.MOD_ID) && block instanceof EntityBlock entityBlock
                        && entityBlock.newBlockEntity(BlockPos.ZERO, block.defaultBlockState()) instanceof PhysTickProgrammable) {
                    found.add(id);
                }
            }
            blocks = Set.copyOf(found);
            listed = blocks;
        }
        return blocks;
    }

    private static volatile @Nullable Set<ResourceLocation> listed;
}
