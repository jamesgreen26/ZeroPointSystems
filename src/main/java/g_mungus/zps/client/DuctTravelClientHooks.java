package g_mungus.zps.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.entity.DuctTravelEntity;
import g_mungus.zps.networking.DuctCycleC2SPacket;
import g_mungus.zps.networking.ZPSGamePackets;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.armortrim.ArmorTrim;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

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
    private static HumanoidModel<AbstractClientPlayer> helmetModel;

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
     * hand.
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

        renderHelmet(rider, model, poseStack, event.getMultiBufferSource(), event.getPackedLight(),
                partialTick, (float) rotation.x);

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
     * Whatever is on the rider's head, drawn over it.
     *
     * <p>Cancelling vanilla's pass took the armour layer with it, so the head slot is drawn here
     * the way {@code HumanoidArmorLayer} would: every layer the material declares, its dye tint,
     * a trim if there is one, and the enchantment glint. The other three slots stay gone — there
     * is no body for them to sit on.
     *
     * <p>The armour model is posed by copying the head model, which has already been turned into
     * the vent's frame, so a helmet follows the head however it is lying.
     */
    private static void renderHelmet(AbstractClientPlayer rider, PlayerModel<AbstractClientPlayer> head,
                                     PoseStack poseStack, MultiBufferSource buffers, int packedLight,
                                     float partialTick, float framePitch) {
        ItemStack helmet = rider.getItemBySlot(EquipmentSlot.HEAD);
        if (!(helmet.getItem() instanceof ArmorItem armor)
                || armor.getEquipmentSlot() != EquipmentSlot.HEAD) {
            return;
        }

        HumanoidModel<AbstractClientPlayer> armorModel = helmetModel();
        head.copyPropertiesTo(armorModel);

        // Vanilla sets these on the player model inside the render we cancelled, so the model is
        // left holding whatever the last player actually drawn put there — and before any player
        // has been drawn at all, EntityModel's own defaults, where young is true. Unlike the head,
        // which is drawn part by part, armour goes through renderToBuffer, and its baby branch
        // drops the head a block and shrinks it: a helmet somewhere around the waist.
        armorModel.young = rider.isBaby();
        armorModel.riding = false;
        armorModel.attackTime = 0.0f;

        armorModel.setAllVisible(false);
        armorModel.head.visible = true;
        armorModel.hat.visible = true;

        // Lets another mod swap in its own model for its own helmet.
        Model posed = ClientHooks.getArmorModel(rider, helmet, EquipmentSlot.HEAD, armorModel);
        IClientItemExtensions extensions = IClientItemExtensions.of(helmet);
        extensions.setupModelAnimations(rider, helmet, EquipmentSlot.HEAD, posed,
                0.0f, 0.0f, partialTick, 0.0f, 0.0f, framePitch);

        int fallbackColor = extensions.getDefaultDyeColor(helmet);
        List<ArmorMaterial.Layer> layers = armor.getMaterial().value().layers();
        for (int i = 0; i < layers.size(); i++) {
            ArmorMaterial.Layer layer = layers.get(i);
            int tint = extensions.getArmorLayerTintColor(helmet, rider, layer, i, fallbackColor);
            if (tint != 0) {
                ResourceLocation texture =
                        ClientHooks.getArmorTexture(rider, helmet, layer, false, EquipmentSlot.HEAD);
                posed.renderToBuffer(poseStack, buffers.getBuffer(RenderType.armorCutoutNoCull(texture)),
                        packedLight, OverlayTexture.NO_OVERLAY, tint);
            }
        }

        ArmorTrim trim = helmet.get(DataComponents.TRIM);
        if (trim != null) {
            TextureAtlasSprite sprite = Minecraft.getInstance().getModelManager()
                    .getAtlas(Sheets.ARMOR_TRIMS_SHEET)
                    .getSprite(trim.outerTexture(armor.getMaterial()));
            posed.renderToBuffer(poseStack,
                    sprite.wrap(buffers.getBuffer(Sheets.armorTrimsSheet(trim.pattern().value().decal()))),
                    packedLight, OverlayTexture.NO_OVERLAY);
        }

        if (helmet.hasFoil()) {
            posed.renderToBuffer(poseStack, buffers.getBuffer(RenderType.armorEntityGlint()),
                    packedLight, OverlayTexture.NO_OVERLAY);
        }
    }

    /**
     * The armour model a helmet is drawn on, baked once and kept.
     *
     * <p>The wide layer serves both skin types: slim and wide armour differ only at the arms, and
     * no arms are drawn here.
     */
    private static HumanoidModel<AbstractClientPlayer> helmetModel() {
        if (helmetModel == null) {
            helmetModel = new HumanoidArmorModel<>(Minecraft.getInstance().getEntityModels()
                    .bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR));
        }
        return helmetModel;
    }

    /** Dropped on a resource reload, when the geometry behind it is rebuilt. */
    public static void clearBakedModels() {
        helmetModel = null;
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
