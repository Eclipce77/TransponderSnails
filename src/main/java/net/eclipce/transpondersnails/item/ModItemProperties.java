package net.eclipce.transpondersnails.item;

import net.eclipce.transpondersnails.TransponderSnails;
import net.eclipce.transpondersnails.entity.client.BlackSnailItemProperties;
import net.eclipce.transpondersnails.entity.client.BabyBlackSnailItemProperties;
import net.eclipce.transpondersnails.entity.client.PortableBlackSnailItemProperties;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;

/**
 * Centralized registration of all item model predicates
 * Called from TransponderSnails.java during client setup
 */
public class ModItemProperties {

    public static void registerItemProperties() {

        registerDenDenMushiProperties();
        registerPortableBlackProperties();
        registerBlackTransponderSnailProperties();
        registerBabyBlackTransponderSnailProperties();

    }

    /**
     * Register predicates for Den Den Mushi items (adult, baby, white)
     */
    private static void registerDenDenMushiProperties() {
        // Adult Den Den Mushi - shell_color
        ItemProperties.register(
                ModItems.DEN_DEN_MUSHI.get(),
                new ResourceLocation(TransponderSnails.MOD_ID, "shell_color"),
                (stack, level, entity, seed) -> {
                    int shellColor = DenDenMushiItem.getShellColor(stack);
                    return shellColor * 0.1f;
                }
        );

        // Baby Den Den Mushi - shell_color
        ItemProperties.register(
                ModItems.BABY_DEN_DEN_MUSHI.get(),
                new ResourceLocation(TransponderSnails.MOD_ID, "shell_color"),
                (stack, level, entity, seed) -> {
                    int shellColor = BabyDenDenMushiItem.getShellColor(stack);
                    return shellColor * 0.1f;
                }
        );

        // White Den Den Mushi - shell_color
        ItemProperties.register(
                ModItems.WHITE_DEN_DEN_MUSHI.get(),
                new ResourceLocation(TransponderSnails.MOD_ID, "shell_color"),
                (stack, level, entity, seed) -> {
                    int shellColor = WhiteDenDenMushiItem.getShellColor(stack);
                    return shellColor * 0.1f;
                }
        );

        // Horned Den Den Mushi - shell_color (same scale as adult DDM: id * 0.1)
        ItemProperties.register(
                ModItems.HORNED_DEN_DEN_MUSHI.get(),
                new ResourceLocation(TransponderSnails.MOD_ID, "shell_color"),
                (stack, level, entity, seed) -> {
                    int shellColor = DenDenMushiItem.getShellColor(stack);
                    return shellColor * 0.1f;
                }
        );

    }

    /**
     * Register predicates for Portable Black Transponder Snail
     * Uses whole numbers: 0.0, 1.0, 2.0, ... 15.0
     */
    private static void registerPortableBlackProperties() {
        // Open predicate (0.0 = closed, 1.0 = open)
        ItemProperties.register(
                ModItems.PORTABLE_BLACK_TRANSPONDER_SNAIL.get(),
                new ResourceLocation(TransponderSnails.MOD_ID, "open"),
                (stack, level, entity, seed) -> {
                    return PortableBlackTransponderSnailItem.isOpen(stack) ? 1.0f : 0.0f;
                }
        );

        // Shell color predicate (0.0-15.0 for dye colors)
        ItemProperties.register(
                ModItems.PORTABLE_BLACK_TRANSPONDER_SNAIL.get(),
                new ResourceLocation(TransponderSnails.MOD_ID, "shell_color"),
                (stack, level, entity, seed) -> {
                    return (float) PortableBlackTransponderSnailItem.getShellColorId(stack);
                }
        );

        // Band color predicate (0.0-15.0 for dye colors)
        ItemProperties.register(
                ModItems.PORTABLE_BLACK_TRANSPONDER_SNAIL.get(),
                new ResourceLocation(TransponderSnails.MOD_ID, "band_color"),
                (stack, level, entity, seed) -> {
                    return (float) PortableBlackTransponderSnailItem.getBandColorId(stack);
                }
        );

        // Call state predicate (0.0, 0.25, 0.5, 0.75 for different call states)
        ItemProperties.register(
                ModItems.PORTABLE_BLACK_TRANSPONDER_SNAIL.get(),
                new ResourceLocation(TransponderSnails.MOD_ID, "call_state"),
                PortableBlackSnailItemProperties::calculateCallState
        );

    }

    /**
     * Register predicates for Black Transponder Snail (adult)
     * Uses 0.01 increments for shell_color: 0.00, 0.01, 0.02, ... 0.15
     */
    private static void registerBlackTransponderSnailProperties() {
        // Open predicate (0.0 = closed, 1.0 = open)
        ItemProperties.register(
                ModItems.BLACK_TRANSPONDER_SNAIL.get(),
                new ResourceLocation(TransponderSnails.MOD_ID, "open"),
                (stack, level, entity, seed) -> {
                    return BlackTransponderSnailItem.isOpen(stack) ? 1.0f : 0.0f;
                }
        );

        // Shell color predicate (0.00-0.15 in 0.01 increments)
        // NOTE: must use / 100.0f, not * 0.01f — multiplication has accumulated float
        // error that pushes ids 5/10/15 just below their JSON thresholds (Lime→Yellow,
        // Purple→Cyan, Black→Red). Division rounds to the exact threshold value.
        ItemProperties.register(
                ModItems.BLACK_TRANSPONDER_SNAIL.get(),
                new ResourceLocation(TransponderSnails.MOD_ID, "shell_color"),
                (stack, level, entity, seed) -> {
                    int colorId = BlackTransponderSnailItem.getShellColorId(stack);
                    return colorId / 100.0f;
                }
        );

        // Call state predicate (0.0, 0.1, 0.2, 0.3 for different call states)
        ItemProperties.register(
                ModItems.BLACK_TRANSPONDER_SNAIL.get(),
                new ResourceLocation(TransponderSnails.MOD_ID, "call_state"),
                BlackSnailItemProperties::calculateCallState
        );

    }

    /**
     * Register predicates for Baby Black Transponder Snail
     * Uses 0.01 increments for shell_color: 0.00, 0.01, 0.02, ... 0.15
     */
    private static void registerBabyBlackTransponderSnailProperties() {
        // Open predicate (0.0 = closed, 1.0 = open)
        ItemProperties.register(
                ModItems.BABY_BLACK_TRANSPONDER_SNAIL.get(),
                new ResourceLocation(TransponderSnails.MOD_ID, "open"),
                (stack, level, entity, seed) -> {
                    return BabyBlackTransponderSnailItem.isOpen(stack) ? 1.0f : 0.0f;
                }
        );

        // Shell color predicate (0.00-0.15 in 0.01 increments)
        // Same fix as adult: use / 100.0f to avoid float precision error at ids 5/10/15
        ItemProperties.register(
                ModItems.BABY_BLACK_TRANSPONDER_SNAIL.get(),
                new ResourceLocation(TransponderSnails.MOD_ID, "shell_color"),
                (stack, level, entity, seed) -> {
                    int colorId = BabyBlackTransponderSnailItem.getShellColorId(stack);
                    return colorId / 100.0f;
                }
        );

        // Call state predicate (0.0, 0.1, 0.2, 0.3 for different call states)
        ItemProperties.register(
                ModItems.BABY_BLACK_TRANSPONDER_SNAIL.get(),
                new ResourceLocation(TransponderSnails.MOD_ID, "call_state"),
                BabyBlackSnailItemProperties::calculateCallState
        );

    }
}