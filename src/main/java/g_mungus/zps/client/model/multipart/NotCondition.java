package g_mungus.zps.client.model.multipart;

import java.util.function.Predicate;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

@OnlyIn(Dist.CLIENT)
public class NotCondition implements net.minecraft.client.renderer.block.model.multipart.Condition {
    public static final String TOKEN = "NOT";
    private final net.minecraft.client.renderer.block.model.multipart.Condition condition;

    public NotCondition(net.minecraft.client.renderer.block.model.multipart.Condition condition) {
        this.condition = condition;
    }

    @Override
    public @NotNull Predicate<BlockState> getPredicate(@NotNull StateDefinition<Block, BlockState> stateDefinition) {
        return this.condition.getPredicate(stateDefinition).negate();
    }
}
