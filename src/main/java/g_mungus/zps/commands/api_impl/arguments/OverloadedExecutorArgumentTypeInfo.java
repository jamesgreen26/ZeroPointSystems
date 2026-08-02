package g_mungus.zps.commands.api_impl.arguments;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.ArgumentType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@SuppressWarnings({"rawtypes", "unchecked"})
public class OverloadedExecutorArgumentTypeInfo implements ArgumentTypeInfo<OverloadedExecutorArgumentType, OverloadedExecutorArgumentTypeInfo.Template> {
    public static final OverloadedExecutorArgumentTypeInfo INSTANCE = new OverloadedExecutorArgumentTypeInfo();

    private OverloadedExecutorArgumentTypeInfo() {
    }

    @Override
    public void serializeToNetwork(Template template, FriendlyByteBuf buffer) {
        buffer.writeVarInt(template.variants.size());
        for (VariantTemplate variant : template.variants) {
            buffer.writeResourceLocation(variant.inputKey);
            writeAssociatedBlocks(buffer, variant.associatedBlocks);
            ArgumentTypeInfo wrappedInfo = variant.wrappedTemplate.type();
            buffer.writeResourceLocation(BuiltInRegistries.COMMAND_ARGUMENT_TYPE.getKey(wrappedInfo));
            wrappedInfo.serializeToNetwork(variant.wrappedTemplate, buffer);
        }
    }

    @Override
    public Template deserializeFromNetwork(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        List<VariantTemplate> variants = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            ResourceLocation inputKey = buffer.readResourceLocation();
            Set<ResourceLocation> associatedBlocks = readAssociatedBlocks(buffer);
            ResourceLocation wrappedTypeKey = buffer.readResourceLocation();
            ArgumentTypeInfo wrappedInfo = BuiltInRegistries.COMMAND_ARGUMENT_TYPE.get(wrappedTypeKey);
            if (wrappedInfo == null) {
                throw new IllegalStateException("Unknown wrapped argument type: " + wrappedTypeKey);
            }
            variants.add(new VariantTemplate(inputKey, associatedBlocks, wrappedInfo.deserializeFromNetwork(buffer)));
        }
        return new Template(variants);
    }

    @Override
    public void serializeToJson(Template template, JsonObject json) {
        JsonArray variantsJson = new JsonArray();
        for (VariantTemplate variant : template.variants) {
            JsonObject variantJson = new JsonObject();
            variantJson.addProperty("input_key", variant.inputKey.toString());
            if (variant.associatedBlocks != null) {
                JsonArray blocksJson = new JsonArray();
                variant.associatedBlocks.stream()
                        .map(ResourceLocation::toString)
                        .sorted()
                        .forEach(blocksJson::add);
                variantJson.add("associated_blocks", blocksJson);
            }
            ArgumentTypeInfo wrappedInfo = variant.wrappedTemplate.type();
            variantJson.addProperty("wrapped_type", BuiltInRegistries.COMMAND_ARGUMENT_TYPE.getKey(wrappedInfo).toString());
            JsonObject wrappedJson = new JsonObject();
            wrappedInfo.serializeToJson(variant.wrappedTemplate, wrappedJson);
            variantJson.add("wrapped", wrappedJson);
            variantsJson.add(variantJson);
        }
        json.add("variants", variantsJson);
    }

    @Override
    public Template unpack(OverloadedExecutorArgumentType argument) {
        return new Template(argument.variants().stream()
                .map(variant -> new VariantTemplate(variant.inputKey(), variant.associatedBlocks(), ArgumentTypeInfos.unpack(variant.argumentType())))
                .toList());
    }

    public static final class Template implements ArgumentTypeInfo.Template<OverloadedExecutorArgumentType> {
        private final List<VariantTemplate> variants;

        public Template(List<VariantTemplate> variants) {
            this.variants = List.copyOf(variants);
        }

        @Override
        public OverloadedExecutorArgumentType instantiate(CommandBuildContext context) {
            return new OverloadedExecutorArgumentType(variants.stream()
                    .map(variant -> new OverloadedExecutorArgumentType.Variant(
                            null,
                            variant.inputKey,
                            (ArgumentType<?>) variant.wrappedTemplate.instantiate(context),
                            variant.associatedBlocks
                    ))
                    .toList());
        }

        @Override
        public ArgumentTypeInfo<OverloadedExecutorArgumentType, ?> type() {
            return INSTANCE;
        }
    }

    private static void writeAssociatedBlocks(FriendlyByteBuf buffer, Set<ResourceLocation> associatedBlocks) {
        buffer.writeBoolean(associatedBlocks != null);
        if (associatedBlocks == null) {
            return;
        }
        buffer.writeVarInt(associatedBlocks.size());
        associatedBlocks.forEach(buffer::writeResourceLocation);
    }

    private static Set<ResourceLocation> readAssociatedBlocks(FriendlyByteBuf buffer) {
        if (!buffer.readBoolean()) {
            return null;
        }
        int size = buffer.readVarInt();
        Set<ResourceLocation> associatedBlocks = new java.util.LinkedHashSet<>();
        for (int i = 0; i < size; i++) {
            associatedBlocks.add(buffer.readResourceLocation());
        }
        return Set.copyOf(associatedBlocks);
    }

    public record VariantTemplate(ResourceLocation inputKey, Set<ResourceLocation> associatedBlocks, ArgumentTypeInfo.Template<?> wrappedTemplate) {
    }
}
