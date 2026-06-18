package g_mungus.zps.item;

import g_mungus.zps.ZPSMod;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemAttributeModifiers;

public class RoboticArmSegmentItem extends Item {
    public RoboticArmSegmentItem(Properties properties) {
        super(properties.attributes(createAttributes()));
    }

    private static ItemAttributeModifiers createAttributes() {
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(ZPSMod.resource("robotic_arm_segment_attack_damage"), 4.0D, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED,
                        new AttributeModifier(ZPSMod.resource("robotic_arm_segment_attack_speed"), -2.8D, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_KNOCKBACK,
                        new AttributeModifier(ZPSMod.resource("robotic_arm_segment_attack_knockback"), 2.0D, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .build();
    }
}
