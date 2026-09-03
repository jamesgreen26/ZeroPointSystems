package g_mungus.zps;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.ForgeRegistries;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, ZPSMod.MOD_ID);
    public static final RegistryObject<SoundEvent> KEYSTROKE = registerSoundEvent("keystroke");
    public static final RegistryObject<SoundEvent> STATIC = registerSoundEvent("static");
    public static final RegistryObject<SoundEvent> ARM_MOVE = registerSoundEvent("arm_move");
    public static final RegistryObject<SoundEvent> IMPACT_THUNK = registerSoundEvent("impact_thunk");
    public static final RegistryObject<SoundEvent> IMPACT_ANVIL_BREAK = registerSoundEvent("impact_anvil_break");

    private static RegistryObject<SoundEvent> registerSoundEvent(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(ZPSMod.MOD_ID, name)));
    }
}
