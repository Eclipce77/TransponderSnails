package net.eclipce.transpondersnails.event;

import net.eclipce.transpondersnails.TransponderSnails;
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
            System.out.println("PortableSnailForceCloseHandler: World unloading, closing all portable snails");
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
            System.out.println("PortableSnailForceCloseHandler: Player " + serverPlayer.getName().getString() + " logged in, checking for open snails");
            closeAllPortableSnailsForPlayer(serverPlayer);
        }
    }

    /**
     * Called when a player dies
     */
    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            System.out.println("PortableSnailForceCloseHandler: Player " + serverPlayer.getName().getString() + " died, closing all portable snails");
            closeAllPortableSnailsForPlayer(serverPlayer);
        }
    }

    /**
     * Called when a player respawns
     */
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            System.out.println("PortableSnailForceCloseHandler: Player " + serverPlayer.getName().getString() + " respawned, checking for open snails");
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

        if (closedCount > 0) {
            System.out.println("PortableSnailForceCloseHandler: Closed " + closedCount + " portable snails for player " + player.getName().getString());
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
            System.out.println("PortableSnailForceCloseHandler: Closed " + closedCount.get() + " portable snails in world");
        }
    }
}