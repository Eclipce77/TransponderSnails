package net.eclipce.transpondersnails.network.packets;

import net.eclipce.transpondersnails.TransponderSnails;
import net.eclipce.transpondersnails.compat.CuriosCompat;
import net.eclipce.transpondersnails.item.ModItems;
import net.eclipce.transpondersnails.item.PortableBlackTransponderSnailItem;
import net.eclipce.transpondersnails.voice.server.InterceptionHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client → Server packet fired when the player presses the snail interact keybind.
 *
 * Priority order on the server:
 *   1. Curios slot  (if Curios is installed)
 *   2. Main hand
 *   3. Offhand
 *
 * The toggle logic mirrors PortableBlackTransponderSnailItem.use() exactly,
 * then calls InterceptionHelper so interception starts/stops as normal.
 *
 * Curios API calls live inside a private inner class (CuriosSlotHelper) that
 * is only class-loaded when Curios is confirmed present, preventing
 * NoClassDefFoundError when Curios is absent.
 */
public class CuriosSnailActionPacket {

    // No payload — the server identifies the player from the network context.

    public CuriosSnailActionPacket() {}

    public CuriosSnailActionPacket(FriendlyByteBuf buf) {
        // Nothing to read
    }

    public void encode(FriendlyByteBuf buf) {
        // Nothing to write
    }

    // =========================================================================
    // SERVER-SIDE HANDLER
    // =========================================================================

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;

            // 1. Try Curios slot first
            if (CuriosCompat.isCuriosLoaded()) {
                try {
                    if (CuriosSlotHelper.toggle(player)) return;
                } catch (Exception e) {
                    System.err.println("CuriosSnailActionPacket: Curios toggle failed: " + e.getMessage());
                }
            }

            // 2. Try main hand
            ItemStack mainHand = player.getMainHandItem();
            if (mainHand.getItem() instanceof PortableBlackTransponderSnailItem) {
                toggleHeld(player, mainHand, InteractionHand.MAIN_HAND);
                return;
            }

            // 3. Try offhand
            ItemStack offHand = player.getOffhandItem();
            if (offHand.getItem() instanceof PortableBlackTransponderSnailItem) {
                toggleHeld(player, offHand, InteractionHand.OFF_HAND);
            }
        });
        context.setPacketHandled(true);
    }

    // =========================================================================
    // HELD-IN-HAND TOGGLE
    // Mirrors PortableBlackTransponderSnailItem.use() exactly.
    // =========================================================================

    private static void toggleHeld(ServerPlayer player, ItemStack stack, InteractionHand hand) {
        boolean newState = !PortableBlackTransponderSnailItem.isOpen(stack);
        PortableBlackTransponderSnailItem.setOpen(stack, newState);
        PortableBlackTransponderSnailItem.markInHand(stack, true);

        // Force the client to receive the updated NBT (same fix as in use())
        player.inventoryMenu.broadcastChanges();
        int slot = (hand == InteractionHand.MAIN_HAND) ? player.getInventory().selected : 40;
        player.connection.send(new ClientboundContainerSetSlotPacket(-2, 0, slot, stack));

        System.out.println("[KEYBIND] Held snail toggled " + (newState ? "OPEN" : "CLOSED")
                + " (slot " + slot + ") for " + player.getName().getString());

        fireInterception(player, newState);
    }

    private static void fireInterception(ServerPlayer player, boolean isNowOpen) {
        var callManager = TransponderSnails.getCallManager();
        if (callManager == null) return;
        if (isNowOpen) {
            InterceptionHelper.onSnailOpened(player, callManager);
        } else {
            InterceptionHelper.onSnailClosed(player, callManager);
        }
    }

    // =========================================================================
    // CURIOS SLOT TOGGLE
    // Kept in a separate inner class so that Curios classes are only loaded
    // when Curios is actually present at runtime.
    // =========================================================================

    private static class CuriosSlotHelper {

        /**
         * Find the Portable Black Transponder Snail in the player's Curios inventory,
         * toggle its open state, write the change back so Curios syncs it to the
         * client, then fire interception logic.
         *
         * @return true if the snail was found and toggled; false if not in any Curios slot.
         */
        static boolean toggle(ServerPlayer player) {
            var lazyOpt = top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player);
            var opt = lazyOpt.resolve();
            if (opt.isEmpty()) return false;

            var handler = opt.get();

            for (var entry : handler.getCurios().entrySet()) {
                var stacksHandler = entry.getValue();
                var itemHandler = stacksHandler.getStacks();

                for (int i = 0; i < itemHandler.getSlots(); i++) {
                    ItemStack stack = itemHandler.getStackInSlot(i);

                    if (stack.isEmpty()
                            || !(stack.getItem() instanceof PortableBlackTransponderSnailItem)) {
                        continue;
                    }

                    // Toggle the open state
                    boolean newState = !PortableBlackTransponderSnailItem.isOpen(stack);
                    PortableBlackTransponderSnailItem.setOpen(stack, newState);

                    // Write back so Curios syncs the NBT change to the client
                    // Cast is safe - Curios always returns an IItemHandlerModifiable
                    ((net.minecraftforge.items.IItemHandlerModifiable) itemHandler).setStackInSlot(i, stack);

                    System.out.println("[KEYBIND] Curios snail toggled " + (newState ? "OPEN" : "CLOSED")
                            + " (slot: " + entry.getKey() + "[" + i + "])"
                            + " for " + player.getName().getString());

                    fireInterception(player, newState);
                    return true;
                }
            }

            return false;
        }
    }
}