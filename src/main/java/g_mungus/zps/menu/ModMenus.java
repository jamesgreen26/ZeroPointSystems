package g_mungus.zps.menu;

import g_mungus.zps.ZPSMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, ZPSMod.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<CoalBurnerMenu>> COAL_BURNER =
            MENUS.register("coal_burner", () -> IMenuTypeExtension.create(CoalBurnerMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<PowerCellMenu>> POWER_CELL =
            MENUS.register("power_cell", () -> IMenuTypeExtension.create(PowerCellMenu::new));
}
