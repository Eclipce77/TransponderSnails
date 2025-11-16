package net.eclipce.transpondersnails.item;

import net.eclipce.transpondersnails.TransponderSnails;
import net.eclipce.transpondersnails.item.ModItems;
import net.eclipce.transpondersnails.item.PortableBlackTransponderSnailItem;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = TransponderSnails.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ModItemProperties {

    public static void registerItemProperties() {
        // Register the "open" property (0.0 = closed, 1.0 = open)
        ItemProperties.register(
                ModItems.PORTABLE_BLACK_TRANSPONDER_SNAIL.get(),
                new ResourceLocation(TransponderSnails.MOD_ID, "open"),
                (stack, level, entity, seed) ->
                        PortableBlackTransponderSnailItem.isOpen(stack) ? 1.0F : 0.0F
        );

        // Register the "shell_color" property (0-15 for dye colors)
        ItemProperties.register(
                ModItems.PORTABLE_BLACK_TRANSPONDER_SNAIL.get(),
                new ResourceLocation(TransponderSnails.MOD_ID, "shell_color"),
                (stack, level, entity, seed) ->
                        (float) PortableBlackTransponderSnailItem.getShellColorId(stack)
        );

        // Register the "band_color" property (0-15 for dye colors)
        ItemProperties.register(
                ModItems.PORTABLE_BLACK_TRANSPONDER_SNAIL.get(),
                new ResourceLocation(TransponderSnails.MOD_ID, "band_color"),
                (stack, level, entity, seed) ->
                        (float) PortableBlackTransponderSnailItem.getBandColorId(stack)
        );

        System.out.println("PortableSnailItemProperties: Registered item properties for Portable Black Transponder Snail");
    }
}