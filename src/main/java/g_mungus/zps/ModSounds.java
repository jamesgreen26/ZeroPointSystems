package g_mungus.zps;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, ZPSMod.MOD_ID);
    public static DeferredHolder<SoundEvent, SoundEvent> KEYSTROKE = registerSoundEvent("keystroke");
    public static DeferredHolder<SoundEvent, SoundEvent> STATIC = registerSoundEvent("static");

    private static DeferredHolder<SoundEvent, SoundEvent> registerSoundEvent(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(ZPSMod.MOD_ID, name)));
    }
}
