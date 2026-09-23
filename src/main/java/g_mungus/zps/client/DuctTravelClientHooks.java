package g_mungus.zps.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.entity.DuctTravelEntity;
import g_mungus.zps.mixin.LivingEntityRendererAccessor;
import g_mungus.zps.networking.DuctCycleC2SPacket;
import g_mungus.zps.networking.ZPSGamePackets;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.ParrotOnShoulderLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.layers.SpinAttackEffectLayer;
import net.minecraft.client.renderer.entity.layers.StuckInBodyLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayList;
import java.util.List;


/**
 * The client half of duct travel: hiding whoever is inside one, and turning left/right into a
 * request for the next vent.
 *
 * <p>Movement keys are read directly rather than through a ZPS keybind. A rider is a passenger of
 * a vehicle that nobody controls, so vanilla already throws their movement input away — which
 * leaves the familiar keys free to mean something else without a binding to explain or a conflict
 * to resolve.
 */
public final class DuctTravelClientHooks {

    /**
     * How long the screen has to stay dark before the crawl is worth hearing. A hop between
     * neighbouring vents is over in a couple of ticks and wants no more than the clank it already
     * gets; a long haul across the base should sound like one.
     */
    private static final int FOOTSTEP_DELAY = 10;
    /** Ticks between footfalls once they start. */
    private static final int FOOTSTEP_INTERVAL = 6;

    /** How close to the duct's axis a look has to be before its yaw stops meaning anything. */
    private static final float POLE_EPSILON = 1.0e-5f;
    /** How far off the axis to lean when reading a yaw that the axis itself cannot give. */
    private static final float POLE_NUDGE = 0.1f;

    /** Ticks a held key waits before it starts stepping through the list on its own. */
    private static final int REPEAT_DELAY = 8;
    /** Ticks between steps once it does. */
    private static final int REPEAT_INTERVAL = 6;

    private static boolean wasLeft;
    private static boolean wasRight;
    private static int heldTicks;
    private static boolean wasRiding;

    /**
     * Where the body goes while the layers run, in model pixels: far enough that nothing hung on it
     * lands inside the far plane. Not merely hidden, because a layer that draws its own thing off a
     * body part — a backtank, a cape, a quiver — never asks whether that part is visible.
     */
    private static final float BODY_EXILE = 1.0e7f;

    private DuctTravelClientHooks() {
    }

    // --- hiding -----------------------------------------------------------------------------

    /**
     * Draws a rider as a head in the grille and nothing else.
     *
     * <p>Vanilla's own pass is cancelled outright rather than having its parts switched off one by
     * one, because the parts are only half of a player: armour, held items, capes and the name tag
     * all come from separate layers that know nothing about model visibility, and every one of them
     * would be left hanging in the wall. Cancelling takes the lot, and the head is drawn back by
     * hand — and then the renderer's own layers are run over it with the body put out of reach,
     * so that whatever belongs on a head, from any mod, is drawn where it always is.
     *
     * <p>The transform is vanilla's minus the drop onto a body. {@code LivingEntityRenderer} turns
     * to face the body, flips into the y-down model space, then lowers everything by 1.501 blocks
     * so the neck lands at the top of a torso. Here there is no torso: skipping that drop puts the
     * head cube exactly on the head-sized hitbox. The pose is turned by the look direction rather
     * than the body's, and the head part left unrotated about y, so the head faces wherever the
     * player does.
     */
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        if (!(player.getVehicle() instanceof DuctTravelEntity duct)) {
            return;
        }
        event.setCanceled(true);

        // Between vents there is nothing to draw. They are not at the vent they left and not yet
        // at the one ahead, so showing a head at either end would be a lie about where they are.
        if (duct.isTravelling()) {
            return;
        }

        LocalPlayer viewer = Minecraft.getInstance().player;
        if (!(player instanceof AbstractClientPlayer rider)
                || viewer == null
                || rider.isInvisibleTo(viewer)) {
            return;
        }

