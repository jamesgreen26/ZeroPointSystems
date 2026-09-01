package g_mungus.zps.block.gas.core;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jetbrains.annotations.NotNull;
import org.valkyrienskies.kelvin.api.DuctNode;
import org.valkyrienskies.kelvin.api.DuctNodePos;
import org.valkyrienskies.kelvin.util.INodeBlock;

/**
 * Java-side view of Kelvin's {@link INodeBlock}.
 *
 * <p>Kelvin is Kotlin, and Java cannot inherit a Kotlin interface's default methods. Every
 * method therefore has to be re-declared here and forwarded to the {@code DefaultImpls} class
 * the Kotlin compiler generates. Anything of ours that implements a Kelvin interface needs
 * this same shape.
 */
public interface KNodeBlock extends INodeBlock {
    EnumProperty<DuctConnectionType> NORTH_CONNECTION = EnumProperty.create("north", DuctConnectionType.class);
    EnumProperty<DuctConnectionType> SOUTH_CONNECTION = EnumProperty.create("south", DuctConnectionType.class);
    EnumProperty<DuctConnectionType> EAST_CONNECTION = EnumProperty.create("east", DuctConnectionType.class);
    EnumProperty<DuctConnectionType> WEST_CONNECTION = EnumProperty.create("west", DuctConnectionType.class);
    EnumProperty<DuctConnectionType> UP_CONNECTION = EnumProperty.create("up", DuctConnectionType.class);
    EnumProperty<DuctConnectionType> DOWN_CONNECTION = EnumProperty.create("down", DuctConnectionType.class);

    @Override
    default void nodePlace(
            @NotNull BlockState blockState,
            @NotNull Level level,
            @NotNull BlockPos blockPos,
            @NotNull BlockState oldState,
            boolean isMoving
    ) {
        INodeBlock.DefaultImpls.nodePlace(this, blockState, level, blockPos, oldState, isMoving);
    }

    @Override
    default void nodeAddClient(
            @NotNull BlockState blockState,
            @NotNull Level level,
            @NotNull BlockPos blockPos
    ) {
        INodeBlock.DefaultImpls.nodeAddClient(this, blockState, level, blockPos);
    }

    @Override
    default void nodeRemoveClient(
            @NotNull BlockState blockState,
            @NotNull Level level,
            @NotNull BlockPos blockPos
    ) {
        INodeBlock.DefaultImpls.nodeRemoveClient(this, blockState, level, blockPos);
    }

    @Override
    default void nodeRemove(
            @NotNull BlockState blockState,
            @NotNull Level level,
            @NotNull BlockPos blockPos,
            @NotNull BlockState newState,
            boolean isMoving
    ) {
        INodeBlock.DefaultImpls.nodeRemove(this, blockState, level, blockPos, newState, isMoving);
    }

    @Override
    default @NotNull DuctNode createNode(@NotNull DuctNodePos ductNodePos) {
        return INodeBlock.DefaultImpls.createNode(this, ductNodePos);
    }

    @Override
    default boolean canConnectTo(
            @NotNull BlockPos self,
            @NotNull BlockPos other,
            @NotNull Direction direction,
            @NotNull BlockGetter level
    ) {
        return INodeBlock.DefaultImpls.canConnectTo(this, self, other, direction, level);
    }
}
