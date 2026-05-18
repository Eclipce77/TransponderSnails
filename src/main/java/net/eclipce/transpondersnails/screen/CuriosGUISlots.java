package net.eclipce.transpondersnails.screen;

import net.eclipce.transpondersnails.TransponderSnails;
import net.eclipce.transpondersnails.compat.CuriosCompat;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.InterModComms;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent;

/**
 * Registers Curios slots for Transponder Snails (OPTIONAL)
 *
 * IMPORTANT: This class no longer imports Curios classes at the top level.
 * This prevents ClassNotFoundException when Curios is not installed.
 */
@Mod.EventBusSubscriber(modid = TransponderSnails.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class CuriosGUISlots {

    /**
     * Register wrist slots during InterMod communication phase (ONLY if Curios is loaded)
     */
    @SubscribeEvent
    public static void onInterModEnqueue(InterModEnqueueEvent event) {
        // Only register slots if Curios is actually loaded
        if (CuriosCompat.isCuriosLoaded()) {
            try {
                // Use helper class to avoid loading Curios classes when not present
                CuriosSlotHelper.registerSlots();
            } catch (Exception e) {
                System.err.println("TransponderSnails: Failed to register Curios slots: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
        }
    }

    /**
     * Helper class that accesses Curios API directly.
     * This is in a separate class to prevent class loading errors when Curios is absent.
     *
     * The Curios classes are referenced directly in the method body, which means
     * they're only loaded when registerSlots() is actually called.
     */
    private static class CuriosSlotHelper {

        public static void registerSlots() {
            // Access Curios API classes directly here (not imported at top)
            // This method is only called when Curios is confirmed to be loaded

            // Register left wrist slot (standard Curios preset)
            InterModComms.sendTo(
                    top.theillusivec4.curios.api.CuriosApi.MODID,
                    top.theillusivec4.curios.api.SlotTypeMessage.REGISTER_TYPE,
                    () -> top.theillusivec4.curios.api.SlotTypePreset.HANDS.getMessageBuilder().size(1).build()
            );
        }
    }
}