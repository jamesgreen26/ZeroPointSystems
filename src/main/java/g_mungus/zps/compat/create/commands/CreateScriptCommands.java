package g_mungus.zps.compat.create.commands;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.simibubi.create.Create;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.SidedFilteringBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import g_mungus.munguscript.language.builtin.BuiltInTypes;
import g_mungus.zps.ZPSMod;
import g_mungus.munguscript.language.node.ScriptNode;
import g_mungus.zps.commands.api.BlockApplicability;
import g_mungus.zps.commands.api.RegisterScriptCommandsEvent;
import g_mungus.zps.commands.api.ZPSNodes;
import g_mungus.zps.commands.api.ZPSScriptContext;
import g_mungus.zps.commands.api.ZPSScriptTypes;
import g_mungus.zps.mixin.create.ScrollValueBehaviourAccessor;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.server.command.EnumArgument;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class CreateScriptCommands {
    private record ScrollBehaviorKey(String execName, Class<?> enumClass) {}

    public static final Set<ResourceLocation> filter_allow_list = new HashSet<>(Set.of(
            Create.asResource("mechanical_saw"),
            Create.asResource("deployer"),
            Create.asResource("track_observer"),
            Create.asResource("stockpile_switch"),
            Create.asResource("item_hatch"),
            Create.asResource("brass_funnel"),
            Create.asResource("smart_fluid_pipe"),
            Create.asResource("smart_observer"),
            Create.asResource("smart_chute"),
            Create.asResource("mechanical_roller"),
            Create.asResource("basin")
    ));

    public static void registerScriptCommands(RegisterScriptCommandsEvent event) {
        var mappingsEvent = new OnRegisterCreateCompatExecutorMappingsEvent();
        NeoForge.EVENT_BUS.post(mappingsEvent);

        Multimap<ScrollBehaviorKey, ResourceLocation> enumGroups = HashMultimap.create();
        Multimap<String, ResourceLocation> intGroups = HashMultimap.create();
        Map<String, ScrollValueBehaviour> intSamples = new HashMap<>();
        Set<ResourceLocation> filterBlocks = new HashSet<>();

        for (var entry : BuiltInRegistries.BLOCK.entrySet()) {
            try {
                if (entry.getValue() instanceof EntityBlock entityBlock) {
                    BlockEntity blockEntity = entityBlock.newBlockEntity(new BlockPos(0, 0, 0), entry.getValue().defaultBlockState());

                    if (blockEntity instanceof SmartBlockEntity smartBlockEntity) {
                        for (var behavior : smartBlockEntity.getAllBehaviours()) {
                            if (behavior instanceof ScrollOptionBehaviour<?> scrollOptionBehaviour) {
                                Class<?> ec = scrollOptionBehaviour.get().getDeclaringClass();
                                String execName = executorName(mappingsEvent, entry.getKey().location(), scrollOptionBehaviour);
                                ScrollBehaviorKey key = new ScrollBehaviorKey(execName, ec);
                                enumGroups.put(key, entry.getKey().location());
                            } else if (behavior instanceof ScrollValueBehaviour scrollValueBehaviour) {
                                String execName = executorName(mappingsEvent, entry.getKey().location(), scrollValueBehaviour);
                                intGroups.put(execName, entry.getKey().location());
                                intSamples.putIfAbsent(execName, scrollValueBehaviour);
                            } else if (behavior instanceof FilteringBehaviour && !(behavior instanceof SidedFilteringBehaviour)) {
                                if(filter_allow_list.contains(entry.getKey().location())) {
                                    filterBlocks.add(entry.getKey().location());
                                }
                            }
                        }
                    }
                }
            } catch (Throwable t) {
                // Some block entities might not like being instantiated before the world is fully loaded, we skip them
            }
        }

        for (var key : enumGroups.keySet()) {
            event.register(getEnumExecutor(mappingsEvent, key.execName(), key.enumClass(), Set.copyOf(enumGroups.get(key))));
        }
        for (var execName : intGroups.keySet()) {
            event.register(getIntExecutor(mappingsEvent, execName, intSamples.get(execName), Set.copyOf(intGroups.get(execName))));
        }

        // A getter for each, named as its executor without "set_". Enums of different classes can
        // share a name, so their blocks are gathered by name; a name that is both a number and an
        // option somewhere keeps the number, since one getter cannot give both.
        Multimap<String, ResourceLocation> enumGetterBlocks = HashMultimap.create();
        enumGroups.forEach((key, block) -> enumGetterBlocks.put(key.execName(), block));
        for (var execName : intGroups.keySet()) {
            if (getterNameFree(event, execName)) {
                event.register(getIntGetter(mappingsEvent, execName, Set.copyOf(intGroups.get(execName))));
            }
        }
        for (var execName : enumGetterBlocks.keySet()) {
            if (intGroups.containsKey(execName)) {
                ZPSMod.LOGGER.warn("Create blocks have both a number and an option called {}; only the number gets a getter",
                        getterName(execName));
            } else if (getterNameFree(event, execName)) {
                event.register(getEnumGetter(mappingsEvent, execName, Set.copyOf(enumGetterBlocks.get(execName))));
            }
        }
        if (!filterBlocks.isEmpty()) {
            event.register(getFilterExecutor("set_filter", Set.copyOf(filterBlocks), event));
        }
        event.register(ZPSNodes.executor("set_display_text", BuiltInTypes.STRING,
                (text, context) -> SetDisplayTextCommand.setDisplayText(context.level(), context.pos(), text))
                .withApplicability(BlockApplicability.of(Create.asResource("display_link").toString())));
    }

    private static ScriptNode getIntExecutor(OnRegisterCreateCompatExecutorMappingsEvent mappings, String displayName,
                                             ScrollValueBehaviour scrollValueBehaviour, Set<ResourceLocation> associatedBlocks) {
        ScrollValueBehaviourAccessor accessor = (ScrollValueBehaviourAccessor) scrollValueBehaviour;
        return ZPSNodes.executor(displayName, BuiltInTypes.INT,
                IntegerArgumentType.integer(accessor.getMin(), accessor.getMax()),
                (in, context) -> {
                    ScrollValueBehaviour behaviour = scrollBehaviour(mappings, displayName, context, false);
                    if (behaviour == null) {
                        return 0;
                    }
                    behaviour.setValue(in);
                    return 1;
                }).withApplicability(BlockApplicability.ofBlocks(associatedBlocks));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ScriptNode getEnumExecutor(OnRegisterCreateCompatExecutorMappingsEvent mappings, String displayName,
                                              Class<?> enumClass, Set<ResourceLocation> associatedBlocks) {
        return ZPSNodes.executor(displayName, BuiltInTypes.INT,
                (ArgumentType<Enum>) (ArgumentType) EnumArgument.enumArgument((Class<Enum>) enumClass), Enum.class,
                (in, context) -> in.ordinal(),
                (in, context) -> {
                    ScrollValueBehaviour behaviour = scrollBehaviour(mappings, displayName, context, true);
                    if (!(behaviour instanceof ScrollOptionBehaviour<?> option) || !enumClass.isInstance(option.get())) {
                        return 0;
                    }
                    option.setValue(in);
                    return 1;
                }).withApplicability(BlockApplicability.ofBlocks(associatedBlocks));
    }

    /** What {@code set_<name>} sets, read as a number. Zero where there is none. */
    private static ScriptNode getIntGetter(OnRegisterCreateCompatExecutorMappingsEvent mappings, String execName,
                                           Set<ResourceLocation> associatedBlocks) {
        return ZPSNodes.getter(getterName(execName), BuiltInTypes.INT, context -> {
            ScrollValueBehaviour behaviour = scrollBehaviour(mappings, execName, context, false);
            return behaviour == null ? 0 : behaviour.getValue();
        }).withApplicability(BlockApplicability.ofBlocks(associatedBlocks));
    }

    /**
     * What {@code set_<name>} sets, read as the option's name, the word {@code set_<name>} takes.
     * Empty where there is none.
     */
    private static ScriptNode getEnumGetter(OnRegisterCreateCompatExecutorMappingsEvent mappings, String execName,
                                            Set<ResourceLocation> associatedBlocks) {
        return ZPSNodes.getter(getterName(execName), BuiltInTypes.STRING, context -> {
            ScrollValueBehaviour behaviour = scrollBehaviour(mappings, execName, context, true);
            return behaviour instanceof ScrollOptionBehaviour<?> option ? option.get().name() : "";
        }).withApplicability(BlockApplicability.ofBlocks(associatedBlocks));
    }

    /**
     * The executor a scroll behaviour on {@code block} is set with: its label in English, unless a
     * mapping renames it.
     */
    private static String executorName(OnRegisterCreateCompatExecutorMappingsEvent mappings, ResourceLocation block,
                                       ScrollValueBehaviour behaviour) {
        String defaultName = "set_" + EnglishLabels.of(behaviour.label).toLowerCase(Locale.ROOT).replace(" ", "_");
        return mappings.resolve(block, defaultName);
    }

    /** Labels come from any Create addon, so one may name a getter that already exists. */
    private static boolean getterNameFree(RegisterScriptCommandsEvent event, String execName) {
        if (event.hasGetter(getterName(execName))) {
            ZPSMod.LOGGER.warn("A Create setting would make a getter called {}, which already exists; it gets none",
                    getterName(execName));
            return false;
        }
        return true;
    }

    private static String getterName(String execName) {
        return execName.startsWith("set_") ? execName.substring("set_".length()) : execName;
    }

    /**
     * The scroll behaviour on the target that {@code execName} sets: an option or a number, as
     * {@code option} says. A block can have both kinds, and an option is a number too as far as
     * the classes go, so it is found by name rather than by class.
     */
    private static @Nullable ScrollValueBehaviour scrollBehaviour(OnRegisterCreateCompatExecutorMappingsEvent mappings,
                                                                  String execName, ZPSScriptContext context,
                                                                  boolean option) {
        if (!(context.level().getBlockEntity(context.pos()) instanceof SmartBlockEntity smartBlockEntity)) {
            return null;
        }
        ResourceLocation block = BuiltInRegistries.BLOCK.getKey(context.level().getBlockState(context.pos()).getBlock());
        for (var behaviour : smartBlockEntity.getAllBehaviours()) {
            if (behaviour instanceof ScrollValueBehaviour scroll
                    && (scroll instanceof ScrollOptionBehaviour<?>) == option
                    && execName.equals(executorName(mappings, block, scroll))) {
                return scroll;
            }
        }
        return null;
    }

    private static ScriptNode getFilterExecutor(String displayName, Set<ResourceLocation> associatedBlocks, RegisterScriptCommandsEvent event) {
        return ZPSNodes.executor(displayName, ZPSScriptTypes.ITEM,
                ItemArgument.item(event.buildContext()), ItemInput.class,
                (in, context) -> {
                    try {
                        return in.createItemStack(1, false);
                    } catch (CommandSyntaxException e) {
                        return ItemStack.EMPTY;
                    }
                },
                (in, context) -> {
                    BlockEntity blockEntity = context.level().getBlockEntity(context.pos());
                    if (blockEntity instanceof SmartBlockEntity smartBlockEntity) {
                        for (var behavior : smartBlockEntity.getAllBehaviours()) {
                            if (behavior instanceof FilteringBehaviour filteringBehaviour && !(behavior instanceof SidedFilteringBehaviour)) {
                                filteringBehaviour.setFilter(in);
                                return 1;
                            }
                        }
                    }
                    return 0;
                }).withApplicability(BlockApplicability.ofBlocks(associatedBlocks));
    }
}
