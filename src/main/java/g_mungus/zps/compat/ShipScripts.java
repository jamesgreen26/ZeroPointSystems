package g_mungus.zps.compat;

import g_mungus.munguscript.language.builtin.BuiltInTypes;
import g_mungus.munguscript.language.node.ScriptNode;
import g_mungus.munguscript.language.node.SimpleGetter;
import g_mungus.munguscript.language.type.ScriptType;
import g_mungus.zps.commands.api.RegisterScriptCommandsEvent;
import g_mungus.zps.commands.api.RegisterScriptTypesEvent;
import g_mungus.zps.commands.api.TargetApplicability;
import g_mungus.zps.commands.api.ZPSNodes;
import g_mungus.zps.commands.api.ZPSScriptContext;
import g_mungus.zps.commands.api.ZPSScriptTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.server.command.EnumArgument;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The {@code ship} script words, the same for a Valkyrien Skies ship and a Sable sublevel. Each grid mod gives a
 * {@link ShipView} of the grid a target is on, as the server tick sees it; inside a phys tick program the words
 * read the physics step instead, through {@link GridPhysics}.
 * <p>
 * Everything is in {@link GridPhysics}' units on both mods: speeds in blocks per second, turning in radians per
 * second about world axes, and masses with a plain block weighing 1.
 */
public final class ShipScripts {

    private ShipScripts() {
    }

    /** A grid as the ship words read it. */
    public interface ShipView {

        String name();

        String id();

        /** The grid's own extent, in blocks along its own axes. */
        Vec3 boundingBox();

        /** Where the grid is in the world: the position of its pose. */
        Vec3 position();

        /** How fast the centre of mass moves through the world, in blocks per second. */
        Vec3 worldVelocity();

        /** How fast the grid turns, about world axes, in radians per second. */
        Vec3 angularVelocity();

        /** The centre of mass, in the grid's block space. */
        Vec3 centreOfMass();

        double mass();

        Vec3 toWorldDirection(Vec3 direction);

        Vec3 toGridDirection(Vec3 direction);
    }

    /** A ship. Reads as its name where text is wanted. */
    static final ScriptType<ShipView> SHIP = ScriptType.opaque(ZPSScriptTypes.key("ship"), ShipView.class)
            .usableAs(BuiltInTypes.STRING, ShipView::name);

    static void registerScriptTypes(RegisterScriptTypesEvent event) {
        event.register(SHIP);
    }

    /**
     * @param gameView      the grid {@code context}'s target is on, as the server tick sees it, or null for none
     * @param applicability what the {@code ship} getter is for, if the grid mod can tell
     */
    static void registerScriptCommands(RegisterScriptCommandsEvent event,
                                       Function<ZPSScriptContext, @Nullable ShipView> gameView,
                                       @Nullable TargetApplicability applicability) {
        // Read off the physics step, these may run in one; the rest are worked out on the tick before it.
        Consumer<ScriptNode> safe = event::registerPhysTickSafe;

        SimpleGetter<ShipView> getter = ZPSNodes.getter("ship", SHIP, context -> {
            GridPhysics physics = context.physics();
            if (physics != null) {
                return new PhysicsView(physics);
            }
            ShipView view = gameView.apply(context);
            return view == null ? new NoShip(context.pos()) : view;
        });
        safe.accept(applicability == null ? getter : getter.withApplicability(applicability));

        event.register(ZPSNodes.mapper("name", SHIP, BuiltInTypes.STRING, (ship, context) -> ship.name()));
        event.register(ZPSNodes.mapper("id", SHIP, BuiltInTypes.STRING, (ship, context) -> ship.id()));
        event.register(ZPSNodes.mapper("bounding_box", SHIP, ZPSScriptTypes.VEC_BOX,
                (ship, context) -> ship.boundingBox()));

        safe.accept(ZPSNodes.mapper("pos", SHIP, ZPSScriptTypes.VEC_POS, (ship, context) -> ship.position()));
        safe.accept(ZPSNodes.mapper("world_vel", SHIP, ZPSScriptTypes.VEC_DIR,
                (ship, context) -> ship.worldVelocity()));
        safe.accept(ZPSNodes.mapper("local_vel", SHIP, ZPSScriptTypes.VEC_DIR,
                (ship, context) -> ship.toGridDirection(ship.worldVelocity())));
        safe.accept(ZPSNodes.mapper("ang_vel", SHIP, ZPSScriptTypes.VEC_DIR,
                (ship, context) -> ship.angularVelocity()));
        safe.accept(ZPSNodes.mapper("local_ang_vel", SHIP, ZPSScriptTypes.VEC_DIR,
                (ship, context) -> ship.toGridDirection(ship.angularVelocity())));
        safe.accept(ZPSNodes.mapper("com", SHIP, ZPSScriptTypes.VEC_POS, (ship, context) -> ship.centreOfMass()));
        safe.accept(ZPSNodes.mapper("mass", SHIP, BuiltInTypes.DOUBLE, (ship, context) -> ship.mass()));
        // A face of the ship, as a direction in the world
        safe.accept(ZPSNodes.rawArgumentMapper("dir", SHIP, ZPSScriptTypes.VEC_DIR, "direction",
                EnumArgument.enumArgument(Direction.class), Direction.class,
                (ship, direction, context) ->
                        ship.toWorldDirection(Vec3.atLowerCornerOf(direction.getNormal())).normalize()));
    }

    /** The grid of a physics step. What only the server tick knows is never read here, see above. */
    private record PhysicsView(GridPhysics physics) implements ShipView {
        /** What a ship read as text in a program gives: {@code name} itself is worked out on the tick instead. */
        @Override
        public String name() {
            return "[Unknown Ship]";
        }

        @Override
        public String id() {
            return "";
        }

        @Override
        public Vec3 boundingBox() {
            return Vec3.ZERO;
        }

        @Override
        public Vec3 position() {
            return physics.position();
        }

        @Override
        public Vec3 worldVelocity() {
            return physics.linearVelocity();
        }

        @Override
        public Vec3 angularVelocity() {
            return physics.angularVelocity();
        }

        @Override
        public Vec3 centreOfMass() {
            return physics.centreOfMass();
        }

        @Override
        public double mass() {
            return physics.mass();
        }

        @Override
        public Vec3 toWorldDirection(Vec3 direction) {
            return physics.toWorldDirection(direction);
        }

        @Override
        public Vec3 toGridDirection(Vec3 direction) {
            return physics.toGridDirection(direction);
        }
    }

    /** No grid: a target in the world, which stands still where it is, along the world's axes. */
    private record NoShip(BlockPos pos) implements ShipView {
        @Override
        public String name() {
            return "";
        }

        @Override
        public String id() {
            return "";
        }

        @Override
        public Vec3 boundingBox() {
            return Vec3.ZERO;
        }

        @Override
        public Vec3 position() {
            return pos.getCenter();
        }

        @Override
        public Vec3 worldVelocity() {
            return Vec3.ZERO;
        }

        @Override
        public Vec3 angularVelocity() {
            return Vec3.ZERO;
        }

        @Override
        public Vec3 centreOfMass() {
            return pos.getCenter();
        }

        @Override
        public double mass() {
            return 0;
        }

        @Override
        public Vec3 toWorldDirection(Vec3 direction) {
            return direction;
        }

        @Override
        public Vec3 toGridDirection(Vec3 direction) {
            return direction;
        }
    }
}
