package g_mungus.zps.compat.create;

import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlockEntity;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.simibubi.create.infrastructure.ponder.AllCreatePonderTags;
import com.tterrag.registrate.util.entry.RegistryEntry;
import g_mungus.zps.ZPSMod;
import g_mungus.zps.blockentity.ModBlockEntities;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class CreateCompat {
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
}