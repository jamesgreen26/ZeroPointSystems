package g_mungus.zps.mixin;

import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import g_mungus.zps.commands.api_impl.ZPSCommands;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(CommandSuggestions.class)
public class CommandSuggestionsMixin {

    @Final
    @Shadow
    private List<FormattedCharSequence> commandUsage;

    @Inject(method = "sortSuggestions", at = @At("RETURN"), cancellable = true)
    private void filterHiddenSuggestions(Suggestions suggestions, CallbackInfoReturnable<List<Suggestion>> cir) {
        List<Suggestion> original = cir.getReturnValue();

        original.removeIf(suggestion ->
                suggestion.getText().startsWith(ZPSCommands.Paths.INTERNAL)
        );

        cir.setReturnValue(original);
    }
}
