package g_mungus.zps.commands.api_impl.arguments;

import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.ArgumentType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

@SuppressWarnings({"rawtypes", "unchecked"})
public class ValueOfOrLiteralArgumentTypeInfo implements ArgumentTypeInfo<ValueOfOrLiteralArgumentType<?>, ValueOfOrLiteralArgumentTypeInfo.Template> {
    public static final ValueOfOrLiteralArgumentTypeInfo INSTANCE = new ValueOfOrLiteralArgumentTypeInfo();

    private ValueOfOrLiteralArgumentTypeInfo() {
    }

    @Override
    public void serializeToNetwork(Template template, FriendlyByteBuf buffer) {
        buffer.writeResourceLocation(template.targetTypeKey);
        ArgumentTypeInfo wrappedInfo = template.wrappedTemplate.type();
        buffer.writeResourceLocation(BuiltInRegistries.COMMAND_ARGUMENT_TYPE.getKey(wrappedInfo));
        wrappedInfo.serializeToNetwork(template.wrappedTemplate, buffer);
    }

    @Override
    public Template deserializeFromNetwork(FriendlyByteBuf buffer) {
        ResourceLocation targetTypeKey = buffer.readResourceLocation();
        ResourceLocation wrappedTypeKey = buffer.readResourceLocation();
        ArgumentTypeInfo wrappedInfo = BuiltInRegistries.COMMAND_ARGUMENT_TYPE.get(wrappedTypeKey);
        if (wrappedInfo == null) {
            throw new IllegalStateException("Unknown wrapped argument type: " + wrappedTypeKey);
        }
        return new Template(targetTypeKey, wrappedInfo.deserializeFromNetwork(buffer));
    }

    @Override
    public void serializeToJson(Template template, JsonObject json) {
        json.addProperty("target_type", template.targetTypeKey.toString());
        ArgumentTypeInfo wrappedInfo = template.wrappedTemplate.type();
        json.addProperty("wrapped_type", BuiltInRegistries.COMMAND_ARGUMENT_TYPE.getKey(wrappedInfo).toString());
        JsonObject wrappedJson = new JsonObject();
        wrappedInfo.serializeToJson(template.wrappedTemplate, wrappedJson);
        json.add("wrapped", wrappedJson);
    }

    @Override
    public Template unpack(ValueOfOrLiteralArgumentType<?> argument) {
        return new Template(argument.targetTypeKey(), ArgumentTypeInfos.unpack(argument.wrappedType()));
    }

    public static final class Template implements ArgumentTypeInfo.Template<ValueOfOrLiteralArgumentType<?>> {
        private final ResourceLocation targetTypeKey;
        private final ArgumentTypeInfo.Template<?> wrappedTemplate;

        public Template(ResourceLocation targetTypeKey, ArgumentTypeInfo.Template<?> wrappedTemplate) {
            this.targetTypeKey = targetTypeKey;
            this.wrappedTemplate = wrappedTemplate;
        }

        @Override
        public ValueOfOrLiteralArgumentType<?> instantiate(CommandBuildContext context) {
            return ValueOfOrLiteralArgumentType.of((ArgumentType<?>) wrappedTemplate.instantiate(context), targetTypeKey);
        }

        @Override
        public ArgumentTypeInfo<ValueOfOrLiteralArgumentType<?>, ?> type() {
            return INSTANCE;
        }
    }
}
