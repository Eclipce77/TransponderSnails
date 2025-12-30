package net.eclipce.transpondersnails.event;

import net.eclipce.transpondersnails.TransponderSnails;
import net.eclipce.transpondersnails.block.ModBlocks;
import net.eclipce.transpondersnails.block.entity.TransponderSnailBlockEntity;

import net.eclipce.transpondersnails.entity.ModEntities;
import net.eclipce.transpondersnails.entity.client.BabyBlackTransponderSnailRenderer;
import net.eclipce.transpondersnails.entity.client.BlackTransponderSnailRenderer;
import net.eclipce.transpondersnails.entity.client.TransponderSnailItemProperties;
import net.eclipce.transpondersnails.entity.client.DenDenMushiModel;
import net.eclipce.transpondersnails.entity.client.ModModelLayers;
import net.eclipce.transpondersnails.item.*;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = TransponderSnails.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ModEventBusClientEvents {

    @SubscribeEvent
    public static void registerLayer(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(ModModelLayers.DEN_DEN_MUSHI_LAYER, DenDenMushiModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                ModEntities.BABY_BLACK_TRANSPONDER_SNAIL.get(),
                BabyBlackTransponderSnailRenderer::new
        );

        event.registerEntityRenderer(ModEntities.BLACK_TRANSPONDER_SNAIL.get(),
                BlackTransponderSnailRenderer::new);

        System.out.println("Registered Baby Black Transponder Snail renderer!");
    }

    @SubscribeEvent
    public static void registerItemModelPredicates(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            // =================== DEN DEN MUSHI PROPERTIES ===================

            // Register shell color predicate
            ItemProperties.register(ModItems.DEN_DEN_MUSHI.get(),
                    new ResourceLocation(TransponderSnails.MOD_ID, "shell_color"),
                    (stack, world, entity, seed) -> {
                        int shellColor = DenDenMushiItem.getShellColor(stack);
                        float predicateValue = shellColor / 100.0f;
                        return predicateValue;
                    });

            // Optional: Add predicate to check if item is captured
            ItemProperties.register(ModItems.DEN_DEN_MUSHI.get(),
                    new ResourceLocation(TransponderSnails.MOD_ID, "captured"),
                    (stack, world, entity, seed) -> {
                        return DenDenMushiItem.isCaptured(stack) ? 1.0f : 0.0f;
                    });

            // =================== TRANSPONDER SNAIL BLOCK ITEM PROPERTIES ===================

            // Shell color for block item
            ItemProperties.register(ModBlocks.TRANSPONDER_SNAIL.get().asItem(),
                    new ResourceLocation(TransponderSnails.MOD_ID, "shell_color"),
                    (stack, world, entity, seed) -> {
                        CompoundTag nbt = stack.getTag();
                        if (nbt != null) {
                            if (nbt.contains("shell_color")) {
                                return nbt.getInt("shell_color") / 100.0f;
                            }
                            if (nbt.contains("BlockEntityTag")) {
                                CompoundTag beTag = nbt.getCompound("BlockEntityTag");
                                if (beTag.contains("ShellColor")) {
                                    return beTag.getInt("ShellColor") / 100.0f;
                                }
                            }
                        }
                        return 0.0f; // Default white
                    });

            // NEW: Call state for dynamic visuals
            ItemProperties.register(ModBlocks.TRANSPONDER_SNAIL.get().asItem(),
                    new ResourceLocation(TransponderSnails.MOD_ID, "call_state"),
                    TransponderSnailItemProperties::calculateCallState);

            System.out.println("TransponderSnails: Registered item properties for dynamic models");

            // Shell color for block item
            ItemProperties.register(ModBlocks.TRANSPONDER_SNAIL_TRANSMITTER.get().asItem(),
                    new ResourceLocation(TransponderSnails.MOD_ID, "shell_color"),
                    (stack, world, entity, seed) -> {
                        CompoundTag nbt = stack.getTag();
                        if (nbt != null) {
                            if (nbt.contains("shell_color")) {
                                return nbt.getInt("shell_color") / 100.0f;
                            }
                            if (nbt.contains("BlockEntityTag")) {
                                CompoundTag beTag = nbt.getCompound("BlockEntityTag");
                                if (beTag.contains("ShellColor")) {
                                    return beTag.getInt("ShellColor") / 100.0f;
                                }
                            }
                        }
                        return 0.0f; // Default white
                    });

            // NEW: Call state for dynamic visuals
            ItemProperties.register(ModBlocks.TRANSPONDER_SNAIL_TRANSMITTER.get().asItem(),
                    new ResourceLocation(TransponderSnails.MOD_ID, "call_state"),
                    TransponderSnailItemProperties::calculateCallState);

            System.out.println("TransponderSnails: Registered item properties for dynamic models");
        });

        // =================== BABY BLACK TRANSPONDER SNAIL PROPERTIES ===================

        // Register shell color predicate for Baby Black Transponder Snail
        // Uses same pattern as Den Den Mushi: color ID / 100.0f
        // Color 0 (white) = 0.00, Color 1 (orange) = 0.01, ... Color 15 (black) = 0.15
        ItemProperties.register(ModItems.BABY_BLACK_TRANSPONDER_SNAIL.get(),
                new ResourceLocation(TransponderSnails.MOD_ID, "shell_color"),
                (stack, world, entity, seed) -> {
                    int shellColor = BabyBlackTransponderSnailItem.getShellColor(stack);
                    float predicateValue = shellColor / 100.0f;
                    System.out.println("Baby Black Snail shell_color predicate: " + shellColor + " -> " + predicateValue);
                    return predicateValue;
                });

        System.out.println("TransponderSnails: Registered Baby Black Transponder Snail item properties");

        // ===================== BLACK TRANSPONDER SNAIL PROPERTIES =====================

        // Black Transponder Snail shell_color property
        ItemProperties.register(ModItems.BLACK_TRANSPONDER_SNAIL.get(),
                new ResourceLocation(TransponderSnails.MOD_ID, "shell_color"),
                (stack, world, entity, seed) -> {
                    int shellColor = BlackTransponderSnailItem.getShellColor(stack);
                    float predicateValue = shellColor / 100.0f;
                    // Debug output - remove once working
                    // System.out.println("Black Transponder Snail shell_color predicate: " + shellColor + " -> " + predicateValue);
                    return predicateValue;
                });

        System.out.println("TransponderSnails: Registered Black Transponder Snail item properties");

        // =================== WHITE DEN DEN MUSHI PROPERTIES ===================

        // Register shell color predicate for White Den Den Mushi
        // Uses same 0.1 increment pattern as regular Den Den Mushi
        ItemProperties.register(ModItems.WHITE_DEN_DEN_MUSHI.get(),
                new ResourceLocation(TransponderSnails.MOD_ID, "shell_color"),
                (stack, world, entity, seed) -> {
                    int shellColor = WhiteDenDenMushiItem.getShellColor(stack);
                    float predicateValue = shellColor * 0.1f;
                    return predicateValue;
                });

        System.out.println("TransponderSnails: Registered White Den Den Mushi item properties");

        // =================== WHITE TRANSPONDER SNAIL BLOCK ITEM PROPERTIES ===================

        // Register shell color predicate for White Transponder Snail block item
        ItemProperties.register(ModBlocks.WHITE_TRANSPONDER_SNAIL.get().asItem(),
                new ResourceLocation(TransponderSnails.MOD_ID, "shell_color"),
                (stack, world, entity, seed) -> {
                    CompoundTag nbt = stack.getTag();
                    if (nbt != null && nbt.contains("shell_color")) {
                        return nbt.getInt("shell_color") * 0.1f;
                    }
                    return 0.0f; // Default white
                });

        System.out.println("TransponderSnails: Registered White Transponder Snail block item properties");
    }

    @SubscribeEvent
    public static void onModelRegister(net.minecraftforge.client.event.ModelEvent.RegisterAdditional event) {
        String[] states = {"transponder_snail", "transponder_snail_sound", "transponder_snail_call", "transponder_snail_active"};
        String[] colors = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
                "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"};

        for (String state : states) {
            for (String color : colors) {
                event.register(new ResourceLocation(TransponderSnails.MOD_ID, "block/" + state + "_shell_" + color));
            }
        }

        System.out.println("Registered 64 transponder snail model variants for BER");
    }

    // Register item color provider in your client setup
    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        // Register existing Den Den Mushi item coloring
        event.register((stack, tintIndex) -> {
            if (!(stack.getItem() instanceof DenDenMushiItem)) {
                return -1;
            }

            if (tintIndex == 0 && DenDenMushiItem.isCaptured(stack)) {
                return DenDenMushiItem.getBodyColor(stack);
            }

            return -1;
        }, ModItems.DEN_DEN_MUSHI.get());

        // Register existing Baby Den Den Mushi item coloring
        event.register((stack, tintIndex) -> {
            if (!(stack.getItem() instanceof BabyDenDenMushiItem)) {
                return -1;
            }

            if (tintIndex == 0 && BabyDenDenMushiItem.isCaptured(stack)) {
                return BabyDenDenMushiItem.getBodyColor(stack);
            }

            return -1;
        }, ModItems.BABY_DEN_DEN_MUSHI.get());

        // Register Transponder Snail block item coloring
        event.register((stack, tintIndex) -> {
            if (tintIndex == 0) {
                CompoundTag nbt = stack.getTag();
                if (nbt != null) {
                    if (nbt.contains("body_color")) {
                        return nbt.getInt("body_color");
                    }
                    if (nbt.contains("BlockEntityTag")) {
                        CompoundTag beTag = nbt.getCompound("BlockEntityTag");
                        if (beTag.contains("BodyColor")) {
                            return beTag.getInt("BodyColor");
                        }
                    }
                }
            }
            return -1;
        }, ModBlocks.TRANSPONDER_SNAIL.get());

        event.register((stack, tintIndex) -> {
            if (tintIndex == 0) {
                CompoundTag nbt = stack.getTag();
                if (nbt != null) {
                    if (nbt.contains("body_color")) {
                        return nbt.getInt("body_color");
                    }
                    if (nbt.contains("BlockEntityTag")) {
                        CompoundTag beTag = nbt.getCompound("BlockEntityTag");
                        if (beTag.contains("BodyColor")) {
                            return beTag.getInt("BodyColor");
                        }
                    }
                }
            }
            return -1;
        }, ModBlocks.TRANSPONDER_SNAIL_TRANSMITTER.get());
    }

    @SubscribeEvent
    public static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tintIndex) -> {
            if (tintIndex == 0 && level != null && pos != null) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof TransponderSnailBlockEntity snailBE) {
                    int color = snailBE.getBodyColor();
                    System.out.println("BlockColors handler - Position: " + pos +
                            ", Body color: #" + Integer.toHexString(color) +
                            ", Initialized: " + snailBE.isColorsInitialized());
                    return color;
                } else {
                    System.out.println("BlockColors handler - Position: " + pos +
                            ", BE is null or wrong type");
                }
            }
            return -1;
        }, ModBlocks.TRANSPONDER_SNAIL.get());

        event.register((state, level, pos, tintIndex) -> {
            if (tintIndex == 0 && level != null && pos != null) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof TransponderSnailBlockEntity snailBE) {
                    int color = snailBE.getBodyColor();
                    System.out.println("BlockColors handler - Position: " + pos +
                            ", Body color: #" + Integer.toHexString(color) +
                            ", Initialized: " + snailBE.isColorsInitialized());
                    return color;
                } else {
                    System.out.println("BlockColors handler - Position: " + pos +
                            ", BE is null or wrong type");
                }
            }
            return -1;
        }, ModBlocks.TRANSPONDER_SNAIL_TRANSMITTER.get());
    }
}
