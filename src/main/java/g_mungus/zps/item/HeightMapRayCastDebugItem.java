package g_mungus.zps.item;

import g_mungus.zps.lidar.HeightMapRaycast;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public class HeightMapRayCastDebugItem extends Item {
    private static final double MAX_CAST_DISTANCE = 512.0;

    public HeightMapRayCastDebugItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }

        Vec3 start = player.getEyePosition();
        Vec3 direction = player.getLookAngle();
        double distance = HeightMapRaycast.INSTANCE.invoke(serverLevel, start, direction, MAX_CAST_DISTANCE);

        if (distance < 0.0) {
            player.sendSystemMessage(Component.literal(
                    String.format(Locale.ROOT, "Height-map raycast: no intersection in %.1f blocks", MAX_CAST_DISTANCE)
            ));
            return InteractionResultHolder.success(stack);
        }

        Vec3 hitPoint = start.add(direction.normalize().scale(distance));
        player.sendSystemMessage(Component.literal(
                String.format(Locale.ROOT, "Height-map raycast distance: %.3f at (%.2f, %.2f, %.2f)",
                        distance, hitPoint.x, hitPoint.y, hitPoint.z)
        ));
        return InteractionResultHolder.success(stack);
    }
}
