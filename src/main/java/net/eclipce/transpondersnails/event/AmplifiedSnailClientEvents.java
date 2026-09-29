package net.eclipce.transpondersnails.event;

import net.eclipce.transpondersnails.TransponderSnails;
import net.eclipce.transpondersnails.block.custom.AmplifiedTransponderSnailBlock;
import net.eclipce.transpondersnails.client.AmplifiedSnailClientExtensions;
import net.eclipce.transpondersnails.item.AmplifiedTransponderSnailItem;
import net.eclipce.transpondersnails.item.ModItems;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Registers the Amplified Transponder Snail's item model predicates.
 * Kept in its own class so ModEventBusClientEvents doesn't need to change.
 *
 * transpondersnails:amplifier_state -> 0.0 idle, 0.25 sound, 0.5 call, 0.75 active
 * transpondersnails:amplifier_using -> 1.0 while held right-click (selects the megaphone-pose models)
 */
@Mod.EventBusSubscriber(modid = TransponderSnails.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class AmplifiedSnailClientEvents {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemProperties.register(ModItems.AMPLIFIED_TRANSPONDER_SNAIL.get(),
                    new ResourceLocation(TransponderSnails.MOD_ID, "amplifier_state"),
                    (stack, level, entity, seed) -> {
                        int state = AmplifiedTransponderSnailItem.getState(stack);

                        // Instant "call" feedback on the client before the server's NBT update arrives
                        if (state == AmplifiedTransponderSnailBlock.STATE_IDLE
                                && AmplifiedSnailClientExtensions.isUsingStack(entity, stack)) {
                            state = AmplifiedTransponderSnailBlock.STATE_CALL;
                        }
                        return state * 0.25f;
                    });

            ItemProperties.register(ModItems.AMPLIFIED_TRANSPONDER_SNAIL.get(),
                    new ResourceLocation(TransponderSnails.MOD_ID, "amplifier_using"),
                    (stack, level, entity, seed) ->
                            AmplifiedSnailClientExtensions.isUsingStack(entity, stack) ? 1.0f : 0.0f);
        });
    }
}