package g_mungus.zps.item;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.model.HumanoidModel.ArmPose;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/// Credit: Create
public interface CustomArmPoseItem {
	@Nullable
	ArmPose getArmPose(ItemStack stack, AbstractClientPlayer player, InteractionHand hand);

	default boolean shouldSwingArm() {
		return true;
	}
}