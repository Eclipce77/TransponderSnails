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

        // Register shell_color property for adult Den Den Mushi
        // FIXED: Use 0.1 increments instead of 0.01 to avoid floating-point precision issues
        ItemProperties.register(
                ModItems.DEN_DEN_MUSHI.get(),
                new ResourceLocation(TransponderSnails.MOD_ID, "shell_color"),
                (stack, level, entity, seed) -> {
                    int shellColor = DenDenMushiItem.getShellColor(stack);
                    // Use 0.1 increments: 0→0.0, 1→0.1, 2→0.2, ..., 15→1.5
                    return shellColor * 0.1f;
                }
        );

        // Register shell_color property for baby Den Den Mushi
        // FIXED: Use 0.1 increments instead of 0.01 to avoid floating-point precision issues
        ItemProperties.register(
                ModItems.BABY_DEN_DEN_MUSHI.get(),
                new ResourceLocation(TransponderSnails.MOD_ID, "shell_color"),
                (stack, level, entity, seed) -> {
                    int shellColor = BabyDenDenMushiItem.getShellColor(stack);
                    // Use 0.1 increments: 0→0.0, 1→0.1, 2→0.2, ..., 15→1.5
                    return shellColor * 0.1f;
                }
        );

        System.out.println("Registered Den Den Mushi item properties with fixed predicates");

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