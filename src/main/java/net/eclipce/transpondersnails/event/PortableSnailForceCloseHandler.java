package net.eclipce.transpondersnails.event;

import net.eclipce.transpondersnails.TransponderSnails;
import net.eclipce.transpondersnails.compat.CuriosCompat;
import net.eclipce.transpondersnails.item.ModItems;
import net.eclipce.transpondersnails.item.PortableBlackTransponderSnailItem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Forces all Portable Black Transponder Snails to close on:
 * - World shutdown
 * - Player login (in case they were open when logging out)
 * - Player death/respawn
 */
@Mod.EventBusSubscriber(modid = TransponderSnails.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class PortableSnailForceCloseHandler {

    /**
     * Called when a world is unloaded (shutdown, dimension change, etc.)
     */
    @SubscribeEvent
    public static void onWorldUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            closeAllPortableSnailsInWorld(serverLevel);
        }
    }

    /**
     * Called when a player logs in
     * This is the second layer of protection - closes any snails that were somehow left open
     */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            closeAllPortableSnailsForPlayer(serverPlayer);
        }
    }

    /**
     * Called when a player dies
     */
    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            closeAllPortableSnailsForPlayer(serverPlayer);
        }
    }

    /**
     * Called when a player respawns
     */
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            closeAllPortableSnailsForPlayer(serverPlayer);
        }
    }

    /**
     * Closes all Portable Black Transponder Snails for a specific player
     */
    private static void closeAllPortableSnailsForPlayer(ServerPlayer player) {
        int closedCount = 0;

        // Close snails in player inventory
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() == ModItems.PORTABLE_BLACK_TRANSPONDER_SNAIL.get()) {
                if (PortableBlackTransponderSnailItem.isOpen(stack)) {
                    PortableBlackTransponderSnailItem.setOpen(stack, false);
                    PortableBlackTransponderSnailItem.markInHand(stack, false);
                    closedCount++;
                }
            }
        }

        // Close snails in Curios slots (safe — no-op if Curios is not installed)
        if (CuriosCompat.isCuriosLoaded()) {
            try {
                closedCount += CuriosCloseHelper.closeSnailsInCuriosSlots(player);
            } catch (Exception e) {
                System.err.println("PortableSnailForceCloseHandler: Curios close failed: " + e.getMessage());
            }
        }

        if (closedCount > 0) {
        }
    }

    /**
     * Closes all Portable Black Transponder Snails in the entire world
     * This includes:
     * - Player inventories
     * - Item entities (dropped items)
     * - Chests and other containers (via entity items when they're loaded)
     */
    private static void closeAllPortableSnailsInWorld(ServerLevel level) {
        // Use AtomicInteger to allow modification inside lambda
        AtomicInteger closedCount = new AtomicInteger(0);

        // Close snails in all player inventories
        for (ServerPlayer player : level.players()) {
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (stack.getItem() == ModItems.PORTABLE_BLACK_TRANSPONDER_SNAIL.get()) {
                    if (PortableBlackTransponderSnailItem.isOpen(stack)) {
                        PortableBlackTransponderSnailItem.setOpen(stack, false);
                        PortableBlackTransponderSnailItem.markInHand(stack, false);
                        closedCount.incrementAndGet();
                    }
                }
            }
        }

        // Close snails in item entities (dropped items)
        level.getAllEntities().forEach(entity -> {
            if (entity instanceof ItemEntity itemEntity) {
                ItemStack stack = itemEntity.getItem();
                if (stack.getItem() == ModItems.PORTABLE_BLACK_TRANSPONDER_SNAIL.get()) {
                    if (PortableBlackTransponderSnailItem.isOpen(stack)) {
                        PortableBlackTransponderSnailItem.setOpen(stack, false);
                        PortableBlackTransponderSnailItem.markInHand(stack, false);
                        itemEntity.setItem(stack); // Update the entity's item
                        closedCount.incrementAndGet();
                    }
                }
            }
        });

        if (closedCount.get() > 0) {
        }
    }

    /**
     * Inner class that references the Curios API directly.
     * Kept separate so the JVM does not load Curios classes
     * when Curios is not installed at runtime.
     */
    private static class CuriosCloseHelper {

        /**
         * Closes every open Portable Black Transponder Snail found in the
         * player's Curios inventory. Returns the number of snails closed.
         */
        static int closeSnailsInCuriosSlots(ServerPlayer player) {
            int count = 0;

            var lazyOpt = top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player);
            var opt = lazyOpt.resolve();
            if (opt.isEmpty()) return 0;

            var handler = opt.get();

            for (var entry : handler.getCurios().entrySet()) {
                var stacksHandler = entry.getValue();
                var itemHandler  = stacksHandler.getStacks();

                for (int i = 0; i < itemHandler.getSlots(); i++) {
                    ItemStack stack = itemHandler.getStackInSlot(i);

                    if (stack.isEmpty()
                            || stack.getItem() != net.eclipce.transpondersnails.item.ModItems
                            .PORTABLE_BLACK_TRANSPONDER_SNAIL.get()) {
                        continue;
                    }

                    if (PortableBlackTransponderSnailItem.isOpen(stack)) {
                        PortableBlackTransponderSnailItem.setOpen(stack, false);
                        PortableBlackTransponderSnailItem.markInHand(stack, false);

                        // Write back so Curios syncs the change to the client
                        // Cast is safe - Curios always returns an IItemHandlerModifiable
                        ((net.minecraftforge.items.IItemHandlerModifiable) itemHandler).setStackInSlot(i, stack);
                        count++;
                    }
                }
            }

            return count;
        }
    }
}