package g_mungus.zps.contraption;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import g_mungus.zps.blockentity.ServoMotorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.LevelReader;

/**
 * Identifies a Servo Motor by its place in the contraption hierarchy: a {@code rootMotorPos} (a real
 * world block position) followed by the chain of contraption-LOCAL cells descending through each
 * nesting level to the target motor. A root motor's path has an empty {@code nestedCells} list.
 *
 * <p>Interaction packets carry this instead of a single motor position, because a nested motor lives
 * at a contraption-local cell that {@code level.getBlockEntity} cannot resolve directly. {@link #of}
 * builds the path by walking up the host chain (client send side); {@link #resolve} walks back down
 * to the live motor instance (both sides, via {@link ServoMotorBlockEntity#getNestedMotor}).
 */
public record ContraptionPath(BlockPos rootMotorPos, List<BlockPos> nestedCells) {

	public static final StreamCodec<RegistryFriendlyByteBuf, ContraptionPath> STREAM_CODEC = new StreamCodec<>() {
		@Override
		public ContraptionPath decode(RegistryFriendlyByteBuf buffer) {
			BlockPos root = buffer.readBlockPos();
			int n = buffer.readVarInt();
			List<BlockPos> cells = new ArrayList<>(n);
			for (int i = 0; i < n; i++)
				cells.add(buffer.readBlockPos());
			return new ContraptionPath(root, cells);
		}

		@Override
		public void encode(RegistryFriendlyByteBuf buffer, ContraptionPath path) {
			buffer.writeBlockPos(path.rootMotorPos);
			buffer.writeVarInt(path.nestedCells.size());
			for (BlockPos cell : path.nestedCells)
				buffer.writeBlockPos(cell);
		}
	};

	/** The path identifying {@code motor}, found by walking up its host chain to the root. */
	public static ContraptionPath of(ServoMotorBlockEntity motor) {
		List<BlockPos> cells = new ArrayList<>();
		ServoMotorBlockEntity m = motor;
		ServoMotorBlockEntity host;
		while ((host = m.hostMotor()) != null) {
			cells.add(0, m.getBlockPos()); // m's cell in the host's local space
			m = host;
		}
		return new ContraptionPath(m.getBlockPos(), cells);
	}

	/** The live motor this path names, or {@code null} if any link is missing. Works on either side. */
	@Nullable
	public ServoMotorBlockEntity resolve(LevelReader level) {
		if (!(level.getBlockEntity(rootMotorPos) instanceof ServoMotorBlockEntity motor))
			return null;
		for (BlockPos cell : nestedCells) {
			motor = motor.getNestedMotor(cell);
			if (motor == null)
				return null;
		}
		return motor;
	}
}
