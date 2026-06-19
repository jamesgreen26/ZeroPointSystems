package g_mungus.zps.compat;

import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import g_mungus.zps.ZPSMod;
import g_mungus.zps.commands.api.RegisterScriptCommandsEvent;
import g_mungus.zps.commands.api.ScriptGetter;
import g_mungus.zps.commands.api.ScriptMapper;
import g_mungus.zps.commands.api.ScriptMapper2;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.server.command.EnumArgument;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.UUID;

public final class SableCompat {

    private SableCompat() {
    }

    static void registerScriptCommands(RegisterScriptCommandsEvent event) {
        event.register(new ScriptGetter<>(
                "sublevel",
                SubLevelAccess.class,
                ZPSMod.resource("sublevel"),
                context -> {
                    SubLevelAccess subLevel = SableCompanion.INSTANCE.getContaining(context.level(), context.pos());
                    return subLevel == null ? NO_SUBLEVEL : subLevel;
                },
                null
        ));

        event.register(new ScriptMapper<>(
                "name",
                SubLevelAccess.class,
                String.class,
                ZPSMod.resource("sublevel"),
                ZPSMod.resource("string"),
                (subLevel, context) -> subLevel == NO_SUBLEVEL || subLevel.getName() == null ? "" : subLevel.getName()
        ));

        event.register(new ScriptMapper<>(
                "id",
                SubLevelAccess.class,
                String.class,
                ZPSMod.resource("sublevel"),
                ZPSMod.resource("string"),
                (subLevel, context) -> subLevel == NO_SUBLEVEL ? "" : subLevel.getUniqueId().toString()
        ));

        event.register(new ScriptMapper<>(
                "pos",
                SubLevelAccess.class,
                Vec3.class,
                ZPSMod.resource("sublevel"),
                ZPSMod.resource("vec_pos"),
                (subLevel, context) -> subLevel == NO_SUBLEVEL
                        ? context.pos().getCenter()
                        : toMinecraft(subLevel.logicalPose().position())
        ));

        event.register(new ScriptMapper<>(
                "world_vel",
                SubLevelAccess.class,
                Vec3.class,
                ZPSMod.resource("sublevel"),
                ZPSMod.resource("vec_dir"),
                (subLevel, context) -> {
                    if (subLevel == NO_SUBLEVEL) {
                        return Vec3.ZERO;
                    }

                    Position position = context.pos().getCenter();
                    return SableCompanion.INSTANCE.getVelocity(context.level(), position);
                }
        ));

        event.register(new ScriptMapper<>(
                "local_vel",
                SubLevelAccess.class,
                Vec3.class,
                ZPSMod.resource("sublevel"),
                ZPSMod.resource("vec_dir"),
                (subLevel, context) -> {
                    if (subLevel == NO_SUBLEVEL) {
                        return Vec3.ZERO;
                    }

                    Position position = context.pos().getCenter();
                    Vec3 worldVelocity = SableCompanion.INSTANCE.getVelocity(context.level(), position);
                    Vector3d localVelocity = new Vector3d(worldVelocity.x, worldVelocity.y, worldVelocity.z);
                    subLevel.logicalPose().orientation().transformInverse(localVelocity);
                    return new Vec3(localVelocity.x, localVelocity.y, localVelocity.z);
                }
        ));

        event.register(new ScriptMapper<>(
                "bounding_box",
                SubLevelAccess.class,
                Vec3.class,
                ZPSMod.resource("sublevel"),
                ZPSMod.resource("vec_box"),
                (subLevel, context) -> {
                    if (subLevel == NO_SUBLEVEL) {
                        return Vec3.ZERO;
                    }

                    BoundingBox3dc bounds = subLevel.boundingBox();
                    return new Vec3(
                            bounds.maxX() - bounds.minX(),
                            bounds.maxY() - bounds.minY(),
                            bounds.maxZ() - bounds.minZ()
                    );
                }
        ));

        event.register(new ScriptMapper2<>(
                "dir",
                SubLevelAccess.class,
                Vec3.class,
                ZPSMod.resource("sublevel"),
                ZPSMod.resource("vec_dir"),
                "direction",
                (subLevel, context) -> subLevelDirection(subLevel, context.argumentValue()),
                EnumArgument.enumArgument(Direction.class),
                Direction.class,
                ZPSMod.resource("direction")
        ));

        // TODO: VSCompat registers a "mass" mapper (ship inertia mass scaled by its
        //  ship-to-world volume). The Sable companion API exposes no inertia/mass accessor
        //  on SubLevelAccess, so there is no exact Sable equivalent to register here.
    }

    /// Only call after verifying that Sable is loaded.
    /// Sable equivalent of VSCompat#shipToWorld(ServerLevel, BlockPos).
    static Vec3 subLevelToWorld(ServerLevel level, BlockPos pos) {
        Position center = pos.getCenter();
        return SableCompanion.INSTANCE.projectOutOfSubLevel(level, center);
    }

    /// Only call after verifying that Sable is loaded.
    /// Sable equivalent of VSCompat#shipToWorld(Level, Vec3).
    static Vec3 subLevelToWorld(Level level, Vec3 pos) {
        Position position = pos;
        return SableCompanion.INSTANCE.projectOutOfSubLevel(level, position);
    }

    /// Only call after verifying that Sable is loaded.
    /// Sable equivalent of VSCompat#worldToShip: transforms a world-space position into the
    /// local space of the sublevel managing anchorPos. Returns worldPos unchanged if anchorPos
    /// is not inside a sublevel.
    static Vec3 worldToSubLevel(Level level, BlockPos anchorPos, Vec3 worldPos) {
        SubLevelAccess subLevel = SableCompanion.INSTANCE.getContaining(level, anchorPos.getCenter());
        if (subLevel == null) return worldPos;
        return subLevel.logicalPose().transformPositionInverse(worldPos);
    }

    private static Vec3 subLevelDirection(SubLevelAccess subLevel, Direction direction) {
        Vec3 normal = Vec3.atLowerCornerOf(direction.getNormal());
        if (subLevel == NO_SUBLEVEL) {
            return normal;
        }

        return subLevel.logicalPose().transformNormal(normal).normalize();
    }

    private static Vec3 toMinecraft(Vector3dc vector) {
        return new Vec3(vector.x(), vector.y(), vector.z());
    }

    private static final SubLevelAccess NO_SUBLEVEL = new SubLevelAccess() {
        private final Pose3d pose = new Pose3d();
        private final BoundingBox3d bounds = new BoundingBox3d();

        @Override
        public @NotNull Pose3dc logicalPose() {
            return this.pose;
        }

        @Override
        public @NotNull Pose3dc lastPose() {
            return this.pose;
        }

        @Override
        public @NotNull BoundingBox3dc boundingBox() {
            return this.bounds;
        }

        @Override
        public @NotNull UUID getUniqueId() {
            return new UUID(0L, 0L);
        }

        @Override
        public @Nullable String getName() {
            return null;
        }
    };
}
