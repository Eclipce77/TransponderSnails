package net.eclipce.transpondersnails.event;

import net.eclipce.transpondersnails.TransponderSnails;
import net.eclipce.transpondersnails.client.KeyBindings;
import net.eclipce.transpondersnails.compat.CuriosCompat;
import net.eclipce.transpondersnails.item.ModItems;
import net.eclipce.transpondersnails.item.PortableBlackTransponderSnailItem;
import net.eclipce.transpondersnails.network.ModPackets;
import net.eclipce.transpondersnails.network.packets.CuriosSnailActionPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * CLIENT-SIDE FORGE BUS handler for the Transponder Snail interact keybind.
 *
 * Each client tick, if the keybind was pressed, checks whether the player
 * has a Portable Black Transponder Snail in any valid slot, then sends
 * CuriosSnailActionPacket to the server to perform the actual toggle.
 *
 * Curios checks go through CuriosCompat, so this class is safe when
 * Curios is not installed.
 */
@Mod.EventBusSubscriber(
        modid = TransponderSnails.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT
)
public class CuriosKeybindHandler {

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        // Only act on END phase to avoid double-firing per tick
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();

        // Don't fire while a screen is open or the player hasn't loaded yet
        if (mc.player == null || mc.screen != null) return;

        // consumeClick() drains one press at a time, correctly handles held-key repeats
        while (KeyBindings.SNAIL_INTERACT.consumeClick()) {
            if (hasSnailEquipped(mc.player)) {
                ModPackets.sendToServer(new CuriosSnailActionPacket());
                System.out.println("[KEYBIND] Snail interact pressed → packet sent to server");
            }
            // If no snail is equipped anywhere, silently ignore the keypress
        }
    }

    /**
     * Returns true if the player has a Portable Black Transponder Snail in:
     *   - main hand
     *   - offhand
     *   - any Curios slot (if Curios is installed)
     */
    private static boolean hasSnailEquipped(Player player) {
        // Main hand
        if (player.getMainHandItem().getItem() instanceof PortableBlackTransponderSnailItem) {
            return true;
        }

        // Offhand
        if (player.getOffhandItem().getItem() instanceof PortableBlackTransponderSnailItem) {
            return true;
        }

        // Curios slot (no-op if Curios is not loaded)
        if (CuriosCompat.isCuriosLoaded()) {
            ItemStack curiosStack = CuriosCompat.getEquippedCuriosItem(
                    player,
                    ModItems.PORTABLE_BLACK_TRANSPONDER_SNAIL.get()
            );
            if (!curiosStack.isEmpty()) {
                return true;
            }
        }

        return false;
    }
}