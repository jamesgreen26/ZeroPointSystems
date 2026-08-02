package g_mungus.zps.compat.create;

import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.api.behaviour.display.DisplayTarget;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlockEntity;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.simibubi.create.infrastructure.ponder.AllCreatePonderTags;
import com.tterrag.registrate.util.entry.RegistryEntry;
import g_mungus.zps.ZPSMod;
import g_mungus.zps.blockentity.ModBlockEntities;
import g_mungus.zps.commands.api.RegisterScriptCommandsEvent;
import g_mungus.zps.compat.create.commands.CreateScriptCommands;
import g_mungus.zps.compat.create.commands.CreateScriptExecutorMappers;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class CreateCompat {
    private static final CreateRegistrate REG = CreateRegistrate.create(ZPSMod.MOD_ID);

    public static final RegistryEntry<SerialBusDisplayLinkSource> SERIAL_BUS_SOURCE = REG.displaySource("serial_bus", SerialBusDisplayLinkSource::new).register();
    public static final RegistryEntry<SerialBusManualDisplayLinkSource> SERIAL_BUS_MANUAL_SOURCE = REG.displaySource("serial_bus_manual", SerialBusManualDisplayLinkSource::new).register();
    public static final RegistryEntry<DataLecternDisplayTarget> DATA_LECTERN_TARGET = REG.displayTarget("data_lectern", DataLecternDisplayTarget::new).register();

    public static void init(IEventBus modEventBus) {
        REG.registerEventListeners(modEventBus);
        modEventBus.addListener(CreateCompat::onCommonSetup);
        MinecraftForge.EVENT_BUS.addListener(CreateScriptExecutorMappers::register);
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            DisplaySource.BY_BLOCK_ENTITY.add(ModBlockEntities.SERIAL_BUS.get(), SERIAL_BUS_SOURCE.get());
            DisplaySource.BY_BLOCK_ENTITY.add(ModBlockEntities.SERIAL_BUS.get(), SERIAL_BUS_MANUAL_SOURCE.get());
            DisplayTarget.BY_BLOCK_ENTITY.register(ModBlockEntities.DATA_LECTERN.get(), DATA_LECTERN_TARGET.get());
        });
    }

    public static void tickDisplayLinkSource(ServerLevel level, BlockPos pos) {
        level.getServer().tell(new TickTask(level.getServer().getTickCount() + 1, () -> {
            if (level.getBlockEntity(pos) instanceof DisplayLinkBlockEntity displayLink && !displayLink.isRemoved()) {
                displayLink.updateGatheredData();
            }
        }));
    }

    public static boolean isActiveSerialBusDisplayLinkSource(Level level, BlockPos displayLinkPos, BlockPos serialBusPos) {
        BlockEntity blockEntity = level.getBlockEntity(displayLinkPos);
        if (!(blockEntity instanceof DisplayLinkBlockEntity displayLink) || displayLink.isRemoved()) {
            return false;
        }

        return displayLink.getSourcePosition().equals(serialBusPos)
                && displayLink.activeSource == SERIAL_BUS_SOURCE.get();
    }

    public static void registerPonderTagEntries(@NotNull PonderTagRegistrationHelper<ResourceLocation> helper) {
        helper.addToTag(AllCreatePonderTags.DISPLAY_SOURCES).add(ZPSMod.resource("serial_bus"));
        helper.addToTag(AllCreatePonderTags.DISPLAY_SOURCES).add(ZPSMod.resource("serial_bus_manual"));
        helper.addToTag(AllCreatePonderTags.DISPLAY_TARGETS).add(ZPSMod.resource("data_lectern"));
    }


    public static void registerScriptCommands(RegisterScriptCommandsEvent event) {
        CreateScriptCommands.registerScriptCommands(event);
    }
}
