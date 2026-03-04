package g_mungus.zps.compat.create;

import com.mojang.brigadier.arguments.ArgumentType;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.contraptions.IControlContraption;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.simibubi.create.infrastructure.ponder.AllCreatePonderTags;
import com.tterrag.registrate.util.entry.RegistryEntry;
import g_mungus.zps.ZPSMod;
import g_mungus.zps.blockentity.ModBlockEntities;
import g_mungus.zps.commands.api.RegisterScriptCommandsEvent;
import g_mungus.zps.commands.api.ScriptContext;
import g_mungus.zps.commands.api.ScriptExecutor;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.command.EnumArgument;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

@ApiStatus.Internal
public class CreateCompat {
    private record ScrollBehaviorKey(String label, Class<?> enumClass) {}
    private static final CreateRegistrate REG = CreateRegistrate.create(ZPSMod.MOD_ID);

    public static final RegistryEntry<SerialBusDisplayLinkSource> SERIAL_BUS_SOURCE = REG.displaySource("serial_bus", SerialBusDisplayLinkSource::new).register();

    public static void init(IEventBus modEventBus) {
        REG.registerEventListeners(modEventBus);
        modEventBus.addListener(CreateCompat::onCommonSetup);
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            DisplaySource.BY_BLOCK_ENTITY.add(ModBlockEntities.SERIAL_BUS.get(), SERIAL_BUS_SOURCE.get());
        });
    }

    public static void tickDisplayLinkSource(LevelAccessor level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof DisplayLinkBlockEntity displayLink) {
            displayLink.updateGatheredData();
        }
    }

    public static void registerPonderTagEntries(@NotNull PonderTagRegistrationHelper<ResourceLocation> helper) {
        helper.addToTag(AllCreatePonderTags.DISPLAY_SOURCES).add(ZPSMod.resource("serial_bus"));
    }

    public static void registerScriptCommands(RegisterScriptCommandsEvent event) {
        Map<ScrollBehaviorKey, Set<ResourceLocation>> groups = new HashMap<>();
        Map<ScrollBehaviorKey, ScrollOptionBehaviour<?>> samples = new HashMap<>();

        for (var entry : ForgeRegistries.BLOCKS.getEntries()) {
            try {
                if (entry.getValue() instanceof EntityBlock entityBlock) {
                    BlockEntity blockEntity = entityBlock.newBlockEntity(new BlockPos(0, 0, 0), entry.getValue().defaultBlockState());

                    if (blockEntity instanceof SmartBlockEntity smartBlockEntity) {
                        for (var behavior : smartBlockEntity.getAllBehaviours()) {
                            if (behavior instanceof ScrollOptionBehaviour<?> scrollOptionBehaviour) {
                                ScrollBehaviorKey key = new ScrollBehaviorKey(scrollOptionBehaviour.label.getString(), scrollOptionBehaviour.get().getDeclaringClass());
                                groups.computeIfAbsent(key, k -> new HashSet<>()).add(entry.getKey().location());
                                samples.putIfAbsent(key, scrollOptionBehaviour);
                            }
                        }
                    }
                }
            } catch (Throwable t) {
                // Some block entities might not like being instantiated before the world is fully loaded, we skip them
            }
        }

        for (var groupEntry : groups.entrySet()) {
            ScrollBehaviorKey key = groupEntry.getKey();
            event.register(getExecutor(samples.get(key), key.enumClass(), groupEntry.getValue()));
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static @NotNull <T> ScriptExecutor<Integer, T> getExecutor(ScrollOptionBehaviour<?> scrollOptionBehaviour, Class<T> enumClass, Set<ResourceLocation> associatedBlocks) {
        String optionLabel = scrollOptionBehaviour.label.getString();
        return new ScriptExecutor<>(
                enumClass.equals(IControlContraption.RotationMode.class) ? "set_rotation_mode" : "set_" + optionLabel.toLowerCase().replace(" ", "_"),
                Integer.class,
                ZPSMod.resource("int"),
                (ArgumentType<T>) EnumArgument.enumArgument((Class<Enum>) enumClass),
                enumClass,
                (BiFunction<T, ScriptContext, Integer>) (BiFunction<Enum<?>, ScriptContext, Integer>) (in, context) -> in.ordinal(),
                (BiFunction<Integer, ScriptContext, Integer>) (in, context) -> {
                    BlockEntity blockEntity = context.level().getBlockEntity(context.pos());
                    if (blockEntity instanceof SmartBlockEntity smartBlockEntity) {
                        for (var behavior : smartBlockEntity.getAllBehaviours()) {
                            if (behavior instanceof ScrollOptionBehaviour<?> b && optionLabel.equals(b.label.getString()) && enumClass.isInstance(b.get())) {
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