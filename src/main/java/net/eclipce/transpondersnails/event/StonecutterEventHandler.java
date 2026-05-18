package net.eclipce.transpondersnails.event;

import net.eclipce.transpondersnails.item.DenDenMushiItem;
import net.eclipce.transpondersnails.item.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = "transpondersnails")
public class StonecutterEventHandler {

    private static final Map<UUID, ItemStack> lastInputs = new HashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player.level().isClientSide) return;

        // Check if player has stonecutter open
        if (event.player.containerMenu instanceof StonecutterMenu menu) {
            UUID playerId = event.player.getUUID();

            // Get input slot (slot 0 in stonecutter)
            ItemStack input = menu.getSlot(0).getItem();

            // Store the current input
            if (!input.isEmpty() && input.getItem() == ModItems.TRANSPONDER_SNAIL.get()) {
                lastInputs.put(playerId, input.copy());
            }

            // Store the current input
            if (!input.isEmpty() && input.getItem() == ModItems.TRANSPONDER_SNAIL_TRANSMITTER.get()) {
                lastInputs.put(playerId, input.copy());
            }

            // Check result slot (slot 1 in stonecutter)
            ItemStack result = menu.getSlot(1).getItem();

            if (!result.isEmpty() && result.getItem() == ModItems.DEN_DEN_MUSHI.get()) {
                ItemStack storedInput = lastInputs.get(playerId);

                if (storedInput != null && !storedInput.isEmpty()) {
                    transferColors(storedInput, result);
                }
            }
        } else {
            // Player closed stonecutter, clear stored input
            lastInputs.remove(event.player.getUUID());
        }
    }

    private static void transferColors(ItemStack transponderSnail, ItemStack denDenMushi) {
        CompoundTag nbt = transponderSnail.getTag();
        if (nbt == null) return;

        int bodyColor = -1;
        int shellColor = -1;

        // Check top-level NBT
        if (nbt.contains("body_color")) {
            bodyColor = nbt.getInt("body_color");
        }
        if (nbt.contains("shell_color")) {
            shellColor = nbt.getInt("shell_color");
        }

        // Check BlockEntityTag
        if (nbt.contains("BlockEntityTag")) {
            CompoundTag blockEntityTag = nbt.getCompound("BlockEntityTag");
            if (bodyColor == -1 && blockEntityTag.contains("BodyColor")) {
                bodyColor = blockEntityTag.getInt("BodyColor");
            }
            if (shellColor == -1 && blockEntityTag.contains("ShellColor")) {
                shellColor = blockEntityTag.getInt("ShellColor");
            }
        }

        // Transfer colors
        if (bodyColor != -1 && shellColor != -1) {
            DenDenMushiItem.setColors(denDenMushi, bodyColor, shellColor);
            DenDenMushiItem.setCaptured(denDenMushi, true);

        }
    }
}