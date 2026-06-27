package g_mungus.zps.client;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator;
import com.mojang.blaze3d.vertex.VertexConsumer;

import g_mungus.zps.blockentity.ServoMotorBlockEntity;
import g_mungus.zps.client.renderer.contraption.ContraptionRenderWorld;
import g_mungus.zps.contraption.Contraption;
import g_mungus.zps.contraption.ContraptionBlockGetter;
import g_mungus.zps.contraption.ContraptionPlaceContext;
import g_mungus.zps.contraption.ContraptionSimLevel;
import g_mungus.zps.contraption.ContraptionTransform;
import g_mungus.zps.networking.ContraptionBreakC2SPacket;
import g_mungus.zps.networking.ContraptionBreakProgressC2SPacket;
import g_mungus.zps.networking.ContraptionPlaceC2SPacket;
import g_mungus.zps.networking.ZPSGamePackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Client-side targeting, input handling and rendering for breaking and placing
 * blocks on an assembled contraption. The player's look ray is transformed into
 * contraption-local space and clipped against the captured blocks; if a hit is
 * nearer than the world target, vanilla interaction is suppressed (via
 * {@link g_mungus.zps.mixin.MinecraftMixin}) and routed here instead, while the
 * selection box and breaking crack are drawn under the contraption's pose.
 */
public final class ContraptionInteractionClient {

	private ContraptionInteractionClient() {}

	/** Cooldown ticks after a break/place, mirroring vanilla's destroy/use delays. */
	private static final int ACTION_DELAY = 5;
	private static final int OUTLINE_ARGB_ALPHA = 102; // ~0.4

	private record Hit(ServoMotorBlockEntity motor, BlockPos localPos, Direction localFace, Vec3 localHit,
		Vec3 worldHit, ContraptionTransform transform) {}

	private record RemoteBreak(BlockPos motorPos, BlockPos localPos, int stage) {}

	private static Hit currentHit;

	private static ServoMotorBlockEntity breakMotor;
	private static BlockPos breakLocal;
	private static float destroyProgress;
	private static int lastSentStage = -1;
	private static int destroyDelay;
	private static int useDelay;

	private static final Map<Integer, RemoteBreak> remoteBreaks = new HashMap<>();

	// region input (called from MinecraftMixin)

	public static boolean hasTarget() {
		return currentHit != null;
	}

