package net.eclipce.transpondersnails.event;

import net.eclipce.transpondersnails.TransponderSnails;
import net.eclipce.transpondersnails.block.ModBlocks;
import net.eclipce.transpondersnails.block.entity.TransponderSnailBlockEntity;

import net.eclipce.transpondersnails.entity.ModEntities;
import net.eclipce.transpondersnails.entity.client.*;
import net.eclipce.transpondersnails.item.*;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = TransponderSnails.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ModEventBusClientEvents {

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

            // =================== BABY BLACK TRANSPONDER SNAIL PROPERTIES ===================

            // 1. Open/Close state (0.0 = closed, 1.0 = open)
            ItemProperties.register(ModItems.BABY_BLACK_TRANSPONDER_SNAIL.get(),
                    new ResourceLocation(TransponderSnails.MOD_ID, "open"),
                    (stack, world, entity, seed) -> {
                        return BabyBlackTransponderSnailItem.isOpen(stack) ? 1.0f : 0.0f;
                    });

            // 2. Shell color (0.00-0.15 in 0.01 increments for 16 colors)
            ItemProperties.register(ModItems.BABY_BLACK_TRANSPONDER_SNAIL.get(),
                    new ResourceLocation(TransponderSnails.MOD_ID, "shell_color"),
                    (stack, world, entity, seed) -> {
                        int colorId = BabyBlackTransponderSnailItem.getShellColorId(stack);
                        return colorId * 0.01f; // 0.00, 0.01, 0.02, ... 0.15
                    });

            // 3. Call state (0.0 idle, 0.1 sound, 0.2 call, 0.3 active)
            ItemProperties.register(ModItems.BABY_BLACK_TRANSPONDER_SNAIL.get(),
                    new ResourceLocation(TransponderSnails.MOD_ID, "call_state"),
                    (stack, world, entity, seed) -> {
                        if (entity instanceof Player player) {
                            return net.eclipce.transpondersnails.voice.client.BabyBlackSnailCallStateManager.getInstance()
                                    .getPredicateValue(player.getUUID());
                        }
                        return 0.0f; // Default to idle
                    });

            System.out.println("TransponderSnails: Registered 3 Baby Black Transponder Snail predicates (open + shell_color + call_state)");

            // ===================== BLACK TRANSPONDER SNAIL PROPERTIES =====================

            // 1. Open/Close state (0.0 = closed, 1.0 = open)
            ItemProperties.register(ModItems.BLACK_TRANSPONDER_SNAIL.get(),
                    new ResourceLocation(TransponderSnails.MOD_ID, "open"),
                    (stack, world, entity, seed) -> {
                        return BlackTransponderSnailItem.isOpen(stack) ? 1.0f : 0.0f;
                    });

            // 2. Shell color (0.00-0.15 in 0.01 increments for 16 colors)
            ItemProperties.register(ModItems.BLACK_TRANSPONDER_SNAIL.get(),
                    new ResourceLocation(TransponderSnails.MOD_ID, "shell_color"),
                    (stack, world, entity, seed) -> {
                        int colorId = BlackTransponderSnailItem.getShellColorId(stack);
                        return colorId * 0.01f; // 0.00, 0.01, 0.02, ... 0.15
                    });

            // 3. Call state (0.0 idle, 0.1 sound, 0.2 call, 0.3 active)
            ItemProperties.register(ModItems.BLACK_TRANSPONDER_SNAIL.get(),
                    new ResourceLocation(TransponderSnails.MOD_ID, "call_state"),
                    (stack, world, entity, seed) -> {
                        if (entity instanceof Player player) {
                            return net.eclipce.transpondersnails.voice.client.BlackSnailCallStateManager.getInstance()
                                    .getPredicateValue(player.getUUID());
                        }
                        return 0.0f; // Default to idle
                    });

            System.out.println("TransponderSnails: Registered 3 Black Transponder Snail predicates (open + shell_color + call_state)");

            // ===================== PORTABLE BLACK TRANSPONDER SNAIL PROPERTIES =====================

            // 1. Open/Close state (0.0 = closed, 1.0 = open)
            ItemProperties.register(ModItems.PORTABLE_BLACK_TRANSPONDER_SNAIL.get(),
                    new ResourceLocation(TransponderSnails.MOD_ID, "open"),
                    (stack, world, entity, seed) -> {
                        return PortableBlackTransponderSnailItem.isOpen(stack) ? 1.0f : 0.0f;
                    });

            // 2. Shell color (0.0 to 15.0)
            ItemProperties.register(ModItems.PORTABLE_BLACK_TRANSPONDER_SNAIL.get(),
                    new ResourceLocation(TransponderSnails.MOD_ID, "shell_color"),
                    (stack, world, entity, seed) -> {
                        return (float) PortableBlackTransponderSnailItem.getShellColorId(stack);
                    });

            // 3. Band color (0.0 to 15.0)
            ItemProperties.register(ModItems.PORTABLE_BLACK_TRANSPONDER_SNAIL.get(),
                    new ResourceLocation(TransponderSnails.MOD_ID, "band_color"),
                    (stack, world, entity, seed) -> {
                        return (float) PortableBlackTransponderSnailItem.getBandColorId(stack);
                    });

            // 4. Call state (0.0 = idle, 0.25 = sound, 0.5 = call, 0.75 = active)
            ItemProperties.register(ModItems.PORTABLE_BLACK_TRANSPONDER_SNAIL.get(),
                    new ResourceLocation(TransponderSnails.MOD_ID, "call_state"),
                    (stack, world, entity, seed) -> {
                        float value = PortableBlackSnailItemProperties.calculateCallState(stack, world, entity, seed);

                        // Debug logging to verify predicate is being called
                        if (value > 0.0f) {
                            System.out.println("[CALL-STATE-PREDICATE] Minecraft called predicate, returning: " + value);
                        }

                        return value;
                    });

            System.out.println("âœ… Registered 4 predicates for Portable Black Transponder Snail:");
            System.out.println("   - open (0.0 or 1.0)");
            System.out.println("   - shell_color (0.0 to 15.0)");
            System.out.println("   - band_color (0.0 to 15.0)");
            System.out.println("   - call_state (0.0, 0.25, 0.5, 0.75)");
            System.out.println("=============================================================");

            // âœ… CRITICAL FIX: Closing brace moved HERE - ALL registrations inside enqueueWork!
        });
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

        // Register Horned Den Den Mushi item coloring (reuses DenDenMushiItem color accessors)
        event.register((stack, tintIndex) -> {
            if (!(stack.getItem() instanceof HornedDenDenMushiItem)) {
                return -1;
            }

            if (tintIndex == 0 && DenDenMushiItem.isCaptured(stack)) {
                return DenDenMushiItem.getBodyColor(stack);
            }

            return -1;
        }, ModItems.HORNED_DEN_DEN_MUSHI.get());

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