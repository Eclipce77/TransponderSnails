package net.eclipce.transpondersnails.entity.custom;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

/**
 * The feedback of picking a Den Den Mushi up with an empty hand, written once so that every creature gives the same: the standard Den Den Mushi's own
 * sound (the item pickup pop, volume 0.2, with the same randomly varied pitch around 2.0), played where the creature stood, and then the creature
 * vanishes while the player's arm swings (the caller returns {@code InteractionResult.sidedSuccess}, as the standard one does).
 */
public final class DenDenMushiPickup {

    private DenDenMushiPickup() {}

    /** The pitch the standard Den Den Mushi uses: ((a - b) * 0.7 + 1) * 2 with a and b random in [0, 1), so 0.6 to 3.4, centred on 2.0. */
    public static float pitch(float a, float b) {
        return ((a - b) * 0.7F + 1.0F) * 2.0F;
    }

    /** Plays the pickup sound at the creature (server side). */
    public static void playSound(Entity creature) {
        creature.level().playSound(null, creature.getX(), creature.getY(), creature.getZ(),
                SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.2F,
                pitch(creature.level().random.nextFloat(), creature.level().random.nextFloat()));
    }
}