	public static boolean handleStartAttack() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return false;
		pick(partialTick());
		if (currentHit == null) return false;
		progressMining();
		mc.player.swing(InteractionHand.MAIN_HAND);
		return true;
	}

	public static boolean handleContinueAttack(boolean leftDown) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return false;
		pick(partialTick());
		if (!leftDown || currentHit == null) {
			resetBreak();
			return false;
		}
		progressMining();
		mc.player.swing(InteractionHand.MAIN_HAND);
		return true;
	}

	public static boolean handleStartUseItem() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null) return false;
		pick(partialTick());
		if (currentHit == null) return false;
		if (useDelay > 0) return true;

		InteractionHand hand = null;
		if (player.getMainHandItem().getItem() instanceof BlockItem) hand = InteractionHand.MAIN_HAND;
		else if (player.getOffhandItem().getItem() instanceof BlockItem) hand = InteractionHand.OFF_HAND;
		// Not holding a placeable block: let vanilla handle item use (eating, bows, etc.).
		if (hand == null) return false;

		ZPSGamePackets.sendToServer(new ContraptionPlaceC2SPacket(currentHit.motor().getBlockPos(),
			currentHit.localPos(), currentHit.localFace(), currentHit.localHit(), hand));
		// Predict the placement locally so the block appears immediately; the authoritative
		// server sync replaces it a round-trip later (and corrects it if the server disagrees).
		predictPlacement(player, currentHit, hand);
		player.swing(hand);
		useDelay = ACTION_DELAY;
		return true;
	}

	private static void predictPlacement(LocalPlayer player, Hit hit, InteractionHand hand) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return;
		ServoMotorBlockEntity motor = hit.motor();
		Contraption contraption = motor.getContraption();
		if (contraption == null) return;

		ItemStack stack = player.getItemInHand(hand);
		ContraptionTransform transform = ContraptionTransform.ofCurrent(motor);
		ContraptionSimLevel sim = new ContraptionSimLevel(mc.level, contraption);
		ContraptionPlaceContext.Placed placed = ContraptionPlaceContext.resolve(sim, player, hand, stack,
			hit.localPos(), hit.localFace(), hit.localHit(), transform);
		if (placed == null) return;

		CompoundTag beNbt = null;
		CompoundTag updateTag = null;
		if (placed.state().getBlock() instanceof EntityBlock entityBlock) {
			BlockEntity be = entityBlock.newBlockEntity(placed.pos(), placed.state());
			if (be != null) {
				be.setLevel(mc.level);
				beNbt = be.saveWithFullMetadata(mc.level.registryAccess());
				updateTag = be.getUpdateTag(mc.level.registryAccess());
			}
		}
		Contraption predicted = contraption.copy();
		predicted.putBlock(placed.pos(), placed.state(), beNbt, updateTag);
		motor.setContraptionClient(predicted);
	}

	private static void predictBreak(ServoMotorBlockEntity motor, BlockPos local) {
		Contraption contraption = motor.getContraption();
		if (contraption == null) return;
		Contraption predicted = contraption.copy();
		predicted.removeBlock(local);
		motor.setContraptionClient(predicted);
	}

	private static void progressMining() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || currentHit == null) {
			resetBreak();
			return;
		}
		if (destroyDelay > 0) return;

		ServoMotorBlockEntity motor = currentHit.motor();
		BlockPos local = currentHit.localPos();
		Contraption contraption = motor.getContraption();
		if (contraption == null) {
			resetBreak();
			return;
		}
		StructureBlockInfo info = contraption.getBlocks().get(local);
		if (info == null) {
			resetBreak();
			return;
		}

		if (motor != breakMotor || !local.equals(breakLocal)) {
			resetBreak();
			breakMotor = motor;
			breakLocal = local;
		}

		if (player.getAbilities().instabuild) {
			sendBreak(motor, local);
			predictBreak(motor, local);
			destroyDelay = ACTION_DELAY;
			resetBreak();
			return;
		}

		destroyProgress += info.state().getDestroyProgress(player, new ContraptionBlockGetter(contraption), local);
		int stage = Math.min(9, (int) (destroyProgress * 10.0f));
		if (stage != lastSentStage) {
			lastSentStage = stage;
			ZPSGamePackets.sendToServer(new ContraptionBreakProgressC2SPacket(motor.getBlockPos(), local, stage));
		}
		if (destroyProgress >= 1.0f) {
			sendBreak(motor, local);
			predictBreak(motor, local);
			destroyDelay = ACTION_DELAY;
			resetBreak();
		}
	}

	private static void sendBreak(ServoMotorBlockEntity motor, BlockPos local) {
		ZPSGamePackets.sendToServer(new ContraptionBreakC2SPacket(motor.getBlockPos(), local));
	}

	private static void resetBreak() {
		if (breakMotor != null && breakLocal != null && lastSentStage >= 0)
			ZPSGamePackets.sendToServer(new ContraptionBreakProgressC2SPacket(breakMotor.getBlockPos(), breakLocal, -1));
		breakMotor = null;
		breakLocal = null;
		destroyProgress = 0;
		lastSentStage = -1;
	}

	// endregion

	// region events

	public static void onClientTick(ClientTickEvent.Post event) {
		if (destroyDelay > 0) destroyDelay--;
		if (useDelay > 0) useDelay--;

		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			currentHit = null;
			remoteBreaks.clear();
			breakMotor = null;
			breakLocal = null;
			destroyProgress = 0;
			lastSentStage = -1;
			return;
		}
		// Resolve the local player against every assembled contraption (client drives
		// this because player movement is client-authoritative).
		if (mc.player != null) {
			for (ServoMotorBlockEntity be : ServoMotorBlockEntity.ACTIVE_CLIENT) {
				if (be.getLevel() == mc.level)
					be.collideWithPlayer(mc.player);
			}
		}
	}

	public static void onRenderHighlight(RenderHighlightEvent.Block event) {
		// Our contraption block is the nearer target: suppress the wrong world outline.
		if (currentHit != null)
			event.setCanceled(true);
	}

	public static void onRemoteDestroyStage(BlockPos motorPos, BlockPos localPos, int breakerId, int stage) {
		if (stage < 0) remoteBreaks.remove(breakerId);
		else remoteBreaks.put(breakerId, new RemoteBreak(motorPos, localPos, stage));
	}

	public static void onRenderLevelStage(RenderLevelStageEvent event) {
		RenderLevelStageEvent.Stage stage = event.getStage();
		if (stage == RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS) {
			pick(event.getPartialTick().getGameTimeDeltaPartialTick(false));
			return;
		}
		if (stage != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return;
		Vec3 cam = event.getCamera().getPosition();
		PoseStack pose = event.getPoseStack();

		if (currentHit != null)
			renderOutline(pose, mc.renderBuffers().bufferSource(), cam, currentHit);

		boolean anyCrack = (breakMotor != null && breakLocal != null && destroyProgress > 0) || !remoteBreaks.isEmpty();
		if (!anyCrack) return;

		MultiBufferSource.BufferSource crumbling = mc.renderBuffers().crumblingBufferSource();
		if (breakMotor != null && breakLocal != null && destroyProgress > 0)
			renderCrack(pose, cam, breakMotor, breakLocal, Math.min(9, (int) (destroyProgress * 10f)), crumbling);
		for (RemoteBreak rb : remoteBreaks.values()) {
			if (rb.stage() < 0) continue;
			if (mc.level.getBlockEntity(rb.motorPos()) instanceof ServoMotorBlockEntity m && m.getContraption() != null)
				renderCrack(pose, cam, m, rb.localPos(), Math.min(9, rb.stage()), crumbling);
		}
		crumbling.endBatch();
	}

	// endregion

	// region raytrace + rendering

	private static float partialTick() {
		return Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
	}

	private static void pick(float partialTick) {
		currentHit = null;
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null || ServoMotorBlockEntity.ACTIVE_CLIENT.isEmpty()) return;

		double reach = player.blockInteractionRange();
		Vec3 eye = player.getEyePosition(partialTick);
		Vec3 look = player.getViewVector(partialTick);
		Vec3 end = eye.add(look.x * reach, look.y * reach, look.z * reach);

		double bestDist = reach;
		if (mc.hitResult != null && mc.hitResult.getType() != HitResult.Type.MISS)
			bestDist = mc.hitResult.getLocation().distanceTo(eye);

		Hit best = null;
		for (ServoMotorBlockEntity be : ServoMotorBlockEntity.ACTIVE_CLIENT) {
			if (be.getLevel() != mc.level) continue;
			Contraption contraption = be.getContraption();
			if (contraption == null || contraption.isEmpty()) continue;

			ContraptionTransform transform = ContraptionTransform.ofInterpolated(be, partialTick);
			Vec3 localStart = transform.worldToLocal(eye);
			Vec3 localEnd = transform.worldToLocal(end);
			BlockHitResult hit = new ContraptionBlockGetter(contraption).clip(
				new ClipContext(localStart, localEnd, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
			if (hit.getType() == HitResult.Type.MISS) continue;

			Vec3 worldHit = transform.localToWorld(hit.getLocation());
			double dist = worldHit.distanceTo(eye);
			if (dist < bestDist) {
				bestDist = dist;
				best = new Hit(be, hit.getBlockPos(), hit.getDirection(), hit.getLocation(), worldHit, transform);
			}
		}
		currentHit = best;
	}

	private static void renderOutline(PoseStack pose, MultiBufferSource.BufferSource buffers, Vec3 cam, Hit hit) {
		Contraption contraption = hit.motor().getContraption();
		if (contraption == null) return;
		StructureBlockInfo info = contraption.getBlocks().get(hit.localPos());
		if (info == null) return;
		VoxelShape shape = info.state().getShape(new ContraptionBlockGetter(contraption), hit.localPos());
		if (shape.isEmpty()) return;

		pose.pushPose();
		pose.mulPose(hit.transform().renderPose(cam));
		pose.translate(hit.localPos().getX(), hit.localPos().getY(), hit.localPos().getZ());
		VertexConsumer vc = buffers.getBuffer(RenderType.lines());
		PoseStack.Pose last = pose.last();
		shape.forAllEdges((x1, y1, z1, x2, y2, z2) -> {
			float dx = (float) (x2 - x1);
			float dy = (float) (y2 - y1);
			float dz = (float) (z2 - z1);
			float len = Mth.sqrt(dx * dx + dy * dy + dz * dz);
			if (len < 1.0e-5f) return;
			dx /= len;
			dy /= len;
			dz /= len;
			vc.addVertex(last, (float) x1, (float) y1, (float) z1).setColor(0, 0, 0, OUTLINE_ARGB_ALPHA)
				.setNormal(last, dx, dy, dz);
			vc.addVertex(last, (float) x2, (float) y2, (float) z2).setColor(0, 0, 0, OUTLINE_ARGB_ALPHA)
				.setNormal(last, dx, dy, dz);
		});
		pose.popPose();
		buffers.endBatch(RenderType.lines());
	}

	private static void renderCrack(PoseStack pose, Vec3 cam, ServoMotorBlockEntity motor, BlockPos local, int stage,
		MultiBufferSource.BufferSource crumbling) {
		Contraption contraption = motor.getContraption();
		if (contraption == null) return;
		StructureBlockInfo info = contraption.getBlocks().get(local);
		if (info == null) return;

		ContraptionTransform transform = ContraptionTransform.ofInterpolated(motor, partialTick());
		pose.pushPose();
		pose.mulPose(transform.renderPose(cam));
		pose.translate(local.getX(), local.getY(), local.getZ());
		RenderType crackType = ModelBakery.DESTROY_TYPES.get(stage);
		VertexConsumer vc = new SheetedDecalTextureGenerator(crumbling.getBuffer(crackType), pose.last(), 1.0f);
		Minecraft.getInstance().getBlockRenderer().renderBreakingTexture(info.state(), local,
			new ContraptionRenderWorld(motor.getLevel(), contraption), pose, vc);
		pose.popPose();
	}

	// endregion
}
