package net.eclipce.transpondersnails.entity.client;

import net.eclipce.transpondersnails.item.PortableBlackTransponderSnailItem;
import net.eclipce.transpondersnails.voice.client.PortableBlackSnailCallStateManager;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * Item properties for Portable Black Transponder Snail call states
 * Uses CLIENT-SIDE state manager synced from server
 */
public class PortableBlackSnailItemProperties {

    /**
     * Calculate call state predicate value
     * Returns: 0.0 (IDLE), 0.25 (SOUND), 0.5 (CALL), 0.75 (ACTIVE)
     */
    public static float calculateCallState(ItemStack stack, @Nullable ClientLevel world,
                                           @Nullable LivingEntity entity, int seed) {

        boolean isOpenValue = PortableBlackTransponderSnailItem.isOpen(stack);

        if (!isOpenValue) {
            return 0.0f;
        }


        if (entity == null) {
            System.err.println("ERROR: Entity is null!");
            return 0.0f;
        }

        PortableBlackSnailCallStateManager stateManager = PortableBlackSnailCallStateManager.getInstance();
        float predicateValue = stateManager.getPredicateValue(entity.getUUID());

        return predicateValue;
    }
}