        PlayerRenderer renderer = event.getRenderer();
        PlayerModel<AbstractClientPlayer> model = renderer.getModel();
        PoseStack poseStack = event.getPoseStack();
        float partialTick = event.getPartialTick();

        float headYaw = Mth.rotLerp(partialTick, rider.yHeadRotO, rider.yHeadRot);
        float headPitch = Mth.lerp(partialTick, rider.xRotO, rider.getXRot());

        Direction facing = duct.getVentFacing();
        Quaternionf frame = ventFrame(facing);
        Vec3 rotation = frameRotation(frame, headPitch, headYaw);

        poseStack.pushPose();
        // Move the pivot half a head back down the duct, so the cube ends up centred on the vent
        // face — and so on the hitbox — whichever way it now grows.
        float half = DuctTravelEntity.HEAD_SIZE / 2.0f;
        poseStack.translate(-half * facing.getStepX(),
                half * (1.0f - facing.getStepY()),
                -half * facing.getStepZ());
        poseStack.mulPose(frame);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - (float) rotation.y));
        poseStack.scale(-1.0f, -1.0f, 1.0f);

        model.head.setPos(0.0f, 0.0f, 0.0f);
        model.head.xRot = (float) rotation.x * Mth.DEG_TO_RAD;
        model.head.yRot = 0.0f;
        model.head.zRot = 0.0f;
        model.head.visible = true;
        model.hat.copyFrom(model.head);
        model.hat.visible = true;

        VertexConsumer consumer = event.getMultiBufferSource()
                .getBuffer(model.renderType(renderer.getTextureLocation(rider)));
        int overlay = LivingEntityRenderer.getOverlayCoords(rider, 0.0f);
        model.head.render(poseStack, consumer, event.getPackedLight(), overlay);
        model.hat.render(poseStack, consumer, event.getPackedLight(), overlay);

        renderHeadLayers(renderer, model, rider, poseStack, event.getMultiBufferSource(),
                event.getPackedLight(), partialTick, (float) rotation.x);

        poseStack.popPose();
    }

    /** Nor are your own hands, when the rest of you is a pair of eyes in a grille. */
    public static void onRenderHand(RenderHandEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && player.getVehicle() instanceof DuctTravelEntity) {
            event.setCanceled(true);
        }
    }

    // --- cycling ----------------------------------------------------------------------------

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof LocalPlayer player)) {
            return;
        }

        if (!(player.getVehicle() instanceof DuctTravelEntity)) {
            if (wasRiding) {
                DuctTravelClientState.clear();
                DuctTravelFade.reset();
                wasRiding = false;
            }
            wasLeft = false;
            wasRight = false;
            heldTicks = 0;
            return;
        }
        wasRiding = true;
        DuctTravelFade.tick();
        playCrawlingStep();

        Minecraft minecraft = Minecraft.getInstance();
        // The arrow keys drive these bindings by default, so both readings of "left" work.
        boolean left = isDown(minecraft.options.keyLeft);
        boolean right = isDown(minecraft.options.keyRight);

        if (left == right) {
            // Neither, or both fighting each other: nothing to do, and nothing to repeat.
            wasLeft = left;
            wasRight = right;
            heldTicks = 0;
            return;
        }

        boolean pressed = left ? !wasLeft : !wasRight;
        wasLeft = left;
        wasRight = right;

        if (pressed) {
            heldTicks = 0;
            send(left ? -1 : 1);
            return;
        }

        heldTicks++;
        if (heldTicks >= REPEAT_DELAY && (heldTicks - REPEAT_DELAY) % REPEAT_INTERVAL == 0) {
            send(left ? -1 : 1);
        }
    }

    /**
     * Whatever is on the rider's head, drawn over it by the layers that always draw it.
     *
     * <p>Cancelling vanilla's pass took every layer with it, and the head slot is served by more
     * than one: the armour layer for a helmet, the custom-head layer for anything else equipped
     * there — Create's goggles are a plain item with a head model — and whatever a mod hangs on
     * the head through a layer of its own. Rather than copy each of them here and miss the next,
     * the renderer's real layers are run, over a head that is already posed, and the body is put
     * where nothing on it can be seen: hidden, and moved out past the far plane, since a layer that
     * draws off a body part never asks whether that part is showing. The armour layer copies part
     * poses onto its own models, so a chestplate goes into exile with the body while the helmet
     * stays on the head. Vanilla's layers that only ever hang on a body are skipped outright.
     */
    private static void renderHeadLayers(PlayerRenderer renderer, PlayerModel<AbstractClientPlayer> model,
                                         AbstractClientPlayer rider, PoseStack poseStack,
                                         MultiBufferSource buffers, int packedLight, float partialTick,
                                         float headPitch) {
        // Vanilla sets these on the model inside the render that was cancelled, so it is left holding
        // whatever the last player actually drawn put there — and before any player has been drawn
        // at all, EntityModel's own defaults, where young is true. Armour copies them, and its baby
        // branch drops the head a block and shrinks it: a helmet somewhere around the waist.
        model.young = rider.isBaby();
        model.riding = false;
        model.attackTime = 0.0f;

        // Every part of a player but the head and its hat; the cloak and the ears are the cape
        // layer's and the head's own.
        List<ModelPart> exiled = List.of(model.body, model.jacket,
                model.rightArm, model.leftArm, model.rightSleeve, model.leftSleeve,
                model.rightLeg, model.leftLeg, model.rightPants, model.leftPants);
        List<PartPose> poses = new ArrayList<>(exiled.size());
        for (ModelPart part : exiled) {
            poses.add(part.storePose());
            part.visible = false;
            part.setPos(0.0f, BODY_EXILE, 0.0f);
        }

        try {
            @SuppressWarnings("unchecked")
            List<RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>> layers =
                    ((LivingEntityRendererAccessor<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>) renderer)
                            .zps$getLayers();
            float ageInTicks = rider.tickCount + partialTick;
            for (RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> layer : layers) {
                if (isBodyOnly(layer)) {
                    continue;
                }
                // The head is already turned into the vent's frame by the pose stack and its part,
                // so relative to the body it is looking dead ahead.
                layer.render(poseStack, buffers, packedLight, rider, 0.0f, 0.0f, partialTick, ageInTicks,
                        0.0f, headPitch);
            }
        } finally {
            for (int i = 0; i < exiled.size(); i++) {
                exiled.get(i).loadPose(poses.get(i));
                exiled.get(i).visible = true;
            }
        }
    }

    /** Vanilla's layers that hang on the body alone, with nothing to offer a head. */
    private static boolean isBodyOnly(RenderLayer<?, ?> layer) {
        return layer instanceof CapeLayer
                || layer instanceof ElytraLayer
                || layer instanceof ItemInHandLayer
                || layer instanceof StuckInBodyLayer
                || layer instanceof ParrotOnShoulderLayer
                || layer instanceof SpinAttackEffectLayer;
    }

    /**
     * The frame the rider's body sits in.
     *
     * <p>Their spine runs along the duct they are wedged in, so the way the vent opens is which
     * way is up for them: out of a floor and they stand, out of a ceiling and they hang, out of a
     * wall and they lie on their side. Everything else about drawing the head is unchanged — it is
     * only the ground it is measured against that moves.
     *
     * <p>Any rotation taking up onto the facing will do. A different choice of spin about that
     * axis is cancelled out exactly by the yaw derived against it, so the shortest arc is as good
     * as any and is what {@code rotationTo} gives.
     */
    private static Quaternionf ventFrame(Direction facing) {
        return new Quaternionf().rotationTo(
                0.0f, 1.0f, 0.0f,
                facing.getStepX(), facing.getStepY(), facing.getStepZ());
    }

    /**
     * Where the player is looking, read as a pitch and yaw against the vent's frame rather than
     * the world's. Returned as (pitch, yaw) — a Vec3 only because there are two of them.
     *
     * <p>This is where the head is stopped from ever presenting its underside to the vent it is
     * poking out of. Going through a direction vector and back means the pitch comes out of an
     * {@code asin}, which cannot leave the quarter turn either side of level, and the yaw out of
     * an {@code atan2}, which swings a half turn the moment the look direction passes over the
     * frame's pole. The head turns over to follow rather than craning backwards — the same thing
     * that happens when you look straight up in Minecraft and keep going.
     */
    private static Vec3 frameRotation(Quaternionf frame, float pitch, float yaw) {
        Quaternionf inverse = new Quaternionf(frame).conjugate();
        Vector3f local = localLook(pitch, yaw, inverse);

        float framePitch = (float) -Math.toDegrees(Math.asin(Mth.clamp(local.y, -1.0f, 1.0f)));

        // Looking straight along the duct leaves the yaw with nothing to bite on: from there every
        // yaw points the same way, so atan2 is handed a pair of zeroes, answers zero, and the head
        // snaps round to face south instead of turning with the player. Read the yaw off a look
        // leaned a hair off the axis, which is the value it converges on from either side. Only
        // the yaw — the pitch still comes from where they are really looking, so the face stays
        // exactly on the axis.
        if (Mth.abs(local.x) < POLE_EPSILON && Mth.abs(local.z) < POLE_EPSILON) {
            local = localLook(pitch >= 0.0f ? pitch - POLE_NUDGE : pitch + POLE_NUDGE, yaw, inverse);
        }
        float frameYaw = (float) -Math.toDegrees(Math.atan2(local.x, local.z));

        return new Vec3(framePitch, frameYaw, 0.0);
    }

    private static Vector3f localLook(float pitch, float yaw, Quaternionf inverseFrame) {
        Vec3 look = Vec3.directionFromRotation(pitch, yaw);
        return new Vector3f((float) look.x, (float) look.y, (float) look.z).rotate(inverseFrame);
    }

    /**
     * Footfalls along the duct while the screen is dark.
     *
     * <p>Played through the local player, which puts them in this client's ears alone — nobody
     * outside hears someone crawling past inside the wall, and the vents' own clank stays the only
     * tell they get. The sound is whatever walking on a duct sounds like, so it follows the block
     * if its material ever changes. Along with the clank, it is one of the two sounds the journey
     * lets past its own silence.
     */
    private static void playCrawlingStep() {
        int dark = DuctTravelFade.darkTicks();
        if (dark < FOOTSTEP_DELAY || (dark - FOOTSTEP_DELAY) % FOOTSTEP_INTERVAL != 0) {
            return;
        }

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        SoundType duct = ModBlocks.GAS_DUCT.get().defaultBlockState()
                .getSoundType(player.level(), player.blockPosition(), player);
        // Vanilla's own footstep mix, with a little pitch scatter so a long crawl does not turn
        // into a metronome.
        float pitch = duct.getPitch() + (player.getRandom().nextFloat() - 0.5f) * 0.1f;
        DuctTravelSounds.playOwn(
                () -> player.playSound(duct.getStepSound(), duct.getVolume() * 0.15f, pitch));
    }

    private static boolean isDown(KeyMapping mapping) {
        return mapping != null && mapping.isDown();
    }

    /**
     * Ask for another vent and start fading immediately, so the screen responds on the same frame
     * as the key rather than a round trip later. A hop already under way swallows the request —
     * holding a direction therefore steps once per transition rather than queueing them up.
     */
    private static void send(int delta) {
        if (DuctTravelFade.isTransitioning()) {
            return;
        }
        DuctTravelFade.beginHop();
        // The world falls silent as the screen goes dark, and stays so until the far vent fades in.
        DuctTravelSounds.hush();

        // The vent being climbed into, heard here rather than in the world: by the time the server
        // moves anyone, the rider is already at the far end of the run and the sound at the vent
        // they left would be too distant to reach them. The server leaves them out of that one.
        LocalPlayer rider = Minecraft.getInstance().player;
        if (rider != null) {
            DuctTravelSounds.playOwn(() -> rider.playSound(DuctTravelEntity.CLANK_SOUND,
                    DuctTravelEntity.CLANK_VOLUME, DuctTravelEntity.CLANK_PITCH));
        }

        ZPSGamePackets.sendToServer(new DuctCycleC2SPacket(delta));
    }
}
