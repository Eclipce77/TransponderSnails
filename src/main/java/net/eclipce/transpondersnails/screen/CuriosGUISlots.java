package net.eclipce.transpondersnails.screen;

import net.eclipce.transpondersnails.TransponderSnails;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.InterModComms;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotTypeMessage;
import top.theillusivec4.curios.api.SlotTypePreset;

/**
 * Registers Curios slots for Transponder Snails
 */
@Mod.EventBusSubscriber(modid = TransponderSnails.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class CuriosGUISlots {

    /**
     * Register wrist slots during InterMod communication phase
     */
    @SubscribeEvent
    public static void onInterModEnqueue(InterModEnqueueEvent event) {
        // Register left wrist slot (standard Curios preset)
        InterModComms.sendTo(CuriosApi.MODID, SlotTypeMessage.REGISTER_TYPE,
                () -> SlotTypePreset.HANDS.getMessageBuilder().size(1).build()
        );

        System.out.println("TransponderSnails: Registered Curios wrist slots (hands preset)");
    }
}