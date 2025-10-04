package net.eclipce.transpondersnails.screen;

import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, "transpondersnails"); // Use your mod ID

    public static final RegistryObject<MenuType<DialingMenu>> DIALING_MENU =
            MENU_TYPES.register("dialing_menu", () -> IForgeMenuType.create(DialingMenu::createFromNetwork));

    // Add more menu types as needed
    //public static final RegistryObject<MenuType<IncomingCallMenu>> INCOMING_CALL_MENU =
            //MENU_TYPES.register("incoming_call_menu",
                    //() -> IForgeMenuType.create((windowId, inv, data) -> new IncomingCallMenu(windowId, inv)));

    //public static final RegistryObject<MenuType<ActiveCallMenu>> ACTIVE_CALL_MENU =
            //MENU_TYPES.register("active_call_menu",
                    //() -> IForgeMenuType.create((windowId, inv, data) -> new ActiveCallMenu(windowId, inv)));
}