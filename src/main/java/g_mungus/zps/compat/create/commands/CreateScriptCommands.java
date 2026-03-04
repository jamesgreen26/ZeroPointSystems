package g_mungus.zps.compat.create.commands;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import g_mungus.zps.ZPSMod;
import g_mungus.zps.commands.api.RegisterScriptCommandsEvent;
import g_mungus.zps.commands.api.ScriptContext;
import g_mungus.zps.commands.api.ScriptExecutor;
import g_mungus.zps.mixin.create.ScrollValueBehaviourAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.command.EnumArgument;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

public class CreateScriptCommands {
    private record ScrollBehaviorKey(String execName, Class<?> enumClass) {}

    public static void registerScriptCommands(RegisterScriptCommandsEvent event) {
        var mappingsEvent = new OnRegisterCreateCompatExecutorMappingsEvent();
        MinecraftForge.EVENT_BUS.post(mappingsEvent);

        Multimap<ScrollBehaviorKey, ResourceLocation> enumGroups = HashMultimap.create();
        Map<ScrollBehaviorKey, ScrollOptionBehaviour<?>> enumSamples = new HashMap<>();
        Multimap<String, ResourceLocation> intGroups = HashMultimap.create();
        Map<String, ScrollValueBehaviour> intSamples = new HashMap<>();

        for (var entry : ForgeRegistries.BLOCKS.getEntries()) {
            try {
                if (entry.getValue() instanceof EntityBlock entityBlock) {
                    BlockEntity blockEntity = entityBlock.newBlockEntity(new BlockPos(0, 0, 0), entry.getValue().defaultBlockState());

                    if (blockEntity instanceof SmartBlockEntity smartBlockEntity) {
                        for (var behavior : smartBlockEntity.getAllBehaviours()) {
                            if (behavior instanceof ScrollOptionBehaviour<?> scrollOptionBehaviour) {
                                Class<?> ec = scrollOptionBehaviour.get().getDeclaringClass();
                                String defaultName = "set_" + scrollOptionBehaviour.label.getString().toLowerCase().replace(" ", "_");
                                String execName = mappingsEvent.resolve(entry.getKey().location(), defaultName);
                                ScrollBehaviorKey key = new ScrollBehaviorKey(execName, ec);
                                enumGroups.put(key, entry.getKey().location());
                                enumSamples.putIfAbsent(key, scrollOptionBehaviour);
                            } else if (behavior instanceof ScrollValueBehaviour scrollValueBehaviour) {
                                String defaultName = "set_" + scrollValueBehaviour.label.getString().toLowerCase().replace(" ", "_");
                                String execName = mappingsEvent.resolve(entry.getKey().location(), defaultName);
                                intGroups.put(execName, entry.getKey().location());
                                intSamples.putIfAbsent(execName, scrollValueBehaviour);
                            }
                        }
                    }
                }
            } catch (Throwable t) {
                // Some block entities might not like being instantiated before the world is fully loaded, we skip them
            }
        }

        for (var key : enumGroups.keySet()) {
            event.register(getEnumExecutor(key.execName(), enumSamples.get(key), key.enumClass(), Set.copyOf(enumGroups.get(key))));
        }
        for (var execName : intGroups.keySet()) {
            event.register(getIntExecutor(execName, intSamples.get(execName), Set.copyOf(intGroups.get(execName))));
        }
    }

    private static @NotNull ScriptExecutor<Integer, Integer> getIntExecutor(String displayName, ScrollValueBehaviour scrollValueBehaviour, Set<ResourceLocation> associatedBlocks) {
        ScrollValueBehaviourAccessor accessor = (ScrollValueBehaviourAccessor) scrollValueBehaviour;
        return ScriptExecutor.simpleWithBlocks(
                displayName,
                Integer.class,
                ZPSMod.resource("int"),
                IntegerArgumentType.integer(accessor.getMin(), accessor.getMax()),
                (in, context) -> {
                    BlockEntity blockEntity = context.level().getBlockEntity(context.pos());
                    if (blockEntity instanceof SmartBlockEntity smartBlockEntity) {
                        for (var behavior : smartBlockEntity.getAllBehaviours()) {
                            if (behavior instanceof ScrollValueBehaviour b) {
                                b.setValue(in);
                                return 1;
                            }
                        }
                    }
                    return 0;
                },
                associatedBlocks
        );
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static @NotNull <T> ScriptExecutor<Integer, T> getEnumExecutor(String displayName, ScrollOptionBehaviour<?> scrollOptionBehaviour, Class<T> enumClass, Set<ResourceLocation> associatedBlocks) {
        return new ScriptExecutor<>(
                displayName,
                Integer.class,
                ZPSMod.resource("int"),
                (ArgumentType<T>) EnumArgument.enumArgument((Class<Enum>) enumClass),
                enumClass,
                (BiFunction<T, ScriptContext, Integer>) (BiFunction<Enum<?>, ScriptContext, Integer>) (in, context) -> in.ordinal(),
                (BiFunction<Integer, ScriptContext, Integer>) (in, context) -> {
                    BlockEntity blockEntity = context.level().getBlockEntity(context.pos());
                    if (blockEntity instanceof SmartBlockEntity smartBlockEntity) {
                        for (var behavior : smartBlockEntity.getAllBehaviours()) {
                            if (behavior instanceof ScrollOptionBehaviour<?> b && enumClass.isInstance(b.get())) {
                                b.setValue(in);
                                return 1;
                            }
                        }
                    }
                    return 0;
                },
                associatedBlocks
        );
    }
}
