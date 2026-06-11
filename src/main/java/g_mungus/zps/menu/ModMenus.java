package g_mungus.zps.menu;

import g_mungus.zps.ZPSMod;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, ZPSMod.MOD_ID);

    public static final RegistryObject<MenuType<CoalBurnerMenu>> COAL_BURNER =
            MENUS.register("coal_burner", () -> IForgeMenuType.create(CoalBurnerMenu::new));
}
