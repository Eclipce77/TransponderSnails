package net.eclipce.transpondersnails.entity.client;

import net.eclipce.transpondersnails.item.BlackTransponderSnailItem;
import net.eclipce.transpondersnails.voice.client.BlackSnailCallStateManager;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * Item properties for Black Transponder Snail call states
 * Uses CLIENT-SIDE state manager synced from server
 */
public class BlackSnailItemProperties {

    /**
     * Calculate call state predicate value
     * Returns: 0.0 (IDLE/CLOSED), 0.1 (SOUND), 0.2 (CALL), 0.3 (ACTIVE)
     */
    public static float calculateCallState(ItemStack stack, @Nullable ClientLevel world,
                                           @Nullable LivingEntity entity, int seed) {
        // Check if snail is open
        boolean isOpenValue = BlackTransponderSnailItem.isOpen(stack);

        if (!isOpenValue) {
            return 0.0f; // Closed/Idle
        }

        if (entity == null) {
            return 0.0f;
        }

        // Get call state from client-side manager
        BlackSnailCallStateManager stateManager = BlackSnailCallStateManager.getInstance();
        return stateManager.getPredicateValue(entity.getUUID());
    }
}